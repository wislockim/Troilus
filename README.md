
Troilus
======
**Troilus** is a high level Cassandra Java8 client on the top of the [DataStax Java Driver for Apache Cassandra](https://github.com/datastax/java-driver). 
It supports synchronous programming as well as asynchronous programming including the [reactive streams](http://www.reactive-streams.org) interface.


#Examples
-------

##Create a Dao
First a DataStax Java Driver [Session](https://github.com/datastax/java-driver) object has to be created
``` java
Cluster cluster = Cluster.builder()
                         .addContactPoint(node)
                         .build();
Session session = cluster.connect("hotel_reservation_system");
```

This Session object will be used to create the DaoManager and fetching Dao's based on the table name. In the examples below the [hotels table](src/test/resources/com/unitedinternet/troilus/example/hotels.ddl) is used
``` java
DaoManager daoManager = new DaoManager(session);
Dao hotelsDao = daoManager.getDao("hotels")
                          .withConsistency(ConsistencyLevel.LOCAL_QUORUM)
                          .withSerialConsistency(ConsistencyLevel.SERIAL);
```

##Write
Write a row in a column-oriented way
``` java
hotelsDao.writeWithKey("id", "BUP932432")
         .value("name", "City Budapest")
         .value("room_ids", ImmutableSet.of("1", "2", "3", "122", "123", "124", "322", "333"))
         .value("classification", 4)
         .withWritetime(microsSinceEpoch)
         .execute();
```


Write a row in an entity-oriented way.  
``` java
hotelsDao.writeEntity(new Hotel("BUP14334", 
                                "Richter Panzio",
                                ImmutableSet.of("1", "2", "3", "4", "5"),
                                Optional.of(2),
                                Optional.empty()))
         .execute();
```

The columns will be mapped by using `@Field` annotated fields
``` java
public class Hotel  {
   
    @Field(name = "id")
    private String id = null;
    
    @Field(name = "name")
    private String name = null;

    @Field(name = "room_ids")
    private ImmutableSet<String> roomIds = ImmutableSet.of();

    @Field(name = "classification")
    private Optional<Integer> classification = Optional.empty();
    
    @Field(name = "description")
    private Optional<String> description = Optional.empty();

    
    @SuppressWarnings("unused")
    private Hotel() { }
    
    public Hotel(String id, 
                 String name, 
                 ImmutableSet<String> roomIds,  
                 Optional<Integer> classification, 
                 Optional<String> description) {
        this.id = id;
        this.name = name;
        this.roomIds = roomIds;
        this.classification = classification;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    
    public ImmutableSet<String> getRoomIds() {
        return roomIds;
    }

    public Optional<Integer> getClassification() {
        return classification;
    }

    public Optional<String> getDescription() {
        return description;
    }
}
```


### updating values
``` java
hotelsDao.writeWithKey("id","BUP932432")
         .value("description", "The City Budapest is in the business district on the Pest side of the river.")
         .execute();
  ```               

### removing values
``` java
hotelsDao.writeWithKey("id","BUP932432")
         .value("description", null)
         .execute();
  ```             


### conditional value update 
``` java
hotelsDao.writeWhere(QueryBuilder.in(HotelsTable.ID, "BUP932432", "BUP233544", "BUP2433"))
         .value(HotelsTable.CLASSIFICATION, 4)
         .execute();
  ```               
        

### lightweight transactions 
unique insert with `ifNotExits()`(performs the insertion only if the row does not already exist)        
``` java
hotelsDao.writeWithKey("id", "BUP932432")
         .value("name", "City Budapest")
         .value("room_ids", ImmutableSet.of("1", "2", "3", "122", "123", "124", "322", "333"))
         .value("classification", 4)
         .withWritetime(microsSinceEpoch)
         .ifNotExits()
         .execute();
  ```  
        
safe update with `onlyIf(..conditions..)` (uses IF followed by a condition to be met for the update to succeed)        
``` java
hotelsDao.writeWithKey(HotelsTable.ID, "BUP932432")
         .value("classification", 5)
	     .onlyIf(QueryBuilder.eq("classification", 4))
         .execute();
  ```  
       
        
##Delete

``` java
hotelsDao.deleteWithKey("id", "BUP932432")
         .execute();
```


##Batching        
Non conditional mutating operations (insert, update, delete) can be executed in a batched manner by combining it with another mutating operation
``` java
Batchable deletion = hotelsDao.deleteWithKey("id", "BUP932432");

hotelsDao.deleteWithKey("id", "BUP14334")
         .combinedWith(deletion)
         .withLockedBatchType()
         .execute();
```


##Read
###Read a single row

Read a row in an entity-oriented way.  
``` java        
Optional<Hotel> optionalHotel = hotelsDao.readWithKey("id", "BUP45544")
                                         .asEntity(Hotel.class)
                                         .execute();
optionalHotel.ifPresent(hotel -> System.out.println(hotel.getName()));
```        

Read a row in a column-oriented way
``` java        
Optional<Record> optionalRecord = hotelsDao.readWithKey("id", "BUP14334")
                                           .column("id")
                                           .column("name")
                                           .withConsistency(ConsistencyLevel.LOCAL_ONE)
                                           .execute();
optionalRecord.ifPresent(record -> record.getString("name").ifPresent(name -> System.out.println(name)));
```        


Read with meta data (ttl, writetime)
``` java        
Record record = hotelsDao.readWithKey("id", "BUP14334")
                         .column("id")
         	             .column("name")
            	         .columnWithMetadata("description")
                         .withConsistency(ConsistencyLevel.LOCAL_ONE)
                         .execute()
                         .get();
                                           
record.getTtl("description").ifPresent(ttl -> System.out.println("ttl=" + ttl)));
```        

  

###Read a list of rows

Read all of the table
``` java  
Iterator<Hotel> hotelIterator = hotelsDao.readAll()
                                         .asEntity(Hotel.class)
                                         .withLimit(5000)
                                         .execute();
hotelIterator.forEachRemaining(hotel -> System.out.println(hotel));
```        
        

Read specific ones by using conditions
``` java  
Iterator<Hotel> hotelIterator = hotelsDao.readWhere(QueryBuilder.in("ID", "BUP45544", "BUP14334"))
                                         .asEntity(Hotel.class)
                                         .withAllowFiltering()
                                         .execute();
hotelIterator.forEachRemaining(hotel -> System.out.println(hotel));                
```        
        

      
#User-defined types support
-------

to use the user-defined types support a Java class which represents the user-definied type has to be implemented. The fields to be mapped have to be annotated with `@Field`


``` java
public class Address {

    @Field(name = "street")
    private String street;
    
    @Field(name = "city")
    private String city;
    
    @Field(name = "post_code")
    private String postCode;
    
        
    @SuppressWarnings("unused")
    private Address() { }

    
    public Address(String street, String city, String postCode) {
        this.street = street;
        this.city = city;
        this.postCode = postCode;
    }


    public String getStreet() {
        return street;
    }


    public String getCity() {
        return city;
    }


    public String getPostCode() {
        return postCode;
    }
}
```



##Write

Write a row in a column-oriented way
``` java
hotelsDao.writeWithKey("id", "BUP932432")
         .value("name", "City Budapest")
         .value("room_ids", ImmutableSet.of("1", "2", "3", "122", "123", "124", "322", "333"))
         .value("classification", 4)
         .value("address", new Address("Th�k�ly Ut 111", "Budapest", "1145"))
         .withWritetime(microsSinceEpoch)
         .execute();
```


Write a row in a entity-oriented way
``` java
hotelsDao.writeEntity(new Hotel("BUP14334", 
                                "Richter Panzio",
                                ImmutableSet.of("1", "2", "3", "4", "5"),
                                Optional.of(2),
                                Optional.empty(),
                                new Address("Th�k�ly Ut 111", "Budapest", "1145")))
         .execute();
```

``` java
public class Hotel  {
   
    @Field(name = "id")
    private String id = null;
    
    @Field(name = "name")
    private String name = null;

    @Field(name = "room_ids")
    private ImmutableSet<String> roomIds = ImmutableSet.of();

    @Field(name = "classification")
    private Optional<Integer> classification = Optional.empty();
    
    @Field(name = "description")
    private Optional<String> description = Optional.empty();

    @Field(name = "address")
    private Address address;

    
    @SuppressWarnings("unused")
    private Hotel() { }
    
     public Hotel(String id, 
                 String name, 
                 ImmutableSet<String> roomIds,  
                 Optional<Integer> classification, 
                 Optional<String> description,
                 Address address) {
        this.id = id;
        this.name = name;
        this.roomIds = roomIds;
        this.classification = classification;
        this.description = description;
        this.address = address;
    }


    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
    
    public ImmutableSet<String> getRoomIds() {
        return roomIds;
    }

    public Optional<Integer> getClassification() {
        return classification;
    }

    public Optional<String> getDescription() {
        return description;
    }
    
    public Address getAddress() {
        return address;
    }
}
```



##Read

Read a row in a entity-oriented way
``` java
hotel = hotelsDao.readWithKey("id", "BUP14334")
                 .asEntity(Hotel.class)
                 .execute()
                 .get();
        
System.out.println(hotel.getAddress());
```


Read a row in a column-oriented way
``` java
record = hotelsDao.readWithKey("id", "BUP14334")
                  .column("id")
                  .column("name")
                  .column("classification")
                  .column("address")
                  .withConsistency(ConsistencyLevel.LOCAL_ONE)
                  .execute()
                  .get();
        
System.out.println(record.getInt("classification"));
System.out.println(record.getObject("address", Address.class));
```


        
#Asynchronous Examples
-------

##Async Write
By calling `executeAsync()` instead `execute()` the method returns immediately without waiting for the database response. Further more the `executeAsync()` returns a Java8 [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html) object which can be used for async processing
``` java
CompletableFuture<Result> future = hotelsDao.writeEntity(new Hotel("BUP14334", "Richter Panzio", Optional.of(2), Optional.empty()))
                                            .withConsistency(ConsistencyLevel.ANY)
                                            .executeAsync();
```


##Async Read

As already mentioned above the methods returns immediately without waiting for the database response. The consumer code within the `thenAccept(...)` method will be called as soon as the database response is received. 

read single row
``` java
hotelsDao.readWithKey("id", "BUP45544")
         .asEntity(Hotel.class)
	     .executeAsync()
         .thenAccept(optionalHotel.ifPresent(hotel -> System.out.println(hotel));
```


read a list of rows. Please consider that the Iterator has a blocking behavior which means the streaming of the result could block
``` java
hotelsDao.readAll()
         .asEntity(Hotel.class)
         .withLimit(5000)
         .executeAsync()
         .thenAccept(hotelIterator -> hotelIterator.forEachRemaining(hotel -> System.out.println(hotel)));
```

For true asynchronous streaming a [Subscriber](http://www.reactive-streams.org) could be registered which will be executed in an asynchronous, reactive way
``` java
Subscriber<Hotel> mySubscriber = new MySubscriber();  

hotelsDao.readAll()
         .asEntity(Hotel.class)
         .withLimit(5000)
         .executeAsync()
         .thenAccept(publisher -> publisher.subscribe(mySubscriber));
```

The Subscriber implements call back methods such as `onNext(...)` or `onError(...)` to process the result stream in a reactive way. By calling the hotels.subscribe(subscriber) above the `onSubscribe(...)` method of the subscriber below will be called.
``` java
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class MySubscriber<T> implements Subscriber<Hotel> {
    private final AtomicReference<Subscription> subscriptionRef = new AtomicReference<>();
    //...
    
    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscriptionRef.set(subscription);
        subscription.request(1);  // here, requesting elements starts the streaming implicitly
    }
    
    @Override
    public void onNext(Hotel hotel) {
        System.out.println(hotel);
        subscription.request(1);
    }
    
    @Override
    public void onComplete() {
        //..
    }
    
    @Override
    public void onError(Throwable t) {
        //..
    }
}
```
