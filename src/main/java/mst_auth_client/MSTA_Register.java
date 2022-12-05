package mst_auth_client;
import mst_auth_library.MST_Auth_Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.datastax.driver.core.Statement;

@ServerEndpoint("/Register")
public class MSTA_Register {

	private static final  int CASSANDRA = 1;	// set to 0 to disable cassandra
	public static Cluster CASSANDRA_CLUSTER = null;
	private static com.datastax.driver.core.Session CASSANDRA_SESSION = null;
    private static String CASSANDRA_URL = "127.0.0.1";
	private static Integer CASSANDRA_PORT = 9042;
	private static LinkedHashMap<String, JSONObject> deploy_to_config = null;
	private static LinkedHashMap<String, String> deploy_to_secret = null;
	private static LinkedHashMap<String, JSONObject> graphid_to_config = null;
		
	//private static String CASSANDRA_AUTH = "";
	//private static String CASSANDRA_USER = ""; 
	//private static String CASSANDRA_PASSWORD = "";
	
	private javax.websocket.Session mysession;

	@OnOpen
	public void onOpen(javax.websocket.Session session){
	    //System.out.println("Open Connection ...");
	    mysession = session;
		
		try {			
		    //System.out.println("CassandraCreate ...");
		    if (CASSANDRA_CLUSTER == null)
		    	CassandraCreate();
			
		
		    //System.out.println("Build Graph ...");
			if (deploy_to_config == null) {
				ClassLoader classLoader = Thread.currentThread().getContextClassLoader();     
			    String rootPath = classLoader.getResource("").getPath();
			    //System.out.println("graphtree " + rootPath);
				InputStream stream = classLoader.getResourceAsStream("../../Application.json");
				if (stream == null) {
				    System.out.println("Application.json missing from WEB-INF folder");
			    	throw(new NullPointerException ("Application.json missing from WEB-INF folder"));		
				}
				ByteArrayOutputStream result = new ByteArrayOutputStream();
				byte[] buffer = new byte[2048];
				for (int length; (length = stream.read(buffer)) != -1; ) {
				     result.write(buffer, 0, length);
				}
				graphid_to_config = new LinkedHashMap<String, JSONObject>();
				deploy_to_config = new LinkedHashMap<String, JSONObject>();
				deploy_to_secret = new LinkedHashMap<String, String>();
				String strproperties = result.toString("UTF-8");
				JSONArray r1sconifg =  new JSONArray(strproperties);
			    for (int i = 0; i < r1sconifg.length(); i++) { 
			    	 JSONObject graphauths = r1sconifg.getJSONObject(i); 
			    	 JSONObject thegraph = graphauths.getJSONObject("microservice");
					 //System.out.println(thegraph.toString());
			    		
			    	 String MyMicroserviceID = thegraph.getString("deploymentKey");
			    	 deploy_to_config.put(MyMicroserviceID, thegraph);
			    	 String MyHash = thegraph.getString("keystorePassword");
			    	 deploy_to_secret.put(MyMicroserviceID, MyHash);
			    	 MyMicroserviceID = thegraph.getString("microserviceId");
			    	 graphid_to_config.put(MyMicroserviceID, thegraph);
			    }
		    }

		} catch (IOException e) {
			e.printStackTrace();
		}

	    //System.out.println("Leaving ...");
	}
	
	@OnClose
	public void onClose(){
	    //System.out.println("Close Connection ...");
		if ( CASSANDRA == 1 ) {
			CASSANDRA_CLUSTER.close();	// not sure this does anything	
		}
	}	

	@OnMessage
	public String onMessage(String message){
		//System.out.println("MSTA_Register: " + message);
		String ret = null;
		JSONObject r2sconifg =  new JSONObject(message);
		if(r2sconifg != null && r2sconifg.has("event")) {
			// event
			JSONObject eventobj =  new JSONObject(r2sconifg.getString("event"));
			String validated = null;
			// check message
			validated = checkmsg(eventobj);
			if (validated.equals("Y")) {
				// check authorization
				if(eventobj.has("receiving_instanceid")) validated = validatereceiving(eventobj);
				else validated = validatesending(eventobj);
			}
			// check microservice chain
			if (validated.equals("Y")) validated = checkchain(eventobj);
			
			eventobj.put("validated", validated);
			
			if (validated.equals("N"))
			    System.out.println("Invalid: " + eventobj);
			
			ret = MSTALog(eventobj.toString());			
		    //System.out.println(ret);
		}
		else if(r2sconifg != null && r2sconifg.has("apiKey")) {
		    //System.out.println("apiKey: " + r2sconifg.getString("apiKey"));
			ret = deploy_to_config.get(r2sconifg.getString("apiKey")).toString();		
		    //System.out.println("ret: " + ret);
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
	// /////////////////////////////////////////////////
	// 
	// Anomoly detection here
	//
	// ////////////////////////////////////////////////
	public String validatesending(JSONObject eventobj)
	{
		String validated = "N";
		String inbound_method = null;	// used for "FORWARD"
		String outbound_method = null;	// just needed
		String receiving_servicename = null;	// used to get authorizations
		String sending_instanceid = null;		// used to get authorizations
		if (eventobj.has("inbound_method")) inbound_method = eventobj.getString("inbound_method");
		else return validated;
		if (eventobj.has("outbound_method")) outbound_method = eventobj.getString("outbound_method");
		else return validated;
		if (eventobj.has("receiving_servicename")) receiving_servicename = eventobj.getString("receiving_servicename");
		else return validated;
		if (eventobj.has("sending_instanceid")) sending_instanceid = eventobj.getString("sending_instanceid");
		else return validated;
		// get the authorization list
		JSONObject ConfigObj =  graphid_to_config.get(sending_instanceid);		
		if (ConfigObj == null) return validated;
		JSONArray AuthList = ConfigObj.getJSONArray("authorizationList");
		JSONObject GraphObject = null;
	    for (int i = 0; i < AuthList.length(); i++) { 
	    	JSONObject tempobject = AuthList.getJSONObject(i);  
	    	 String graphname = tempobject.getString("microserviceName");
	    	 if (graphname.equals(receiving_servicename)) {		// sending so get list for receiving service
	    		 GraphObject = tempobject;
	    		 break;
	    	 }
	    }
	    if (GraphObject == null) return validated;
	    
		if ((MST_Auth_Utils.CheckAuthorization(GraphObject, "SEND", inbound_method, outbound_method) == 1)) {
			validated = "Y";
		}
		return validated;
	}
	public String validatereceiving(JSONObject eventobj)
	{
		String validated = "N";
		String inbound_method = null;	// just needed
		String sending_servicename = null;	// used to get authorizations
		String receiving_instanceid = null;	// used to get authorizations
		if (eventobj.has("inbound_method")) inbound_method = eventobj.getString("inbound_method");
		else return validated;
		if (eventobj.has("sending_servicename")) sending_servicename = eventobj.getString("sending_servicename");
		else return validated;
		if (eventobj.has("receiving_instanceid")) receiving_instanceid = eventobj.getString("receiving_instanceid");
		else return validated;
		// get the authorization list
		JSONObject ConfigObj =  graphid_to_config.get(receiving_instanceid);		
		if (ConfigObj == null) return validated;
		JSONArray AuthList = ConfigObj.getJSONArray("authorizationList");
		JSONObject GraphObject = null;
	    for (int i = 0; i < AuthList.length(); i++) { 
	    	JSONObject tempobject = AuthList.getJSONObject(i);  
	    	 String graphname = tempobject.getString("microserviceName");
	    	 if (graphname.equals(sending_servicename)) {		// receiving so get list for sending service
	    		 GraphObject = tempobject;
	    		 break;
	    	 }
	    }
	    if (GraphObject == null) return validated;
	    
		if ((MST_Auth_Utils.CheckAuthorization(GraphObject, "RECEIVE", inbound_method, inbound_method) == 1)) {
			validated = "Y";
		}
		return validated;
	}
	// now looking back
	public String checkmsg(JSONObject eventobj)
	{
		String validated = "Y";
		int reason_code = 0;	
		if (eventobj.has("reason_code")) reason_code = eventobj.getInt("reason_code");
		else return "N";
		if (reason_code != 200 ) return "N";
		
		String[] fields = {"root_msgid", "msgid", "send_timestamp", "parent_msgid", "sending_instanceid", "sending_serviceid", "sending_servicename", "receiving_serviceid", "receiving_servicename", "body_hash"};
		
		String msgid = null;	
		if (eventobj.has("msgid")) msgid = eventobj.getString("msgid");
		else return "N";

		String stquery = "SELECT JSON * FROM mstauth.service_tree WHERE ";
		stquery += " msgid = ";
		stquery += msgid;
		Statement  st = new SimpleStatement(stquery);
		JSONObject jsonobj = null;
		if ( CASSANDRA == 1 ) {
			if (CASSANDRA_CLUSTER == null || CASSANDRA_CLUSTER.isClosed()) CassandraCreate();		
			ResultSet resultSet = CASSANDRA_SESSION.execute(st);
		    List<Row> all = resultSet.all();
		    for (int i = 0; i < all.size(); i++)
		    {
		    	String jsonstr = all.get(i).getString("[json]");
		    	jsonobj =  new JSONObject(jsonstr);
			    for (int y = 0; y < fields.length; y++)
			    {
					if (!eventobj.has(fields[y])) return "N";			    	
					if (!jsonobj.has(fields[y])) return "N";
					if (!(jsonobj.getString(fields[y]).equals(eventobj.getString(fields[y])))) return "N";
			    }
		    }
		}
		
		return validated;
	}
	public String checkchain(JSONObject eventobj)
	{
		String validated = "Y";
		// if anything is missing then invalid
		String msgid = null;	
		String parent_msgid = null;	
		String root_msgid = null;	
		if (eventobj.has("msgid")) msgid = eventobj.getString("msgid");
		else return "N";
		if (eventobj.has("parent_msgid")) parent_msgid = eventobj.getString("parent_msgid");
		else return "N";
		if (eventobj.has("root_msgid")) root_msgid = eventobj.getString("root_msgid");
		else return "N";
		
		// iteratively look back through the gain and see if valid
		// the reason we have to do it this way (and not just check everything with this root)
		// is if this is a large service chain,
		// some of the messages don't have to relate to me
		// but if we just march back through parents, should be good
		String stquery = "SELECT JSON * FROM mstauth.service_tree WHERE ";
		stquery += " root_msgid = ";
		stquery += root_msgid;
		stquery += " and msgid = ";
		stquery += parent_msgid;
		Statement  st = new SimpleStatement(stquery);
	    //st.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
		JSONObject jsonobj = null;
		if ( CASSANDRA == 1 ) {
			if (CASSANDRA_CLUSTER == null || CASSANDRA_CLUSTER.isClosed()) CassandraCreate();		
			ResultSet resultSet = CASSANDRA_SESSION.execute(st);
		    List<Row> all = resultSet.all();
			String tempval = "Y";
		    for (int i = 0; i < all.size(); i++)
		    {
		    	String jsonstr = all.get(i).getString("[json]");
		    	jsonobj =  new JSONObject(jsonstr);
		    	if (jsonobj.getString("validated").equals("N")) tempval = "N";
		    }
		    if (all.size() > 0)
		    	validated = tempval;
		    else {
		    	// it is possible it is missing if this is asynch.
		    	// for now, the asynch will check when it gets here
		    	//
		    }
		}
		
		// if I am root, then all good
		if (msgid.equals(root_msgid)) return validated;
		
    	return checkchain(jsonobj);
		
	}
	
	@OnError
	public void onError(Throwable e){
	    e.printStackTrace();
	}	

	public String MSTALog(String input) {
		String jsonquery = "INSERT INTO mstauth.service_tree JSON '" + input +"'";
	    //System.out.println(jsonquery);
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
					  //System.out.println("MST-Auth" + e.toString());
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
