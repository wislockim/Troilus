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



import java.util.Map;
import java.util.Map.Entry;

import net.oneandone.troilus.interceptor.QueryInterceptor;
import net.oneandone.troilus.java7.Dao;
import net.oneandone.troilus.java7.Deletion;
import net.oneandone.troilus.java7.Insertion;
import net.oneandone.troilus.java7.ListReadWithUnit;
import net.oneandone.troilus.java7.Record;
import net.oneandone.troilus.java7.ResultList;
import net.oneandone.troilus.java7.SingleReadWithUnit;
import net.oneandone.troilus.java7.UpdateWithUnitAndCounter;
import net.oneandone.troilus.java7.WriteWithCounter;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.core.querybuilder.Clause;
import com.datastax.driver.core.ConsistencyLevel;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

 
/**
 * Dao impl
 *
 */
public class Java7DaoImpl implements Dao {
    
    private final Context ctx;
    private final Tablename tablename;
    
    /**
     * @param session    the underlying session which has an assigned keyspace
     * @param tablename  the table name
     */
    public Java7DaoImpl(Session session, String tablename) {
        this(new Context(session), Tablename.newTablename(session, tablename));
    }

    /**
     * @param session      the underlying session
     * @param tablename    the table name
     * @param keyspacename the keyspacename
     */
    public Java7DaoImpl(Session session, String keyspacename, String tablename) {
        this(new Context(session), Tablename.newTablename(keyspacename, tablename));
    }

    
    private Java7DaoImpl(Context ctx, Tablename tablename) {
        this.ctx = ctx;
        this.tablename = tablename;
    }
    
    
    
    @Override
    public Dao withConsistency(ConsistencyLevel consistencyLevel) {
        return new Java7DaoImpl(ctx.withConsistency(consistencyLevel), this.tablename);
    }
    
    @Override
    public Dao withSerialConsistency(ConsistencyLevel consistencyLevel) {
        return new Java7DaoImpl(ctx.withSerialConsistency(consistencyLevel), this.tablename);
    }
 
    @Override
    public Dao withTracking() {
        return new Java7DaoImpl(ctx.withTracking(), this.tablename);
    }
    
    @Override
    public Dao withoutTracking() {
        return new Java7DaoImpl(ctx.withoutTracking(), this.tablename);
    }

    @Override
    public Dao withRetryPolicy(RetryPolicy policy) {
        return new Java7DaoImpl(ctx.withRetryPolicy(policy), this.tablename);
    }

    @Override
    public Dao withInterceptor(QueryInterceptor queryInterceptor) {
        return new Java7DaoImpl(ctx.withInterceptor(queryInterceptor), this.tablename);
    }
    
    @Override
    public Insertion writeEntity(Object entity) {
        ImmutableMap<String, Optional<Object>> values = ctx.getBeanMapper().toValues(entity, ctx.getCatalog().getColumnNames(tablename));
        return new InsertQuery(ctx, new WriteQueryDataImpl(tablename).valuesToMutate(values));
    }
    
    @Override
    public UpdateWithUnitAndCounter writeWhere(Clause... clauses) {
        return new UpdateQuery(ctx, new WriteQueryDataImpl(tablename).whereConditions((ImmutableList.copyOf(clauses))));
    }  
    
    @Override
    public WriteWithCounter writeWithKey(ImmutableMap<String, Object> composedKeyParts) {
        return new WriteWithCounterQuery(ctx, new WriteQueryDataImpl(tablename).keys(composedKeyParts));
    }
  
    
    @Override
    public WriteWithCounter writeWithKey(String keyName, Object keyValue) {
        return writeWithKey(ImmutableMap.of(keyName, keyValue));
    }
    
    @Override
    public WriteWithCounter writeWithKey(String keyName1, Object keyValue1, 
                                         String keyName2, Object keyValue2) {
        return writeWithKey(ImmutableMap.of(keyName1, keyValue1,
                                            keyName2, keyValue2));
        
    }
    
    @Override
    public WriteWithCounter writeWithKey(String keyName1, Object keyValue1, 
                                         String keyName2, Object keyValue2, 
                                         String keyName3, Object keyValue3) {
        return writeWithKey(ImmutableMap.of(keyName1, keyValue1, 
                                            keyName2, keyValue2, 
                                            keyName3, keyValue3));
        
    }
    
    @Override
    public <T> WriteWithCounter writeWithKey(ColumnName<T> keyName, T keyValue) {
        return writeWithKey(keyName.getName(), (Object) keyValue); 
    }
    
    @Override
    public <T, E> WriteWithCounter writeWithKey(ColumnName<T> keyName1, T keyValue1,
                                                ColumnName<E> keyName2, E keyValue2) {
        return writeWithKey(keyName1.getName(), (Object) keyValue1,
                            keyName2.getName(), (Object) keyValue2); 
    }
    
    @Override
    public <T, E, F> WriteWithCounter writeWithKey(ColumnName<T> keyName1, T keyValue1, 
                                                   ColumnName<E> keyName2, E keyValue2, 
                                                   ColumnName<F> keyName3, F keyValue3) {
        return writeWithKey(keyName1.getName(), (Object) keyValue1,
                            keyName2.getName(), (Object) keyValue2,
                            keyName3.getName(), (Object) keyValue3); 
    }    
    
    @Override
    public Deletion deleteWhere(Clause... whereConditions) {
        return new DeleteQuery(ctx, new DeleteQueryDataImpl(tablename).whereConditions(ImmutableList.copyOf(whereConditions)));
    };   
    
    @Override
    public Deletion deleteWithKey(String keyName, Object keyValue) {
        return deleteWithKey(ImmutableMap.of(keyName, keyValue));
    }

    @Override
    public Deletion deleteWithKey(String keyName1, Object keyValue1, 
                                  String keyName2, Object keyValue2) {
        return deleteWithKey(ImmutableMap.of(keyName1, keyValue1, 
                                             keyName2, keyValue2));
    }
    
    @Override
    public Deletion deleteWithKey(String keyName1, Object keyValue1, 
                                  String keyName2, Object keyValue2, 
                                  String keyName3, Object keyValue3) {
        return deleteWithKey(ImmutableMap.of(keyName1, keyValue1,
                                             keyName2, keyValue2, 
                                             keyName3, keyValue3));
    }
    
    @Override
    public <T> Deletion deleteWithKey(ColumnName<T> keyName, T keyValue) {
        return deleteWithKey(keyName.getName(), (Object) keyValue);
    }
    
    @Override
    public <T, E> Deletion deleteWithKey(ColumnName<T> keyName1, T keyValue1,
                                         ColumnName<E> keyName2, E keyValue2) {
        return deleteWithKey(keyName1.getName(), (Object) keyValue1,
                             keyName2.getName(), (Object) keyValue2);

    }
    
    @Override
    public <T, E, F> Deletion deleteWithKey(ColumnName<T> keyName1, T keyValue1,
                                            ColumnName<E> keyName2, E keyValue2, 
                                            ColumnName<F> keyName3, F keyValue3) {
        return deleteWithKey(keyName1.getName(), (Object) keyValue1,
                             keyName2.getName(), (Object) keyValue2,
                             keyName3.getName(), (Object) keyValue3);
    }
    
    public DeleteQuery deleteWithKey(ImmutableMap<String, Object> keyNameValuePairs) {
        return new DeleteQuery(ctx, new DeleteQueryDataImpl(tablename).key(keyNameValuePairs));
    }
    
    @Override
    public SingleReadWithUnit<Record, Record> readWithKey(ImmutableMap<String, Object> composedkey) {
        Map<String, ImmutableList<Object>> keys = Maps.newHashMap();
        for (Entry<String, Object> entry : composedkey.entrySet()) {
            keys.put(entry.getKey(), ImmutableList.of(entry.getValue()));
        }
        
        return new SingleReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.copyOf(keys)));
    }
    
    @Override
    public SingleReadWithUnit<Record, Record> readWithKey(String keyName, Object keyValue) {
        return readWithKey(ImmutableMap.of(keyName, keyValue));
    }
     
    @Override
    public SingleReadWithUnit<Record, Record> readWithKey(String keyName1, Object keyValue1, 
                                                            String keyName2, Object keyValue2) {
        return readWithKey(ImmutableMap.of(keyName1, keyValue1, 
                           keyName2, keyValue2));
    }
    
    @Override
    public SingleReadWithUnit<Record, Record> readWithKey(String keyName1, Object keyValue1, 
                                                            String keyName2, Object keyValue2,
                                                            String keyName3, Object keyValue3) {
        return readWithKey(ImmutableMap.of(keyName1, keyValue1, 
                                           keyName2, keyValue2, 
                                           keyName3, keyValue3));
    }
    
    @Override
    public <T> SingleReadWithUnit<Record, Record> readWithKey(ColumnName<T> keyName, T keyValue) {
        return readWithKey(keyName.getName(), (Object) keyValue);
    }
    
    @Override
    public <T, E> SingleReadWithUnit<Record, Record> readWithKey(ColumnName<T> keyName1, T keyValue1,
                                                                   ColumnName<E> keyName2, E keyValue2) {
        return readWithKey(keyName1.getName(), (Object) keyValue1,
                           keyName2.getName(), (Object) keyValue2);
    }
    
    @Override
    public <T, E, F> SingleReadWithUnit<Record, Record> readWithKey(ColumnName<T> keyName1, T keyValue1, 
                                                                      ColumnName<E> keyName2, E keyValue2,
                                                                      ColumnName<F> keyName3, F keyValue3) {
        return readWithKey(keyName1.getName(), (Object) keyValue1,
                           keyName2.getName(), (Object) keyValue2,                         
                           keyName3.getName(), (Object) keyValue3);
    }
    
    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKeys(String name, ImmutableList<Object> values) {
        return new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(name, values)));
    }
    
    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKeys(String composedKeyNamePart1, Object composedKeyValuePart1,
                                                     String composedKeyNamePart2, ImmutableList<Object> composedKeyValuesPart2) {
        return new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(composedKeyNamePart1, ImmutableList.of(composedKeyValuePart1),
                                                                                            composedKeyNamePart2, composedKeyValuesPart2)));        
    }
    
    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKeys(String composedKeyNamePart1, Object composedKeyValuePart1,
                                                     String composedKeyNamePart2, Object composedKeyValuePart2,
                                                     String composedKeyNamePart3, ImmutableList<Object> composedKeyValuesPart3) {
        return new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(composedKeyNamePart1, ImmutableList.of(composedKeyValuePart1),
                                                                                            composedKeyNamePart2, ImmutableList.of(composedKeyValuePart2),
                                                                                            composedKeyNamePart3, composedKeyValuesPart3)));        
    }

    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKey(String composedKeyNamePart1, Object composedKeyValuePart1) {
        return new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(composedKeyNamePart1, ImmutableList.of(composedKeyValuePart1))));
    }
    
    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKey(String composedKeyNamePart1, Object composedKeyValuePart1,
                                                            String composedKeyNamePart2, Object composedKeyValuePart2) {
        return new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(composedKeyNamePart1, ImmutableList.of(composedKeyValuePart1),
                                                                                            composedKeyNamePart2, ImmutableList.of(composedKeyValuePart2))));        
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKeys(ColumnName<T> name, ImmutableList<T> values) {
        return readSequenceWithKeys(name.getName(), (ImmutableList<Object>) values);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, E> ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKeys(ColumnName<T> composedKeyNamePart1, T composedKeyValuePart1,
                                                            ColumnName<E> composedKeyNamePart2, ImmutableList<E> composedKeyValuesPart2) {
        return readSequenceWithKeys(composedKeyNamePart1.getName(), (Object) composedKeyValuePart1,
                            composedKeyNamePart2.getName(), (ImmutableList<Object>) composedKeyValuesPart2);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T, E, F> ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKeys(ColumnName<T> composedKeyNamePart1, T composedKeyValuePart1,
                                                               ColumnName<E> composedKeyNamePart2, E composedKeyValuePart2,
                                                               ColumnName<F> composedKeyNamePart3, ImmutableList<F> composedKeyValuesPart3) {
        return readSequenceWithKeys(composedKeyNamePart1.getName(), (Object) composedKeyValuePart1,
                            composedKeyNamePart2.getName(), (Object) composedKeyValuePart2,
                            composedKeyNamePart3.getName(), (ImmutableList<Object>) composedKeyValuesPart3);
        
    }
    
    @Override
    public <T> ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKey(ColumnName<T> name, T value) {
        return readSequenceWithKey(name.getName(), (Object) value);
    }

    @Override
    public <T, E> ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKey(ColumnName<T> composedKeyNamePart1, T composedKeyValuePart1,
                                                               ColumnName<E> composedKeyNamePart2, E composedKeyValuePart2) {
        return readSequenceWithKey(composedKeyNamePart1.getName(), (Object) composedKeyValuePart1,
                                   composedKeyNamePart2.getName(), (Object) composedKeyValuePart2);
    }    
    
    @Override
    public ListReadQuery readSequenceWhere(Clause... clauses) {
        return new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).whereConditions(ImmutableSet.copyOf(clauses)));
    }
     
    @Override
    public ListReadQuery readSequence() {
        return new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).columnsToFetch(ImmutableMap.<String, Boolean>of()));
    }
}