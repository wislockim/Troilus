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
package net.oneandone.troilus.referentialintegrity;



import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import net.oneandone.troilus.CassandraDB;
import net.oneandone.troilus.ConstraintException;
import net.oneandone.troilus.Dao;
import net.oneandone.troilus.DaoImpl;
import net.oneandone.troilus.Record;
import net.oneandone.troilus.ResultList;
import net.oneandone.troilus.interceptor.ConstraintsInterceptor;
import net.oneandone.troilus.interceptor.ReadQueryData;
import net.oneandone.troilus.interceptor.ReadQueryResponseInterceptor;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;



public class DeviceTest {
    
    private static CassandraDB cassandra;
    
    
    @BeforeClass
    public static void beforeClass() throws IOException {
        cassandra = CassandraDB.newInstance();
    }
        
    @AfterClass
    public static void afterClass() throws IOException {
        cassandra.close();
    }

    
    @Before
    public void before() throws IOException {
        cassandra.tryExecuteCqlFile(PhonenumbersTable.DDL);
        cassandra.tryExecuteCqlFile(DeviceTable.DDL);
    }
    


    
    @Test
    public void testRI() throws Exception {           
           
        /*
            The phones table is used to assign a phone number to a device. The key is the phone number. The phone number table contains a mandatory device id column referring to the assigned device. The
            1, insert operations ensures that an existing phone row will not be overridden. 
            2, it should not be allowed to update the device id column. This means assigning a phone number to new devices requires to remove the old entry first and to create a new phones row.
            3, A phone number can not be assigned to a non-existing device. 
            4, by accessing the table entries the back relation should be check with cl one 
         */
                
        Dao phoneNumbersDao = new DaoImpl(cassandra.getSession(), "phone_numbers");
        Dao deviceDao = new DaoImpl(cassandra.getSession(), "device");
        
        
        
        
        Dao phoneNumbersDaoWithConstraints = phoneNumbersDao.withInterceptor(new PhonenumbersConstraints(deviceDao))
                                                            .withInterceptor(ConstraintsInterceptor.newConstraints()
                                                                                                   .withNotNullColumn("device_id")
                                                                                                   .withImmutableColumn("device_id"));
        Dao deviceDaoWithConstraints = deviceDao.withInterceptor(new DeviceConstraints(phoneNumbersDao));


        
        deviceDaoWithConstraints.writeWithKey("device_id", "834343")
                                .value(DeviceTable.TYPE, 3)
                                .ifNotExists()
                                .execute();
        
        
        deviceDaoWithConstraints.writeWithKey("device_id", "2333243")
                                .value(DeviceTable.TYPE, 1)
                                .ifNotExists()
                                .execute();

        
        deviceDaoWithConstraints.writeWithKey("device_id", "934453434")
                                .value(DeviceTable.TYPE, 3)
                                .ifNotExists()
                                .execute();
        
        
        
        
        phoneNumbersDao.writeWithKey(PhonenumbersTable.NUMBER, "0089645454455")
                       .value(PhonenumbersTable.DEVICE_ID, "834343")
                       .value(PhonenumbersTable.ACTIVE, true)
                       .ifNotExists()
                       .execute();

        deviceDaoWithConstraints.writeWithKey("device_id", "834343")
                                .addSetValue("phone_numbers", "0089645454455")
                                .execute();
        
        
        
        // insert new  entry
        phoneNumbersDao.writeWithKey(PhonenumbersTable.NUMBER, "0089123234234")
                       .value(PhonenumbersTable.DEVICE_ID, "2333243")
                       .value(PhonenumbersTable.ACTIVE, true)
                       .ifNotExists()
                       .execute();

        deviceDaoWithConstraints.writeWithKey("device_id", "2333243")
                                .addSetValue("phone_numbers", "0089123234234")
                                .execute();



        
        
        // update modifiable column
        phoneNumbersDaoWithConstraints.writeWithKey(PhonenumbersTable.NUMBER, "0089123234234")
                                      .value(PhonenumbersTable.ACTIVE, false)
                                      .execute();
        
    
        // update non-modifiable column
        try {
            phoneNumbersDaoWithConstraints.writeWithKey(PhonenumbersTable.NUMBER, "0089123234234")
                                          .value(PhonenumbersTable.DEVICE_ID, "dfacbsd")
                                          .execute();
            Assert.fail("ConstraintException expected");
        } catch (ConstraintException expected) { 
            Assert.assertTrue(expected.getMessage().contains("immutable column device_id can not be updated"));
        }
    
  
        // insert without device id 
        try {
            phoneNumbersDaoWithConstraints.writeWithKey(PhonenumbersTable.NUMBER, "08834334")
                                          .value(PhonenumbersTable.ACTIVE, true)
                                          .ifNotExists()
                                          .execute();
            Assert.fail("ConstraintException expected");
        } catch (ConstraintException expected) {
            Assert.assertTrue(expected.getMessage().contains("NOT NULL column(s) device_id has to be set"));
        }
        

        /*
        // insert with unknown device id 
        try {
            phoneNumbersDaoWithConstraints.writeWithKey(PhonenumbersTable.NUMBER, "08834334")
                                          .value(PhonenumbersTable.DEVICE_ID, "doesNotExits")
                                          .value(PhonenumbersTable.ACTIVE, true)
                                          .ifNotExists()
                                          .execute();
            Assert.fail("ConstraintException expected");
        } catch (ConstraintException expected) {
            Assert.assertTrue(expected.getMessage().contains("device with id"));
        }
        */
          
        
        // read 
        phoneNumbersDaoWithConstraints.readWithKey(PhonenumbersTable.NUMBER, "0089645454455")
                                      .execute()
                                      .get();
        

        phoneNumbersDaoWithConstraints.readWithKey(PhonenumbersTable.NUMBER, "0089645454455")
                                      .execute()
                                      .get();

  /*
        phoneNumbersDaoWithConstraints.readWithKey(PhonenumbersTable.NUMBER, "0089645454455")
                                      .column("active")
                                      .execute()
                                      .get();
    */    

        // modify record to make it inconsistent 
        phoneNumbersDao.writeWithKey(PhonenumbersTable.NUMBER, "0089645454455")
                       .value("device_id", "2333243")
                       .execute();

        
        // read inconsistent record
        try {
            phoneNumbersDaoWithConstraints.readWithKey(PhonenumbersTable.NUMBER, "0089645454455")
                                          .execute()
                                          .get();
            Assert.fail("ConstraintException expected");
        } catch (ConstraintException expected) {
            Assert.assertTrue(expected.getMessage().contains("reverse reference devices table -> phone_numbers table does not exit"));
        }
            
/*
        try {
            phoneNumbersDaoWithConstraints.readWithKey(PhonenumbersTable.NUMBER, "0089645454455")
                                          .column("active")
                                          .execute()
                                          .get();
    
            Assert.fail("ConstraintException expected");
        } catch (ConstraintException expected) {
            Assert.assertTrue(expected.getMessage().contains("reverse reference devices table -> phone_numbers table does not exit"));
        }*/
    }       
 
       
       
    
    

    

    private static final class DeviceConstraints implements ReadQueryResponseInterceptor {
        private final Dao phoneNumbersDao;
                    
        public DeviceConstraints(Dao phoneNumbersDao) {
            this.phoneNumbersDao = phoneNumbersDao;
        }
        

        @Override
        public CompletableFuture<ResultList<Record>> onReadResponseAsync(ReadQueryData queryData, ResultList<Record> recordList) {

            // check is related phone numbers points to this device
            return CompletableFuture.completedFuture(recordList);
        }
    }

}



