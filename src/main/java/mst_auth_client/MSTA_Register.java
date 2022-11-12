package mst_auth_client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.server.ServerEndpoint;
//import javax.websocket.Session;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;

@ServerEndpoint("/Register")
public class MSTA_Register {

	private static final  int CASSANDRA = 1;	// set to 0 to disable cassandra
	public static Cluster CASSANDRA_CLUSTER = null;
	private com.datastax.driver.core.Session CASSANDRA_SESSION = null;
    private static String CASSANDRA_URL = "127.0.0.1";
	private static Integer CASSANDRA_PORT = 9042;
	private javax.websocket.Session mysession;
	private LinkedHashMap<String, JSONObject> graphid_to_config = null;
	private LinkedHashMap<String, String> graphid_to_secret = null;
		
	//private static String CASSANDRA_AUTH = "";
	//private static String CASSANDRA_USER = ""; 
	//private static String CASSANDRA_PASSWORD = "";

	@OnOpen
	public void onOpen(javax.websocket.Session session){
	    System.out.println("Open Connection ...");
	    mysession = session;
		CassandraCreate();
		
		try {
			
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();     
		    String rootPath = classLoader.getResource("").getPath();
		    //System.out.println("graphtree " + rootPath);
			InputStream stream = classLoader.getResourceAsStream("../../graphtree.json");
			if (stream == null) {
			    System.out.println("graphtree.json missing from WEB-INF folder");
		    	throw(new NullPointerException ("graphtree.json missing from WEB-INF folder"));		
			}
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			byte[] buffer = new byte[2048];
			for (int length; (length = stream.read(buffer)) != -1; ) {
			     result.write(buffer, 0, length);
			}
			graphid_to_config = new LinkedHashMap<String, JSONObject>();
			graphid_to_secret = new LinkedHashMap<String, String>();
			String strproperties = result.toString("UTF-8");
		    //System.out.println(strproperties);
			JSONArray r1sconifg =  new JSONArray(strproperties);
		    //System.out.println(strproperties + " length " + r1sconifg.length());
			//JSONArray r2sconifg =  r1sconifg.getJSONArray("microservice");
		    for (int i = 0; i < r1sconifg.length(); i++) { 
		    	 JSONObject graphauths = r1sconifg.getJSONObject(i); 
		    	 JSONObject thegraph = graphauths.getJSONObject("microservice");
				 //System.out.println(thegraph.toString());
		    		
		    	 String MyMicroserviceID = thegraph.getString("MyMicroserviceID");
		    	 graphid_to_config.put(MyMicroserviceID, thegraph);
		    	 String MyHash = thegraph.getString("MyHash");
		    	 graphid_to_secret.put(MyMicroserviceID, MyHash);
		    }

		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	@OnClose
	public void onClose(){
	    System.out.println("Close Connection ...");
		if ( CASSANDRA == 1 ) {
			CASSANDRA_CLUSTER.close();	// not sure this does anything	
		}
	}	

	@OnMessage
	public String onMessage(String message){
		String ret = null;
		JSONObject r2sconifg =  new JSONObject(message);
		String Type = r2sconifg.getString("Type");
		String Record = r2sconifg.getString("Record");
		if (Type.equals("Log")) {
			ret = MSTALog(message);			
		}
		else if (Type.equals("Handshake")) {
			ret = graphid_to_secret.get(Record);		
		}
		else if (Type.equals("Config")) {
			ret = graphid_to_config.get(Record).toString();					
		}
		
		
	    //return message; for now null, can use this or SendMessage
		//SendMessage(ret);
		return (ret);
	}
	
	public void SendMessage(String message){
		try {
			mysession.getBasicRemote().sendText(message);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public Future<Void>  SendMessageA(String message){
		try {
			// Future<Void> socketwait = SendMessageA(String message);
			// socketwait.isDone();	//blocks
			return mysession.getAsyncRemote().sendText(message);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@OnError
	public void onError(Throwable e){
	    e.printStackTrace();
	}	

	public String MSTALog(String input) {
		String jsonquery = "INSERT INTO mstauth.service_tree JSON '" + input +"'";
		CassandraInsert(jsonquery);
		
		// $$$$$
		// do what we need to do
		
		return "OK";
	}

	public void CassandraInsert(String statement) {
		if ( CASSANDRA == 1 ) {
			Statement  st = new SimpleStatement(statement);
			if (CASSANDRA_CLUSTER == null || CASSANDRA_CLUSTER.isClosed()) CassandraCreate();		
			CASSANDRA_SESSION.executeAsync(st);
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

}
