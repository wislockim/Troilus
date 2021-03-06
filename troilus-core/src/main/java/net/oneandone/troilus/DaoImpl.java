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


import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import net.oneandone.troilus.interceptor.CascadeOnDeleteInterceptor;
import net.oneandone.troilus.interceptor.CascadeOnWriteInterceptor;
import net.oneandone.troilus.interceptor.DeleteQueryData;
import net.oneandone.troilus.interceptor.DeleteQueryRequestInterceptor;
import net.oneandone.troilus.interceptor.QueryInterceptor;
import net.oneandone.troilus.interceptor.ReadQueryData;
import net.oneandone.troilus.interceptor.ReadQueryRequestInterceptor;
import net.oneandone.troilus.interceptor.ReadQueryResponseInterceptor;
import net.oneandone.troilus.interceptor.WriteQueryData;
import net.oneandone.troilus.interceptor.WriteQueryRequestInterceptor;
import net.oneandone.troilus.java7.Batchable;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.RetryPolicy;
import com.datastax.driver.core.querybuilder.Clause;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;

 

/**
 * DaoImpl
 */
public class DaoImpl implements Dao {
    
    private final Tablename tablename;
    private final Context ctx;
    
    /**
     * @param session     the underlying session which has an assigned keyspace
     * @param tablename   the table name
     */
    public DaoImpl(Session session, String tablename) {
        this(new Context(session), Tablename.newTablename(session, tablename));
    }

    
    /**
     * @param session      the underlying session
     * @param tablename    the table name
     * @param keyspacename the keyspacename
     */
    public DaoImpl(Session session, String keyspacename, String tablename) {
        this(new Context(session), Tablename.newTablename(keyspacename, tablename));
    }

 
    private DaoImpl(Context ctx, Tablename tablename) {
        this.ctx = ctx;
        this.tablename = tablename;
    }
    
    
    @Override
    public Dao withConsistency(ConsistencyLevel consistencyLevel) {
        return new DaoImpl(ctx.withConsistency(consistencyLevel), this.tablename);
    }
    
    @Override
    public Dao withSerialConsistency(ConsistencyLevel consistencyLevel) {
        return new DaoImpl(ctx.withSerialConsistency(consistencyLevel), this.tablename);
    }
 
    @Override
    public Dao withTracking() {
        return new DaoImpl(ctx.withTracking(), this.tablename);
    }
    
    @Override
    public Dao withoutTracking() {
        return new DaoImpl(ctx.withoutTracking(), this.tablename);
    }

    @Override
    public Dao withRetryPolicy(RetryPolicy policy) {
        return new DaoImpl(ctx.withRetryPolicy(policy), this.tablename);
    }

    
    @Override
    public Dao withInterceptor(QueryInterceptor queryInterceptor) {
        Context context = ctx.withInterceptor(queryInterceptor);
        
        if (ReadQueryRequestInterceptor.class.isAssignableFrom(queryInterceptor.getClass())) {
            context = context.withInterceptor(new ListReadQueryRequestInterceptorAdapter((ReadQueryRequestInterceptor) queryInterceptor));
        }

        if (ReadQueryResponseInterceptor.class.isAssignableFrom(queryInterceptor.getClass())) {
            context = context.withInterceptor(new ListReadQueryResponseInterceptorAdapter((ReadQueryResponseInterceptor) queryInterceptor));
        } 

        if (WriteQueryRequestInterceptor.class.isAssignableFrom(queryInterceptor.getClass())) {
            context = context.withInterceptor(new WriteQueryRequestInterceptorAdapter((WriteQueryRequestInterceptor) queryInterceptor));
        } 

        if (DeleteQueryRequestInterceptor.class.isAssignableFrom(queryInterceptor.getClass())) {
            context = context.withInterceptor(new DeleteQueryRequestInterceptorAdapter((DeleteQueryRequestInterceptor) queryInterceptor));
        } 

        if (CascadeOnWriteInterceptor.class.isAssignableFrom(queryInterceptor.getClass())) {
            context = context.withInterceptor(new CascadeOnWriteInterceptorAdapter((CascadeOnWriteInterceptor) queryInterceptor));
        }

        if (CascadeOnDeleteInterceptor.class.isAssignableFrom(queryInterceptor.getClass())) {
            context = context.withInterceptor(new CascadeOnDeleteInterceptorAdapter((CascadeOnDeleteInterceptor) queryInterceptor));
        }

        return new DaoImpl(context, this.tablename);
    }
    
    
    @Override
    public Insertion writeEntity(Object entity) {
        final ImmutableMap<String, com.google.common.base.Optional<Object>> values = ctx.getBeanMapper().toValues(entity, ctx.getCatalog().getColumnNames(tablename));
        return new InsertQueryAdapter(ctx, new InsertQuery(ctx, new WriteQueryDataImpl(tablename).valuesToMutate(values)));
    }
    
    @Override
    public UpdateWithUnitAndCounter writeWhere(Clause... clauses) {
        return new UpdateQueryAdapter(ctx, new UpdateQuery(ctx, new WriteQueryDataImpl(tablename).whereConditions((ImmutableList.copyOf(clauses)))));
    }
    
    @Override
    public WriteWithCounter writeWithKey(ImmutableMap<String, Object> composedKeyParts) {
        return new WriteWithCounterQueryAdapter(ctx, new WriteWithCounterQuery(ctx, new WriteQueryDataImpl(tablename).keys(composedKeyParts)));
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
        return new DeleteQueryAdapter(ctx, new DeleteQuery(ctx, new DeleteQueryDataImpl(tablename).whereConditions(ImmutableList.copyOf(whereConditions))));      
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
    
    public Deletion deleteWithKey(ImmutableMap<String, Object> keyNameValuePairs) {
        return new DeleteQueryAdapter(ctx, new DeleteQuery(ctx, new DeleteQueryDataImpl(tablename).key(keyNameValuePairs)));      
    }
    
    
    
    @Override
    public SingleReadWithUnit<Optional<Record>, Record> readWithKey(ImmutableMap<String, Object> composedkey) {
        final Map<String, ImmutableList<Object>> keys = Maps.newHashMap();
        for (Entry<String, Object> entry : composedkey.entrySet()) {
            keys.put(entry.getKey(), ImmutableList.of(entry.getValue()));
        }
        
        return new SingleReadQueryAdapter(ctx, new SingleReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.copyOf(keys))));
    }
    
    @Override
    public SingleReadWithUnit<Optional<Record>, Record> readWithKey(String keyName, Object keyValue) {
        return readWithKey(ImmutableMap.of(keyName, keyValue));
    }
     
    @Override
    public SingleReadWithUnit<Optional<Record>, Record> readWithKey(String keyName1, Object keyValue1, 
                                                                    String keyName2, Object keyValue2) {
        return readWithKey(ImmutableMap.of(keyName1, keyValue1, 
                           keyName2, keyValue2));
    }
    
    @Override
    public SingleReadWithUnit<Optional<Record>, Record> readWithKey(String keyName1, Object keyValue1, 
                                                                    String keyName2, Object keyValue2,
                                                                    String keyName3, Object keyValue3) {
        return readWithKey(ImmutableMap.of(keyName1, keyValue1, 
                                           keyName2, keyValue2, 
                                           keyName3, keyValue3));
    }
    
    @Override
    public <T> SingleReadWithUnit<Optional<Record>, Record> readWithKey(ColumnName<T> keyName, T keyValue) {
        return readWithKey(keyName.getName(), (Object) keyValue);
    }
    
    @Override
    public <T, E> SingleReadWithUnit<Optional<Record>, Record> readWithKey(ColumnName<T> keyName1, T keyValue1,
                                                                           ColumnName<E> keyName2, E keyValue2) {
        return readWithKey(keyName1.getName(), (Object) keyValue1,
                           keyName2.getName(), (Object) keyValue2);
    }
    
    @Override
    public <T, E, F> SingleReadWithUnit<Optional<Record>, Record> readWithKey(ColumnName<T> keyName1, T keyValue1, 
                                                                              ColumnName<E> keyName2, E keyValue2,
                                                                              ColumnName<F> keyName3, F keyValue3) {
        return readWithKey(keyName1.getName(), (Object) keyValue1,
                           keyName2.getName(), (Object) keyValue2,                         
                           keyName3.getName(), (Object) keyValue3);
    }
    
    
    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKeys(String name, ImmutableList<Object> values) {
        return new ListReadQueryAdapter(ctx, new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(name, values))));
    }
    
    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKeys(String composedKeyNamePart1, Object composedKeyValuePart1,
                                                                             String composedKeyNamePart2, ImmutableList<Object> composedKeyValuesPart2) {
        return new ListReadQueryAdapter(ctx, new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(composedKeyNamePart1, ImmutableList.of(composedKeyValuePart1),
                                                                                                                          composedKeyNamePart2, composedKeyValuesPart2))));
    }
    
    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKeys(String composedKeyNamePart1, Object composedKeyValuePart1,
                                                                             String composedKeyNamePart2, Object composedKeyValuePart2,
                                                                             String composedKeyNamePart3, ImmutableList<Object> composedKeyValuesPart3) {
        return new ListReadQueryAdapter(ctx, new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(composedKeyNamePart1, ImmutableList.of(composedKeyValuePart1),
                                                                                                                          composedKeyNamePart2, ImmutableList.of(composedKeyValuePart2),
                                                                                                                          composedKeyNamePart3, composedKeyValuesPart3))));        
    }

    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKey(String composedKeyNamePart1, Object composedKeyValuePart1) {
        return new ListReadQueryAdapter(ctx, new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(composedKeyNamePart1, ImmutableList.of(composedKeyValuePart1)))));
    }

    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWithKey(String composedKeyNamePart1, Object composedKeyValuePart1,
                                                                            String composedKeyNamePart2, Object composedKeyValuePart2) {
        return new ListReadQueryAdapter(ctx, new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).keys(ImmutableMap.of(composedKeyNamePart1, ImmutableList.of(composedKeyValuePart1),
                                                                                                                          composedKeyNamePart2, ImmutableList.of(composedKeyValuePart2)))));
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
    public ListReadWithUnit<ResultList<Record>, Record> readSequenceWhere(Clause... clauses) {
        return new ListReadQueryAdapter(ctx, new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).whereConditions(ImmutableSet.copyOf(clauses))));
    }
     
    @Override
    public ListReadWithUnit<ResultList<Record>, Record> readSequence() {
        return new ListReadQueryAdapter(ctx, new ListReadQuery(ctx, new ReadQueryDataImpl(tablename).columnsToFetch(ImmutableMap.of())));
    }

    
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("ctx", ctx)
                          .toString();
    }
    
    

    
    /**
     * Java8 adapter of a ListReadQueryData
     */
    static class ListReadQueryDataAdapter implements ReadQueryData {

        private final net.oneandone.troilus.java7.interceptor.ReadQueryData data;

        
        ListReadQueryDataAdapter(Tablename tablename) {
            this(new ReadQueryDataImpl(tablename));
        }

        private ListReadQueryDataAdapter(net.oneandone.troilus.java7.interceptor.ReadQueryData data) {
            this.data = data;
        }
        

        @Override
        public ListReadQueryDataAdapter keys(ImmutableMap<String, ImmutableList<Object>> keys) {
            return new ListReadQueryDataAdapter(data.keys(keys));  
        }
        
        @Override
        public ListReadQueryDataAdapter whereConditions(ImmutableSet<Clause> whereConditions) {
            return new ListReadQueryDataAdapter(data.whereConditions(whereConditions));  
        }

        @Override
        public ListReadQueryDataAdapter columnsToFetch(ImmutableMap<String, Boolean> columnsToFetch) {
            return new ListReadQueryDataAdapter(data.columnsToFetch(columnsToFetch));  
        }

        @Override
        public ListReadQueryDataAdapter limit(Optional<Integer> optionalLimit) {
            return new ListReadQueryDataAdapter(data.limit(optionalLimit.orElse(null)));  
        }

        @Override
        public ListReadQueryDataAdapter allowFiltering(Optional<Boolean> optionalAllowFiltering) {
            return new ListReadQueryDataAdapter(data.allowFiltering(optionalAllowFiltering.orElse(null)));  
        }

        @Override
        public ListReadQueryDataAdapter fetchSize(Optional<Integer> optionalFetchSize) {
            return new ListReadQueryDataAdapter(data.fetchSize(optionalFetchSize.orElse(null)));  
        }

        @Override
        public ListReadQueryDataAdapter distinct(Optional<Boolean> optionalDistinct) {
            return new ListReadQueryDataAdapter(data.distinct(optionalDistinct.orElse(null)));  
        }
        
        @Override
        public Tablename getTablename() {
            return data.getTablename();
        }
        
        @Override
        public ImmutableMap<String, ImmutableList<Object>> getKeys() {
            return data.getKeys();
        }
        
        @Override
        public ImmutableSet<Clause> getWhereConditions() {
            return data.getWhereConditions();
        }

        @Override
        public ImmutableMap<String, Boolean> getColumnsToFetch() {
            return data.getColumnsToFetch();
        }

        @Override
        public Optional<Integer> getLimit() {
            return Optional.ofNullable(data.getLimit());
        }

        @Override
        public Optional<Boolean> getAllowFiltering() {
            return Optional.ofNullable(data.getAllowFiltering());
        }

        @Override
        public Optional<Integer> getFetchSize() {
            return Optional.ofNullable(data.getFetchSize());
        }

        @Override
        public Optional<Boolean> getDistinct() {
            return Optional.ofNullable(data.getDistinct());
        }
        
        static net.oneandone.troilus.java7.interceptor.ReadQueryData convert(ReadQueryData data) {
            return new ReadQueryDataImpl(data.getTablename()).keys(data.getKeys())
                                                             .whereConditions(data.getWhereConditions())
                                                             .columnsToFetch(data.getColumnsToFetch())
                                                             .limit(data.getLimit().orElse(null))
                                                             .allowFiltering(data.getAllowFiltering().orElse(null))
                                                             .fetchSize(data.getFetchSize().orElse(null))
                                                             .distinct(data.getDistinct().orElse(null));
        }
    }
    


    /**
     * Java8 adapter of a RecordList
     */
    static class RecordListAdapter implements ResultList<Record> {
        private final net.oneandone.troilus.java7.ResultList<net.oneandone.troilus.java7.Record> recordList;
        
        private RecordListAdapter(net.oneandone.troilus.java7.ResultList<net.oneandone.troilus.java7.Record> recordList) {
            this.recordList = recordList;
        }
        
        static ResultList<Record> convertFromJava7(net.oneandone.troilus.java7.ResultList<net.oneandone.troilus.java7.Record> recordList) {
            return new RecordListAdapter(recordList);
        }
        
        @Override
        public ExecutionInfo getExecutionInfo() {
            return recordList.getExecutionInfo();
        }
        
        @Override
        public ImmutableList<ExecutionInfo> getAllExecutionInfo() {
            return recordList.getAllExecutionInfo();
        }

        @Override
        public boolean wasApplied() {
            return recordList.wasApplied();
        }
        
        
        @Override
        public FetchingIterator<Record> iterator() {
            
            return new FetchingIterator<Record>() {
                private final net.oneandone.troilus.java7.FetchingIterator<net.oneandone.troilus.java7.Record> iterator = recordList.iterator();

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }
                
                @Override
                public Record next() {
                    return RecordAdapter.convertFromJava7(iterator.next());
                }
                
                @Override
                public int getAvailableWithoutFetching() {
                    return iterator.getAvailableWithoutFetching();
                }
                
                @Override
                public CompletableFuture<ResultSet> fetchMoreResultsAsync() {
                    return CompletableFutures.toCompletableFuture(iterator.fetchMoreResultsAsync());
                }
                
                @Override
                public boolean isFullyFetched() {
                    return iterator.isFullyFetched();
                }
            };
        }
  
  
        
        
        
        
        static net.oneandone.troilus.java7.ResultList<net.oneandone.troilus.java7.Record> convertToJava7(ResultList<Record> recordList) {
            
            return new net.oneandone.troilus.java7.ResultList<net.oneandone.troilus.java7.Record>() {
                
                @Override
                public boolean wasApplied() {
                    return recordList.wasApplied();
                }
                
                @Override
                public ExecutionInfo getExecutionInfo() {
                    return recordList.getExecutionInfo();
                }
                
                @Override
                public ImmutableList<ExecutionInfo> getAllExecutionInfo() {
                    return recordList.getAllExecutionInfo();
                }

                public net.oneandone.troilus.java7.FetchingIterator<net.oneandone.troilus.java7.Record> iterator() {
                    
                    return new net.oneandone.troilus.java7.FetchingIterator<net.oneandone.troilus.java7.Record>() {
                        
                        private final FetchingIterator<Record> iterator = recordList.iterator();

                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }
                        
                        @Override
                        public net.oneandone.troilus.java7.Record next() {
                            return RecordAdapter.convertToJava7(iterator.next());
                        }
                        
                        @Override
                        public int getAvailableWithoutFetching() {
                            return iterator.getAvailableWithoutFetching();
                        }
                        
                        @Override
                        public ListenableFuture<ResultSet> fetchMoreResultsAsync() {
                            return CompletableFutures.toListenableFuture(iterator.fetchMoreResultsAsync());
                        }
                        
                        @Override
                        public boolean isFullyFetched() {
                            return iterator.isFullyFetched();
                        }
                    };
                }
            };
        }
   }

   
   

   static final class Java7RecordSubscriberAdapter implements Subscriber<Record> {
       private final Subscriber<? super net.oneandone.troilus.java7.Record> subscriber;
       
       public Java7RecordSubscriberAdapter(Subscriber<? super net.oneandone.troilus.java7.Record> subscriber) {
           this.subscriber = subscriber;
      }

      @Override
      public void onSubscribe(Subscription s) {
          subscriber.onSubscribe(s);
      }

      @Override
      public void onNext(Record record) {
          subscriber.onNext(RecordAdapter.convertToJava7(record));
      }

      @Override
      public void onError(Throwable t) {
          subscriber.onError(t);
      }
   
      @Override
      public void onComplete() {
          subscriber.onComplete();
      }
   }

   
        
   static class EntityListAdapter<F> extends ResultAdapter implements ResultList<F> {
       private final net.oneandone.troilus.java7.ResultList<F> entityList;
   
       EntityListAdapter(net.oneandone.troilus.java7.ResultList<F> entityList) {
           super(entityList);
           this.entityList = entityList;
       }
       
       @Override
       public FetchingIterator<F> iterator() {
   
           return new FetchingIterator<F>() {
               final net.oneandone.troilus.java7.FetchingIterator<F> recordIt = entityList.iterator();
               
               @Override
               public boolean hasNext() {
                   return recordIt.hasNext();
               }
           
               @Override
               public F next() {
                   return recordIt.next();
               }
               
               @Override
               public int getAvailableWithoutFetching() {
                   return recordIt.getAvailableWithoutFetching();
               }
               
               @Override
               public boolean isFullyFetched() {
                   return recordIt.isFullyFetched();
               }
               
               @Override
               public CompletableFuture<ResultSet> fetchMoreResultsAsync() {
                   return CompletableFutures.toCompletableFuture(recordIt.fetchMoreResultsAsync());
               }
           };
       }
   }
   

   
   static final class SubscriberAdapter<F> implements Subscriber<F> {
       private final Subscriber<? super F> subscriber;
       
       public SubscriberAdapter(Subscriber<? super F> subscriber) {
           this.subscriber = subscriber;
      }

      @Override
      public void onSubscribe(Subscription s) {
          subscriber.onSubscribe(s);
      }

      @Override
      public void onNext(F t) {
          subscriber.onNext(t);
      }

      @Override
      public void onError(Throwable t) {
          subscriber.onError(t);
      }
   
      @Override
      public void onComplete() {
          subscriber.onComplete();
      }
   }
   
    
    private static class WriteQueryDataAdapter implements WriteQueryData {

        private final net.oneandone.troilus.java7.interceptor.WriteQueryData data;
            
        WriteQueryDataAdapter(net.oneandone.troilus.java7.interceptor.WriteQueryData data) {
            this.data = data;
        }
        
        @Override
        public WriteQueryDataAdapter keys(ImmutableMap<String, Object> keys) {
            return new WriteQueryDataAdapter(data.keys(keys));
        }
        
        @Override
        public WriteQueryDataAdapter whereConditions(ImmutableList<Clause> whereConditions) {
            return new WriteQueryDataAdapter(data.whereConditions(whereConditions));
        }

        @Override
        public WriteQueryDataAdapter valuesToMutate(ImmutableMap<String, Optional<Object>> valuesToMutate) {
            return new WriteQueryDataAdapter(data.valuesToMutate(toGuavaOptional(valuesToMutate)));
        }
     
        @Override
        public WriteQueryDataAdapter setValuesToAdd(ImmutableMap<String, ImmutableSet<Object>> setValuesToAdd) {
            return new WriteQueryDataAdapter(data.setValuesToAdd(setValuesToAdd));
        }
        
        @Override
        public WriteQueryDataAdapter setValuesToRemove(ImmutableMap<String, ImmutableSet<Object>> setValuesToRemove) {
            return new WriteQueryDataAdapter(data.setValuesToRemove(setValuesToRemove));
        }
     
        @Override
        public WriteQueryDataAdapter listValuesToAppend(ImmutableMap<String, ImmutableList<Object>> listValuesToAppend) {
            return new WriteQueryDataAdapter(data.listValuesToAppend(listValuesToAppend));
        }
       
        @Override
        public WriteQueryDataAdapter listValuesToPrepend(ImmutableMap<String, ImmutableList<Object>> listValuesToPrepend) {
            return new WriteQueryDataAdapter(data.listValuesToPrepend(listValuesToPrepend));
        }
     
        @Override
        public WriteQueryDataAdapter listValuesToRemove(ImmutableMap<String, ImmutableList<Object>> listValuesToRemove) {
            return new WriteQueryDataAdapter(data.listValuesToRemove(listValuesToRemove));
        }
     
        @Override
        public WriteQueryDataAdapter mapValuesToMutate(ImmutableMap<String, ImmutableMap<Object, Optional<Object>>> mapValuesToMutate) {
            return new WriteQueryDataAdapter(data.mapValuesToMutate(toGuavaOptional(mapValuesToMutate)));
        }
               
        @Override
        public WriteQueryDataAdapter onlyIfConditions(ImmutableList<Clause> onlyIfConditions) {
            return new WriteQueryDataAdapter(data.onlyIfConditions(onlyIfConditions));
        }

        @Override
        public WriteQueryDataAdapter ifNotExists(Optional<Boolean> ifNotExists) {
            return new WriteQueryDataAdapter(data.ifNotExists(ifNotExists.orElse(null)));
        }
        
        @Override
        public Tablename getTablename() {
            return data.getTablename();
        }
        
        @Override        
        public ImmutableMap<String, Object> getKeys() {
            return data.getKeys();
        }
        
        @Override
        public <T> boolean hasKey(ColumnName<T> name) {
            return data.hasKey(name);
        }
        
        @Override
        public boolean hasKey(String name) {
            return data.hasKey(name);
        }
        
        @Override
        public <T> T getKey(ColumnName<T> name) {
            return data.getKey(name);
        }
        
        @Override
        public Object getKey(String name) {
            return data.getKey(name);
        }
         
        @Override
        public ImmutableList<Clause> getWhereConditions() {
            return data.getWhereConditions();
        }

        @Override
        public ImmutableMap<String, Optional<Object>> getValuesToMutate() {
            return fromGuavaOptional(data.getValuesToMutate());
        }
        
        @Override
        public <T> boolean hasValueToMutate(ColumnName<T> name) {
            return data.hasValueToMutate(name);
        }
        
        @Override
        public boolean hasValueToMutate(String name) {
            return data.hasValueToMutate(name);
        }
        
        @Override
        public <T> T getValueToMutate(ColumnName<T> name) {
            return data.getValueToMutate(name);
        }
        
        @Override
        public Object getValueToMutate(String name) {
            return data.getValueToMutate(name);
        }
        
        @Override
        public ImmutableMap<String, ImmutableSet<Object>> getSetValuesToAdd() {
            return data.getSetValuesToAdd();
        }
        
        @Override
        public <T> boolean hasSetValuesToAdd(ColumnName<Set<T>> name) {
            return data.hasSetValuesToAdd(name);
        }
        
        @Override
        public boolean hasSetValuesToAdd(String name) {
            return data.hasSetValuesToAdd(name);
        }

        @Override
        public <T> ImmutableSet<T> getSetValuesToAdd(ColumnName<Set<T>> name) {
            return data.getSetValuesToAdd(name);
        }
        
        @Override
        public ImmutableSet<Object> getSetValuesToAdd(String name) {
            return data.getSetValuesToAdd(name);
        }
        
        @Override
        public <T> boolean hasSetValuesToAddOrSet(ColumnName<Set<T>> name) {
            return data.hasSetValuesToAddOrSet(name);
        }
        
        @Override
        public boolean hasSetValuesToAddOrSet(String name) {
            return data.hasSetValuesToAddOrSet(name);
        }
        
        @Override
        public <T> ImmutableSet<T> getSetValuesToAddOrSet( ColumnName<Set<T>> name) {
            return data.getSetValuesToAddOrSet(name);
        }
        
        @Override
        public ImmutableSet<Object> getSetValuesToAddOrSet(String name) {
            return data.getSetValuesToAddOrSet(name);
        }
        
        @Override
        public ImmutableMap<String, ImmutableSet<Object>> getSetValuesToRemove() {
            return data.getSetValuesToRemove();
        }
        
        @Override
        public <T> boolean hasSetValuesToRemove(ColumnName<Set<T>> name) {
            return data.hasSetValuesToRemove(name);
        }
        
        @Override
        public boolean hasSetValuesToRemove(String name) {
            return data.hasSetValuesToRemove(name);
        }
        
        @Override
        public <T> ImmutableSet<T> getSetValuesToRemove(ColumnName<Set<T>> name) {
            return data.getSetValuesToRemove(name);
        }
        
        @Override
        public ImmutableSet<Object> getSetValuesToRemove(String name) {
            return data.getSetValuesToRemove(name);
        }

        @Override
        public ImmutableMap<String, ImmutableList<Object>> getListValuesToAppend() {
            return data.getListValuesToAppend();
        }
        
        @Override
        public <T> boolean hasListValuesToAppend(ColumnName<List<T>> name) {
            return data.hasListValuesToAppend(name);
        }
        
        @Override
        public boolean hasListValuesToAppend(String name) {
            return data.hasListValuesToAppend(name);
        }
        
        @Override
        public <T> ImmutableList<T> getListValuesToAppend(ColumnName<List<T>> name) {
            return data.getListValuesToAppend(name);
        }
        
        @Override
        public ImmutableList<Object> getListValuesToAppend(String name) {
            return data.getListValuesToAppend(name);
        }
        
        @Override
        public <T> boolean hasListValuesToPrepend(ColumnName<List<T>> name) {
            return data.hasListValuesToPrepend(name);
        }
        
        @Override
        public boolean hasListValuesToPrepend(String name) {
            return data.hasListValuesToPrepend(name);
        }
        
        @Override
        public <T> ImmutableList<T> getListValuesToPrepend(ColumnName<List<T>> name) {
            return data.getListValuesToPrepend(name);
        }
        
        @Override
        public ImmutableList<Object> getListValuesToPrepend(String name) {
            return data.getListValuesToPrepend(name);
        }
        

        @Override
        public ImmutableMap<String, ImmutableList<Object>> getListValuesToPrepend() {
            return data.getListValuesToPrepend();
        }

        @Override
        public <T> boolean hasListValuesToAddOrSet(ColumnName<List<T>> name) {
            return data.hasListValuesToAddOrSet(name);
        }
        
        @Override
        public boolean hasListValuesToAddOrSet(String name) {
            return data.hasListValuesToAddOrSet(name);
        }
        
        @Override
        public <T> ImmutableList<T> getListValuesToAddOrSet( ColumnName<List<T>> name) {
            return data.getListValuesToAddOrSet(name);
        }
        
        @Override
        public ImmutableList<Object> getListValuesToAddOrSet(String name) {
            return data.getListValuesToAddOrSet(name);
        }
        
        @Override
        public ImmutableMap<String, ImmutableList<Object>> getListValuesToRemove() {
            return data.getListValuesToRemove();
        }
        
        @Override
        public <T> boolean hasListValuesToRemove(ColumnName<List<T>> name) {
            return data.hasListValuesToRemove(name);
        }
        
        @Override
        public boolean hasListValuesToRemove(String name) {
            return data.hasListValuesToRemove(name);
        }
        
        @Override
        public ImmutableList<Object> getListValuesToRemove(String name) {
            return data.getListValuesToRemove(name);
        }
        
        @Override
        public <T> ImmutableList<T> getListValuesToRemove(ColumnName<List<T>> name) {
            return data.getListValuesToRemove(name);
        }

        @Override
        public ImmutableMap<String, ImmutableMap<Object, Optional<Object>>> getMapValuesToMutate() {
            final Map<String, ImmutableMap<Object, Optional<Object>>> result = Maps.newHashMap();
            for (Entry<String, ImmutableMap<Object, com.google.common.base.Optional<Object>>> entry : data.getMapValuesToMutate().entrySet()) {
                Map<Object, Optional<Object>> iresult = Maps.newHashMap();
                for (Entry<Object, com.google.common.base.Optional<Object>> entry2 : entry.getValue().entrySet()) {
                    iresult.put(entry2.getKey(), Optional.ofNullable(entry2.getValue().orNull()));
                }
                result.put(entry.getKey(), ImmutableMap.copyOf(iresult));
            }
            
            return ImmutableMap.copyOf(result);
        }
        
        @Override
        public <T, V> boolean hasMapValuesToMutate(ColumnName<Map<T, V>> name) {
            return data.hasMapValuesToMutate(name);
        }
        
        @Override
        public boolean hasMapValuesToMutate(String name) {
            return data.hasMapValuesToMutate(name);
        }

        @Override
        public <T, V> ImmutableMap<T, Optional<V>> getMapValuesToMutate( ColumnName<Map<T, V>> name) {
            return fromGuavaOptional(data.getMapValuesToMutate(name));
        }

        @Override
        public ImmutableMap<Object, Optional<Object>> getMapValuesToMutate(String name) {
            return fromGuavaOptional(data.getMapValuesToMutate(name));
        }
        
        @Override
        public ImmutableList<Clause> getOnlyIfConditions() {
            return data.getOnlyIfConditions();
        }
        
        @Override
        public Optional<Boolean> getIfNotExits() {
            return Optional.ofNullable(data.getIfNotExits());
        }

        private static <T, V> ImmutableMap<T, Optional<V>> fromGuavaOptional(ImmutableMap<T, com.google.common.base.Optional<V>> map) {
            final Map<T, Optional<V>> result = Maps.newHashMap();
            for (Entry<T, com.google.common.base.Optional<V>> entry : map.entrySet()) {
                result.put(entry.getKey(), Optional.ofNullable(entry.getValue().orNull()));
            }
            
            return ImmutableMap.copyOf(result);        
        }

        
        private static <T,V> ImmutableMap<T, com.google.common.base.Optional<V>> toGuavaOptional(ImmutableMap<T, Optional<V>> map) {
            final Map<T, com.google.common.base.Optional<V>> result = Maps.newHashMap();
            for (Entry<T, Optional<V>> entry : map.entrySet()) {
                result.put(entry.getKey(), com.google.common.base.Optional.fromNullable(entry.getValue().orElse(null)));
            }
                
            return ImmutableMap.copyOf(result);
        }
        

        private static ImmutableMap<String, ImmutableMap<Object, com.google.common.base.Optional<Object>>> toGuavaOptional(Map<String, ImmutableMap<Object, Optional<Object>>> map) {
            final Map<String, ImmutableMap<Object, com.google.common.base.Optional<Object>>> result = Maps.newHashMap();
            for (Entry<String, ImmutableMap<Object, Optional<Object>>> entry : map.entrySet()) {
                Map<Object, com.google.common.base.Optional<Object>> iresult = Maps.newHashMap();
                for (Entry<Object, Optional<Object>> entry2 : entry.getValue().entrySet()) {
                    iresult.put(entry2.getKey(), com.google.common.base.Optional.fromNullable(entry2.getValue().orElse(null)));
                }
                result.put(entry.getKey(), ImmutableMap.copyOf(iresult));
            }
            
            return ImmutableMap.copyOf(result);
        }
        
        static net.oneandone.troilus.java7.interceptor.WriteQueryData convert(WriteQueryData data) {
            return new WriteQueryDataImpl(data.getTablename()).keys(data.getKeys())
                                                              .whereConditions(data.getWhereConditions())
                                                              .valuesToMutate(toGuavaOptional(data.getValuesToMutate()))
                                                              .setValuesToAdd(data.getSetValuesToAdd())
                                                              .setValuesToRemove(data.getSetValuesToRemove())
                                                              .listValuesToAppend(data.getListValuesToAppend())
                                                              .listValuesToPrepend(data.getListValuesToPrepend())
                                                              .listValuesToRemove(data.getListValuesToRemove())
                                                              .mapValuesToMutate(toGuavaOptional(data.getMapValuesToMutate()))
                                                              .onlyIfConditions(data.getOnlyIfConditions())
                                                              .ifNotExists(data.getIfNotExits().orElse(null));
        }
    }
    
    

    
    private static final class ListReadQueryRequestInterceptorAdapter implements net.oneandone.troilus.java7.interceptor.ReadQueryRequestInterceptor {
        
        private ReadQueryRequestInterceptor interceptor;
        
        public ListReadQueryRequestInterceptorAdapter(ReadQueryRequestInterceptor interceptor) {
            this.interceptor = interceptor;
        }
        
        @Override
        public ListenableFuture<net.oneandone.troilus.java7.interceptor.ReadQueryData> onReadRequestAsync(net.oneandone.troilus.java7.interceptor.ReadQueryData data) {
            return CompletableFutures.toListenableFuture(interceptor.onReadRequestAsync(new ListReadQueryDataAdapter(data))
                                                                    .thenApply((queryData -> ListReadQueryDataAdapter.convert(queryData))));
        }
        
        @Override
        public String toString() {
            return "ListReadQueryPreInterceptor (with " + interceptor + ")";
        }
    }
   
    
    private static final class ListReadQueryResponseInterceptorAdapter implements net.oneandone.troilus.java7.interceptor.ReadQueryResponseInterceptor {
        
        private ReadQueryResponseInterceptor interceptor;
        
        public ListReadQueryResponseInterceptorAdapter(ReadQueryResponseInterceptor interceptor) {
            this.interceptor = interceptor;
        }
        
        @Override
        public ListenableFuture<net.oneandone.troilus.java7.ResultList<net.oneandone.troilus.java7.Record>> onReadResponseAsync(net.oneandone.troilus.java7.interceptor.ReadQueryData data, net.oneandone.troilus.java7.ResultList<net.oneandone.troilus.java7.Record> recordList) {
            return CompletableFutures.toListenableFuture(interceptor.onReadResponseAsync(new ListReadQueryDataAdapter(data), RecordListAdapter.convertFromJava7(recordList))
                                                                    .thenApply(list -> RecordListAdapter.convertToJava7(list)));
        }
        
        @Override
        public String toString() {
            return "ListReadQueryPostInterceptor (with " + interceptor + ")";
        }
    }
    
    
    private static final class WriteQueryRequestInterceptorAdapter implements net.oneandone.troilus.java7.interceptor.WriteQueryRequestInterceptor {
         
        private WriteQueryRequestInterceptor interceptor;
        
        public WriteQueryRequestInterceptorAdapter(WriteQueryRequestInterceptor interceptor) {
            this.interceptor = interceptor;
        }
        
        @Override
        public ListenableFuture<net.oneandone.troilus.java7.interceptor.WriteQueryData> onWriteRequestAsync(net.oneandone.troilus.java7.interceptor.WriteQueryData data) {
            return CompletableFutures.toListenableFuture(interceptor.onWriteRequestAsync(new WriteQueryDataAdapter(data))
                                                                    .thenApply(queryData -> WriteQueryDataAdapter.convert(queryData)));
        }
        
        @Override
        public String toString() {
            return "WriteQueryPreInterceptorAdapter (with " + interceptor + ")";
        }
    }
    
    
    
    private static final class DeleteQueryRequestInterceptorAdapter implements net.oneandone.troilus.java7.interceptor.DeleteQueryRequestInterceptor {
         
        private DeleteQueryRequestInterceptor interceptor;
        
        public DeleteQueryRequestInterceptorAdapter(DeleteQueryRequestInterceptor interceptor) {
            this.interceptor = interceptor;
        }
        
        
        @Override
        public ListenableFuture<DeleteQueryData> onDeleteRequestAsync(DeleteQueryData queryData) {
            return CompletableFutures.toListenableFuture(interceptor.onDeleteRequestAsync(queryData));
        }
        
        @Override
        public String toString() {
            return "WriteQueryPreInterceptorAdapter (with " + interceptor + ")";
        }
    }
    
    
    private static final class CascadeOnWriteInterceptorAdapter implements net.oneandone.troilus.java7.interceptor.CascadeOnWriteInterceptor {
        private CascadeOnWriteInterceptor interceptor;
        
        public CascadeOnWriteInterceptorAdapter(CascadeOnWriteInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public ListenableFuture<ImmutableSet<? extends Batchable<?>>> onWriteAsync(net.oneandone.troilus.java7.interceptor.WriteQueryData queryData) {
            return CompletableFutures.toListenableFuture(interceptor.onWrite(new WriteQueryDataAdapter(queryData))
                                                                    .thenApply(mutations -> mutations.stream().map(mutation -> Mutations.toJava7Mutation(mutation)).collect(Collectors.<net.oneandone.troilus.java7.Batchable<?>>toSet()))
                                                                    .thenApply(mutations -> ImmutableSet.copyOf(mutations)));
        }
        
        @Override
        public String toString() {
            return "CascadeOnWriteInterceptorAdapter (with " + interceptor + ")";
        }
    }

    
    private static final class CascadeOnDeleteInterceptorAdapter implements net.oneandone.troilus.java7.interceptor.CascadeOnDeleteInterceptor {
        private CascadeOnDeleteInterceptor interceptor;
        
        public CascadeOnDeleteInterceptorAdapter(CascadeOnDeleteInterceptor interceptor) {
            this.interceptor = interceptor;
        }
        
        @Override
        public ListenableFuture<ImmutableSet<? extends Batchable<?>>> onDeleteAsync(DeleteQueryData queryData) {
            return CompletableFutures.toListenableFuture(interceptor.onDelete(queryData)
                                                                    .thenApply(mutations -> mutations.stream().map(mutation -> Mutations.toJava7Mutation(mutation)).collect(Collectors.<net.oneandone.troilus.java7.Batchable<?>>toSet()))
                                                                    .thenApply(mutations -> ImmutableSet.copyOf(mutations)));
        }
        
        @Override
        public String toString() {
            return "CascadeOnDeleteInterceptorAdapter (with " + interceptor + ")";
        }
    }
}