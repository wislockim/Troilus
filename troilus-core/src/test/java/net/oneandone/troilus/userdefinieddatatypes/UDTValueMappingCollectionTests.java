/**
 * 
 */
package net.oneandone.troilus.userdefinieddatatypes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import net.oneandone.troilus.CassandraDB;
import net.oneandone.troilus.Dao;
import net.oneandone.troilus.DaoImpl;
import net.oneandone.troilus.Field;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import com.datastax.driver.core.Session;



/**
 * Tests APIs against Single embedded UDT, List of UDT, Set of UDT and Map of UDT
 * 
 * Hotels example only tests collections with native DataTypes
 * UserDefinedDataTypesTest.java does not test updating and entity with Collections of UDTValue
 * 
 * @author Jason Westra 12-10-2015
 *
 */
@RunWith(value=BlockJUnit4ClassRunner.class)
public class UDTValueMappingCollectionTests extends TestCase {

	
	private static CassandraDB cassandra;
	 
	Session session;
	
	public static final String keyspace = "ks_"+System.currentTimeMillis();
	
	public static final String TABLE_MOCK_WITH_UDT_LIST = "mock_with_udt_list";
	public static final String TABLE_MOCK_WITH_UDT_SET = "mock_with_udt_set";
	public static final String TABLE_MOCK_WITH_UDT_MAP = "mock_with_udt_map";
	public static final String TABLE_MOCK_WITH_UDT = "mock_with_udt_single";
	
	@BeforeClass
    public static void beforeClass() throws IOException {
        cassandra = CassandraDB.newInstance();
        
        dropKeyspace();
		createKeyspace();
		createUDTs();
		createTables();	
    }
        
    @AfterClass
    public static void afterClass() throws IOException {
    	dropKeyspace();
        cassandra.close();
    }
	    
	@Before
	public void setUp() throws Exception {
		session = cassandra.getSession();
		
	}
		
	private static void createKeyspace() {
		cassandra.getSession().execute("CREATE KEYSPACE "+keyspace+" with replication={'class': 'SimpleStrategy', 'replication_factor' : 1};");
	}

	private static void createTables() {
		Session session = cassandra.getSession();
		
		session.execute("CREATE TABLE "+keyspace+"."+TABLE_MOCK_WITH_UDT_LIST+" (id text, version bigint, create_date timestamp, descriptions list<frozen<description>>, PRIMARY KEY (id));");
		session.execute("CREATE TABLE "+keyspace+"."+TABLE_MOCK_WITH_UDT_SET+" (id text, version bigint, create_date timestamp, descriptions set<frozen<description>>, PRIMARY KEY (id));");
		session.execute("CREATE TABLE "+keyspace+"."+TABLE_MOCK_WITH_UDT_MAP+" (id text, version bigint, create_date timestamp, descriptions map<text,frozen<description>>, PRIMARY KEY (id));");
		session.execute("CREATE TABLE "+keyspace+"."+TABLE_MOCK_WITH_UDT+" (id text, version bigint, create_date timestamp, description frozen<description>, PRIMARY KEY (id));");
	}
	
	
	private static void createUDTs() {
		Session session = cassandra.getSession();
		session.execute("CREATE TYPE "+keyspace+".description (name text, time timestamp)");
	}

	private static void dropKeyspace() {
		try {
			Session session = cassandra.getSession();
			session.execute("DROP KEYSPACE "+keyspace+";");
		} catch(Exception e) {
			
		}
	}
	
	@Test
	public void testEntityWithUDTSingle() throws Exception {
		MockDOWithUDTSingle dataObject = new MockDOWithUDTSingle();
		dataObject.setCreateDate(new Date());
		dataObject.setId(System.currentTimeMillis()+"");
		dataObject.setVersion(1);
		
		DescriptionUDT description = new DescriptionUDT();
		description.name = "someName";
		description.time = new Date();
		
		dataObject.setDescription(description);
		
		Dao dao = new DaoImpl(session, keyspace, TABLE_MOCK_WITH_UDT_LIST);
		dao.writeEntity(dataObject)
			.ifNotExists()
			.execute();
		
		MockDOWithUDTSingle entityAsInserted = null;
		try {
			entityAsInserted = dao.readWithKey("id", dataObject.getId())
					.asEntity(MockDOWithUDTSingle.class)
					.execute().get();
		} catch(Exception e) {
			e.printStackTrace();
			// if will fail on assert below
		}
		
		assertNotNull(entityAsInserted);
	}
		
	@Test
	public void testEntityWithUDTList() throws Exception {
		MockDOWithUDTList dataObject = new MockDOWithUDTList();
		dataObject.setCreateDate(new Date());
		dataObject.setId(System.currentTimeMillis()+"");
		dataObject.setVersion(1);
		
		DescriptionUDT description1 = new DescriptionUDT();
		description1.name = "someName1";
		description1.time = new Date();
		
		DescriptionUDT description2 = new DescriptionUDT();
		description2.name = "someName2";
		description2.time = new Date();
				
		ArrayList<DescriptionUDT> descriptions = new ArrayList<DescriptionUDT>(); 
		descriptions.add(description1);
		descriptions.add(description2);
		
		dataObject.setDescriptions(descriptions);
		
		Dao dao = new DaoImpl(session, keyspace, TABLE_MOCK_WITH_UDT_LIST);
		dao.writeEntity(dataObject)
			.ifNotExists()
			.execute();
		
		MockDOWithUDTList entityAsInserted = null;
		try {
			entityAsInserted = dao.readWithKey("id", dataObject.getId())
					.asEntity(MockDOWithUDTList.class)
					.execute().get();
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		}
		
		assertNotNull(entityAsInserted);
		assertTrue(entityAsInserted.getDescriptions().size() == 2);
		
	}
	
	@Test
	public void testEntityWithUDTSet() throws Exception {
		MockDOWithUDTSet dataObject = new MockDOWithUDTSet();
		dataObject.setCreateDate(new Date());
		dataObject.setId(System.currentTimeMillis()+"");
		dataObject.setVersion(1);
		
		DescriptionUDT description1 = new DescriptionUDT();
		description1.name = "someName1";
		description1.time = new Date();
		
		DescriptionUDT description2 = new DescriptionUDT();
		description2.name = "someName2";
		description2.time = new Date();
				
		HashSet<DescriptionUDT> descriptions = new HashSet<DescriptionUDT>(); 
		descriptions.add(description1);
		descriptions.add(description2);
		
		dataObject.setDescriptions(descriptions);
		
		Dao dao = new DaoImpl(session, keyspace, TABLE_MOCK_WITH_UDT_SET);
		dao.writeEntity(dataObject)
			.ifNotExists()
			.execute();
		
		MockDOWithUDTSet entityAsInserted = null;
		try {
			entityAsInserted = dao.readWithKey("id", dataObject.getId())
					.asEntity(MockDOWithUDTSet.class)
					.execute().get();
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		}
		
		assertNotNull(entityAsInserted);
		assertTrue(entityAsInserted.getDescriptions().size() == 2);
		
	}
	
	@Test
	public void testEntityWithUDTMap() throws Exception {
		MockDOWithUDTMap dataObject = new MockDOWithUDTMap();
		dataObject.setCreateDate(new Date());
		dataObject.setId(System.currentTimeMillis()+"");
		dataObject.setVersion(1);
		
		DescriptionUDT description1 = new DescriptionUDT();
		description1.name = "someName1";
		description1.time = new Date();
		
		DescriptionUDT description2 = new DescriptionUDT();
		description2.name = "someName2";
		description2.time = new Date();
				
		Map<String, DescriptionUDT> descriptions = new HashMap<String, DescriptionUDT>(); 
		descriptions.put("1", description1);
		descriptions.put("2", description2);
		
		dataObject.setDescriptions(descriptions);
		
		Dao dao = new DaoImpl(session, keyspace, TABLE_MOCK_WITH_UDT_MAP);
		dao.writeEntity(dataObject)
			.ifNotExists()
			.execute();
		
		MockDOWithUDTMap entityAsInserted = null;
		try {
			entityAsInserted = dao.readWithKey("id", dataObject.getId())
					.asEntity(MockDOWithUDTMap.class)
					.execute().get();
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		}
		
		assertNotNull(entityAsInserted);
		assertTrue(entityAsInserted.getDescriptions().size() == 2);
		
	}
	
	
	

	
	
	// Tests @Field shows up on subclasses
	abstract public static class AbstractDO {
		
		@Field(name="id")
		private String id;
				
		@Field(name="create_date")
		private Date createDate;
				
		@Field(name="version")
		private long version;

		/**
		 * @return the id
		 */
		public String getId() {
			return id;
		}

		/**
		 * @param id the id to set
		 */
		public void setId(String id) {
			this.id = id;
		}

		/**
		 * @return the createDate
		 */
		public Date getCreateDate() {
			return createDate;
		}

		/**
		 * @param createDate the createDate to set
		 */
		public void setCreateDate(Date createDate) {
			this.createDate = createDate;
		}

		/**
		 * @return the version
		 */
		public long getVersion() {
			return version;
		}

		/**
		 * @param version the version to set
		 */
		public void setVersion(long version) {
			this.version = version;
		}
	}
	
	public static class MockDOWithInheritance extends AbstractDO {
		
		@Field(name="latitude")
		private BigDecimal latitude;

		/**
		 * @return the latitude
		 */
		public BigDecimal getLatitude() {
			return latitude;
		}

		/**
		 * @param latitude the latitude to set
		 */
		public void setLatitude(BigDecimal latitude) {
			this.latitude = latitude;
		}
	}
	
	public static class MockDOWithUDTList extends AbstractDO {
		
		@Field(name="descriptions")
		private List<DescriptionUDT> descriptions;

		/**
		 * @return the descriptions
		 */
		public List<DescriptionUDT> getDescriptions() {
			return descriptions;
		}

		/**
		 * @param descriptions the descriptions to set
		 */
		public void setDescriptions(List<DescriptionUDT> descriptions) {
			this.descriptions = descriptions;
		}
	}
	
	public static class MockDOWithUDTSet extends AbstractDO {
		
		@Field(name="descriptions")
		private Set<DescriptionUDT> descriptions;

		/**
		 * @return the descriptions
		 */
		public Set<DescriptionUDT> getDescriptions() {
			return descriptions;
		}

		/**
		 * @param descriptions the descriptions to set
		 */
		public void setDescriptions(Set<DescriptionUDT> descriptions) {
			this.descriptions = descriptions;
		}
	}
	
	public static class MockDOWithUDTMap extends AbstractDO {
		
		@Field(name="descriptions")
		private Map<String, DescriptionUDT> descriptions;

		/**
		 * @return the descriptions
		 */
		public Map<String, DescriptionUDT> getDescriptions() {
			return descriptions;
		}

		/**
		 * @param descriptions the descriptions to set
		 */
		public void setDescriptions(Map<String, DescriptionUDT> descriptions) {
			this.descriptions = descriptions;
		}
	}
	
	public static class MockDOWithUDTSingle extends AbstractDO {
		
		@Field(name="description")
		DescriptionUDT description;

		/**
		 * @return the description
		 */
		public DescriptionUDT getDescription() {
			return description;
		}

		/**
		 * @param description the description to set
		 */
		public void setDescription(DescriptionUDT description) {
			this.description = description;
		}
	}
	
	
	// The User Defined Type
	public static class DescriptionUDT {
		
		@Field(name="name")
		private String name;
		
		@Field(name="time")
		private Date time;

		/**
		 * @return the name
		 */
		public String getName() {
			return name;
		}

		/**
		 * @param name the name to set
		 */
		public void setName(String name) {
			if (name == null) throw new IllegalArgumentException("name cannot be empty");
			this.name = name;
		}

		/**
		 * @return the time
		 */
		public Date getTime() {
			return time;
		}

		/**
		 * @param time the time to set
		 */
		public void setTime(Date time) {
			if (time == null) throw new IllegalArgumentException("time cannot be empty");
			this.time = time;
		}
		
		//////////////////////////////////////////////
		// REQUIRED FOR UDT THAT IS IN A SET
		// SMART TO HAVE REGARDLESS....
		//////////////////////////////////////////////
		public int hashCode() {
			return (this.time.hashCode() * 37) + (this.name.hashCode() * 37);
		}
		
		public boolean equals(Object o) {
			if (o instanceof DescriptionUDT) {
				DescriptionUDT other = (DescriptionUDT)o;
				if (other.time.equals(this.time) &&
						other.name.equals(this.name)) {
					return true;
				}
			}
			return false;
		}
	}
}