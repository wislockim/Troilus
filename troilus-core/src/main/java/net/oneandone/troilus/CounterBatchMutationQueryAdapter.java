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


import net.oneandone.troilus.Context;
import net.oneandone.troilus.CounterBatchMutationQuery;


 

/**
 * Java8 adapter of a CounterBatchMutationQuery
 */
class CounterBatchMutationQueryAdapter extends AbstractQueryAdapter<CounterMutation> implements CounterMutation {
    
    private final CounterBatchMutationQuery query;
    
     
    /**
     * @param ctx     the context 
     * @param query   the underlying query
     */
    CounterBatchMutationQueryAdapter(Context ctx, CounterBatchMutationQuery query) {
        super(ctx, query);
        this.query = query;
    }

    
    ////////////////////
    // factory methods
    
    @Override
    protected CounterMutation newQuery(Context newContext) {
        return new CounterBatchMutationQueryAdapter(newContext, query.newQuery(newContext));
    }

    private CounterMutation newQuery(CounterBatchMutationQuery query) {
        return new CounterBatchMutationQueryAdapter(getContext(), query.newQuery(getContext()));
    }
    
    //
    ////////////////////

    
    @Override
    public CounterMutation combinedWith(CounterMutation other) {
        return newQuery(query.combinedWith(CounterMutationQueryAdapter.toJava7CounterMutation(other)));
    }
}
