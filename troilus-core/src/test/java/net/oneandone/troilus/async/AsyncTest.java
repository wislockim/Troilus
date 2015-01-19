package net.oneandone.troilus.async;



import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import net.oneandone.troilus.AbstractCassandraBasedTest;
import net.oneandone.troilus.Dao;
import net.oneandone.troilus.DaoImpl;
import net.oneandone.troilus.Record;
import net.oneandone.troilus.Result;
import net.oneandone.troilus.TooManyResultsException;
import net.oneandone.troilus.api.FeesTable;

import org.junit.Assert;
import org.junit.Test;

import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.collect.ImmutableList;


public class AsyncTest extends AbstractCassandraBasedTest {
    
    
    
    @Test
    public void testAsync() throws Exception {
        Dao feeDao = new DaoImpl(getSession(), FeesTable.TABLE);

        
        // delete records of previous tests
        for (Record record : feeDao.readAll().execute()) {
            feeDao.deleteWithKey(FeesTable.CUSTOMER_ID, record.getString(FeesTable.CUSTOMER_ID),
                                 FeesTable.YEAR, record.getInt(FeesTable.YEAR));
        }
        
        
        
        ////////////////
        // inserts
        CompletableFuture<Result> insert1 = feeDao.writeWithKey(FeesTable.CUSTOMER_ID, "132", FeesTable.YEAR, 3)
                                                  .value(FeesTable.AMOUNT, 23433)
                                                 .executeAsync();
        
        CompletableFuture<Result> insert2 = feeDao.writeWithKey(FeesTable.CUSTOMER_ID, "132", FeesTable.YEAR, 4)
                                                  .value(FeesTable.AMOUNT, 1223)
                                                  .executeAsync();

        CompletableFuture<Result> insert3 = feeDao.writeWithKey(FeesTable.CUSTOMER_ID, "132", FeesTable.YEAR, 8)
                                                  .value(FeesTable.AMOUNT, 23233)
                                                  .executeAsync();
        
        CompletableFuture.allOf(insert1, insert2, insert3)
                         .get();  // waits for completion
        
        
        
        try {
            feeDao.readWithKey(FeesTable.CUSTOMER_ID, "132")
                  .columns(FeesTable.CUSTOMER_ID, FeesTable.YEAR, FeesTable.AMOUNT)
                  .executeAsync()
                  .get();   // waits for completion
            
            Assert.fail("TooManyResultsException expected");
        } catch (ExecutionException expected) { 
            Assert.assertTrue(expected.getCause() instanceof TooManyResultsException);
        }
        
        
        
        ////////////////
        // reads
        ImmutableList<Record> recs = feeDao.readWhere(QueryBuilder.eq(FeesTable.CUSTOMER_ID, "132"))
                                           .column(FeesTable.CUSTOMER_ID)
                                           .executeAsync()
                                           .thenApply(iterable -> iterable.iterator())
                                           .thenApply(result -> ImmutableList.of(result.next(), result.next(), result.next()))
                                           .get();   // waits for completion
        Assert.assertEquals(3, recs.size());
        
        
        

        Record record = feeDao.readWithKey(FeesTable.CUSTOMER_ID, "132", FeesTable.YEAR, 4)
                              .columns(FeesTable.CUSTOMER_ID, FeesTable.YEAR, FeesTable.AMOUNT)
                              .executeAsync()
                              .thenApply(optionalRecord -> optionalRecord.<RuntimeException>orElseThrow(RuntimeException::new))
                              .get();   // waits for completion;
        Assert.assertEquals((Integer) 4, record.getInt(FeesTable.YEAR).get());
    }        
}

