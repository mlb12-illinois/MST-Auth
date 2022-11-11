package mst_auth_client;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;

import java.util.concurrent.TimeUnit;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.json.JSONException;

import mst_auth_library.MST_Auth_ClientServlet;
import mst_auth_library.MST_Auth_Microservice;

public class MSTA_MSTA_Servlet extends MST_Auth_ClientServlet {
	private static final  int CASSANDRA = 1;	// set to 0 to disable cassandra
	public static Cluster CASSANDRA_CLUSTER = null;
	private Session CASSANDRA_SESSION = null;
    private static String CASSANDRA_URL = "127.0.0.1";
	private static Integer CASSANDRA_PORT = 9042;
	
	//private static String CASSANDRA_AUTH = "";
	//private static String CASSANDRA_USER = ""; 
	//private static String CASSANDRA_PASSWORD = ""; 

	public MST_Auth_Microservice  GetService () {	
		//System.out.println("MSTA Server GetService");
		MSTA_MSTA MSTA_msta = new MSTA_MSTA();// override this
		MSTA_msta.SetServlet(this);
		return MSTA_msta;			
	}

	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		CassandraCreate();
		//String input = "{\"sending_servicename\":\"MSTABusiness\",\"sending_serviceid\":\"237367ff-ed9b-4d9c-b636-ad0d28ac5f62\",\"create_timestamp\":\"2022-11-02 18:32:25.71\",\"sending_instanceid\":\"fe8a5797-1cd2-4083-82b6-3d3ba9e40a49\",\"root_msgid\":\"a42d8262-0d55-49f9-aef6-5ffc8bcd35df\",\"receiving_serviceid\":\"0534fe29-3dc4-4641-bc94-a939d0d8ba71\",\"msgid\":\"3f25c69a-1794-401f-a684-02532ba7439f\",\"receiving_servicename\":\"MSTADataProvider\",\"parent_msgid\":\"6628f71d-eb88-4d8e-9528-8f7b3350538a\"}";
		//String jsonquery = "INSERT INTO mstauth.service_tree JSON '" + input +"'";
		//CassandraInsert(jsonquery);

	}
	
	public void destroy() {
		super.destroy();
		if ( CASSANDRA == 1 ) {
			CASSANDRA_CLUSTER.close();	// not sure this does anything	
		}
	}
	
	public void CassandraInsert(String statement) {
		if ( CASSANDRA == 1 ) {
			Statement  st = new SimpleStatement(statement);
			if (CASSANDRA_CLUSTER == null || CASSANDRA_CLUSTER.isClosed()) CassandraCreate();		
			CASSANDRA_SESSION.execute(st);
		}
		
	}
	private void CassandraCreate() {
		if ( CASSANDRA == 1 ) {
			
			int tries = 3;
			while (tries > 0)
			{
				try {
					
					CASSANDRA_CLUSTER = Cluster.builder()
							.addContactPoint(CASSANDRA_URL)
							.withPort(CASSANDRA_PORT)
	//						.withAuthProvider(new SigV4AuthProvider(CASSANDRA_AUTH))
	//		                .withSSL()
	//						.withCredentials(CASSANDRA_USER, CASSANDRA_PASSWORD)
							.build();
	
					CASSANDRA_SESSION = CASSANDRA_CLUSTER.connect();
					CASSANDRA_SESSION.execute("USE mstauth");
					
					return;
				}
				catch(Exception e) {
					tries --;
					  System.out.println("MST-Auth" + e.toString());
					  if (tries > 0) {
						  try 
						  {
							  TimeUnit.MILLISECONDS.sleep(5000);	// add a little wait, to see if root will end
						  }
						  catch (JSONException | InterruptedException ie) 
						  {
							  //throw new MSTAException(MyMicroserviceName + ":" + MyMicroserviceID + ":" + MyInstanceID + ": MST-Auth Cassandra InterruptedException " + ie.toString());
						  }						  
					  }
				}
			}
		}
	}
	
	public void MSTALog(String input) {
		//System.out.println("OY2 " + input);
		String jsonquery = "INSERT INTO mstauth.service_tree JSON '" + input +"'";
		CassandraInsert(jsonquery);
	}
	
}
