/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.troilus;



import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.datastax.driver.core.ColumnMetadata;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.TableMetadata;
import com.datastax.driver.core.UserType;
import com.datastax.driver.core.exceptions.DriverInternalError;
import com.datastax.driver.core.exceptions.InvalidQueryException;
import com.datastax.driver.core.querybuilder.BuiltStatement;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;




public class DBSession  {
    private static final Logger LOG = LoggerFactory.getLogger(DBSession.class);

    private final Session session;
    private final boolean isKeyspacenameAssigned;
    private final String keyspacename;
    private final UDTValueMapper udtValueMapper;
    private final TableMetadataCache tableMetadataCache;
    private final PreparedStatementCache preparedStatementCache;
    private final UserTypeCache userTypeCache;
    
    private final AtomicLong lastCacheCleanTime = new AtomicLong(0);
    

    

    DBSession(Session session, BeanMapper beanMapper, Executor executor) {
        this.session = session;
        
        this.keyspacename = session.getLoggedKeyspace();
        this.isKeyspacenameAssigned = (keyspacename != null);
        
        //this.udtValueMapper = new UDTValueMapper(session.getCluster().getConfiguration().getProtocolOptions().getProtocolVersion(), beanMapper);
        this.udtValueMapper = new UDTValueMapper(session.getCluster().getConfiguration().getProtocolOptions().getProtocolVersionEnum(), beanMapper);
        this.preparedStatementCache = new PreparedStatementCache(session);
        this.userTypeCache = new UserTypeCache(session);
        this.tableMetadataCache = new TableMetadataCache(session, executor);
    }


    public ListenableFuture<ResultSet> executeAsync(Statement statement) {
        try {
            return getSession().executeAsync(statement);
        } catch (InvalidQueryException | DriverInternalError e) {
            cleanUp();
            LOG.warn("could not execute statement", e);
            return Futures.immediateFailedFuture(e);
        }
    }

    
    boolean isKeyspaqcenameAssigned() {
        return isKeyspacenameAssigned; 
    }
    
    String getKeyspacename() {
        return keyspacename;
    }
    
    private Session getSession() {
        return session;
    }
    
    
    ProtocolVersion getProtocolVersion() {
        //return getSession().getCluster().getConfiguration().getProtocolOptions().getProtocolVersion();
        return getSession().getCluster().getConfiguration().getProtocolOptions().getProtocolVersionEnum();
    }
    
    UserTypeCache getUserTypeCache() {
        return userTypeCache; 
    }
            
    UDTValueMapper getUDTValueMapper() {
        return udtValueMapper;
    }
 
    ImmutableSet<String> getColumnNames(Tablename tablename) {
        return tableMetadataCache.getColumnNames(tablename);
    }
    
    ColumnMetadata getColumnMetadata(Tablename tablename, String columnName) {
        return tableMetadataCache.getColumnMetadata(tablename, columnName); 
    }
    
    ListenableFuture<PreparedStatement> prepareAsync(final BuiltStatement statement) {
        return preparedStatementCache.prepareAsync(statement);
    }
    
    
    public ListenableFuture<Statement> bindAsync(ListenableFuture<PreparedStatement> preparedStatementFuture, final Object[] values) {
        Function<PreparedStatement, Statement> bindStatementFunction = new Function<PreparedStatement, Statement>() {
            @Override
            public Statement apply(PreparedStatement preparedStatement) {
                return preparedStatement.bind(values);
            }
        };
        return Futures.transform(preparedStatementFuture, bindStatementFunction);
    }
    
    
    Object toStatementValue(Tablename tablename, String name, Object value) {
        if (isNullOrEmpty(value)) {
            return null;
        } 
        
        DataType dataType = getColumnMetadata(tablename, name).getType();
        
        // build in
        if (UDTValueMapper.isBuildInType(dataType)) {
            
            // enum
            if (DataTypes.isTextDataType(dataType) && Enum.class.isAssignableFrom(value.getClass())) {
                return value.toString();
            }
            
            // byte buffer (byte[])
            if (dataType.equals(DataType.blob()) && byte[].class.isAssignableFrom(value.getClass())) {
                return ByteBuffer.wrap((byte[]) value);
            }

            
            return value;
         
        // udt    
        } else {
            return getUDTValueMapper().toUdtValue(tablename, getUserTypeCache(), getColumnMetadata(tablename, name).getType(), value);
        }
    }
    
    
    ImmutableList<Object> toStatementValues(Tablename tablename, String name, ImmutableList<Object> values) {
        List<Object> result = Lists.newArrayList(); 

        for (Object value : values) {
            result.add(toStatementValue(tablename, name, value));
        }
        
        return ImmutableList.copyOf(result);
    }

 
    private boolean isNullOrEmpty(Object value) {
        return (value == null) || 
               (Collection.class.isAssignableFrom(value.getClass()) && ((Collection<?>) value).isEmpty()) || 
               (Map.class.isAssignableFrom(value.getClass()) && ((Map<?, ?>) value).isEmpty());
    }
    
    
    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("preparedStatementsCache", preparedStatementCache.toString())
                          .toString();
    }
  
    
    private void cleanUp() {

        // avoid bulk clean calls within the same time
        if (System.currentTimeMillis() > (lastCacheCleanTime.get() + 1600)) {  // not really thread safe. However this does not matter 
            lastCacheCleanTime.set(System.currentTimeMillis());

            preparedStatementCache.invalidateAll();
            userTypeCache.invalidateAll();
        }
    }
    
    
    
    static final class UserTypeCache {
        private final Session session;
        private final Cache<String, UserType> userTypeCache;
        
        public UserTypeCache(Session session) {
            this.session = session;
            this.userTypeCache = CacheBuilder.newBuilder().maximumSize(100).<String, UserType>build();
        }

        
        public UserType get(Tablename tablename, String usertypeName) {
            String key = tablename.getKeyspacename() + "." + usertypeName;
            
            UserType userType = userTypeCache.getIfPresent(key);
            if (userType == null) {
                userType = session.getCluster().getMetadata().getKeyspace(tablename.getKeyspacename()).getUserType(usertypeName);
                userTypeCache.put(key, userType);
            } 
            
            return userType;
        }
        

        public void invalidateAll() {
            userTypeCache.invalidateAll();
        }      
    }    
    
    
    
    private static final class PreparedStatementCache {
        private final Session session;
        private final Cache<String, PreparedStatement> preparedStatementCache;

        public PreparedStatementCache(Session session) {
            this.session = session;
            this.preparedStatementCache = CacheBuilder.newBuilder().maximumSize(150).<String, PreparedStatement>build();
        }
        
        
        ListenableFuture<PreparedStatement> prepareAsync(final BuiltStatement statement) {
            PreparedStatement preparedStatment = preparedStatementCache.getIfPresent(statement.getQueryString());
            if (preparedStatment == null) {
                ListenableFuture<PreparedStatement> future = session.prepareAsync(statement);
                
                Function<PreparedStatement, PreparedStatement> addToCacheFunction = new Function<PreparedStatement, PreparedStatement>() {
                    
                    public PreparedStatement apply(PreparedStatement preparedStatment) {
                        preparedStatementCache.put(statement.getQueryString(), preparedStatment);
                        return preparedStatment;
                    }
                };
                
                return Futures.transform(future, addToCacheFunction);
            } else {
                return Futures.immediateFuture(preparedStatment);
            }
        }
        
        
        public void invalidateAll() {
            preparedStatementCache.invalidateAll();
        }      
        
        
        @Override
        public String toString() {
            return Joiner.on(", ").withKeyValueSeparator("=").join(preparedStatementCache.asMap());
        }
    }
    
    
    
    
    private static final class TableMetadataCache {
        private final Session session;
        private final Cache<Tablename, Metadata> tableMetadataCache;
        
        private final Executor executor;
        private final AtomicBoolean isRefreshRunning = new AtomicBoolean(false);
        
        

        public TableMetadataCache(Session session, Executor executor) {
            this.session = session;
            this.executor = executor;
            this.tableMetadataCache = CacheBuilder.newBuilder().maximumSize(150).<Tablename, Metadata>build();
        }
        
        ImmutableSet<String> getColumnNames(Tablename tablename) {
            return getMetadata(tablename).getColumnNames();
        }
        
        ColumnMetadata getColumnMetadata(Tablename tablename, String columnName) {
            return getMetadata(tablename).getColumnMetadata(columnName);
        }
        
        private Metadata getMetadata(Tablename tablename) {
            Metadata metadata = tableMetadataCache.getIfPresent(tablename);
            if (metadata == null) {
                metadata = loadMetadata(tablename);
                tableMetadataCache.put(tablename, metadata);
                           
            } else if (metadata.isExpired()) {
                refreshCache();
            }
            
            
            return metadata;
        }

        
        private void refreshCache() {
            
            if (isRefreshRunning.getAndSet(true)) {
                
                Runnable refreshTask = new Runnable() {
                    
                    @Override
                    public void run() {
                        try {
                            for (Tablename tablename : Sets.newHashSet(tableMetadataCache.asMap().keySet())) {
                                Metadata metadata = loadMetadata(tablename);
                                tableMetadataCache.put(tablename, metadata);
                                
                            }
                        } finally {
                            isRefreshRunning.set(false);
                        }
                    }
                };
                
                executor.execute(refreshTask);
            }
        }
        
        
        private Metadata loadMetadata(Tablename tablename) {
            TableMetadata tableMetadata = loadTableMetadata(session, tablename);
            ImmutableSet<String> columnNames = loadColumnNames(tableMetadata);
            Metadata metadata = new Metadata(System.currentTimeMillis(), tablename, tableMetadata, columnNames);
            
            return metadata;
        }
        
        
        private static TableMetadata loadTableMetadata(Session session, Tablename tablename) {
            TableMetadata tableMetadata = session.getCluster().getMetadata().getKeyspace(tablename.getKeyspacename()).getTable(tablename.getTablename());
            if (tableMetadata == null) {
                throw new RuntimeException("table " + tablename + " is not defined");
            }

            return tableMetadata;
        }

        private static ImmutableSet<String> loadColumnNames(TableMetadata tableMetadata) {
            Set<String> columnNames = Sets.newHashSet();
            for (ColumnMetadata columnMetadata : tableMetadata.getColumns()) {
                columnNames.add(columnMetadata.getName());
            }
            
            return ImmutableSet.copyOf(columnNames);
        }
    }
    
    
    private static final class Metadata {
        private final long readtime;
        private final Tablename tablename;
        private final TableMetadata tableMetadata;
        private final ImmutableSet<String> columnNames;
        
        public Metadata(long readtime, Tablename tablename, TableMetadata tableMetadata, ImmutableSet<String> columnNames) {
            this.readtime = readtime;
            this.tablename = tablename;
            this.tableMetadata = tableMetadata;
            this.columnNames = columnNames;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > (readtime + (96 * 1000));
        }
        
        ImmutableSet<String> getColumnNames() {
            return columnNames;
        }
        
        ColumnMetadata getColumnMetadata(String columnName) {
            ColumnMetadata metadata = tableMetadata.getColumn(columnName);
            if (metadata == null) {
                throw new RuntimeException("table " + tablename + " does not support column '" + columnName + "'");
            }
            return metadata;
        }
    }
}