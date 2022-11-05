package mst_auth_client;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;

import mst_auth_library.MSTAException;
import mst_auth_library.MST_Auth_BaseClientWrapper;
import mst_auth_library.MST_Auth_BaseServlet;
import mst_auth_library.MST_Auth_ClientWrapper;
import mst_auth_library.MST_Auth_ServerClientWrapper;
import mst_auth_library.MST_Auth_Servlet;

public class MST_Auth_Client {
	private MST_Auth_BaseClientWrapper msta_library;
	public MST_Auth_Client() {
	}
	public void SetLibrary (MST_Auth_BaseClientWrapper MSTALibrary ) {
		msta_library = MSTALibrary;			
	}
	public void doGet(HttpServletRequest request, HttpServletResponse response, String trustedbody) throws IOException, MSTAException  {
	    response.getWriter().append("doGet").append(request.getContextPath());
	}
	public void doPost(HttpServletRequest request, HttpServletResponse response, String trustedbody) throws IOException, MSTAException  {
		//System.out.println("Server doPost");
		String input;
		if (trustedbody == null)
			input = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
		else input = trustedbody;
		
		//System.out.println("OY0c " + input);
		msta_library.Audit(input);
		
	    //System.out.println(input);
		//System.out.println("Server Exit doPost");
		response.getWriter().append(input);
	}
	public void doPut(HttpServletRequest request, HttpServletResponse response, String trustedbody) throws IOException, MSTAException  {
	    response.getWriter().append("doPut Served at: ").append(request.getContextPath());
	}
	public void doDelete(HttpServletRequest request, HttpServletResponse response, String trustedbody) throws IOException, MSTAException  {
	    response.getWriter().append("doDelete Served at: ").append(request.getContextPath());
	}
	public void callbackResponse(HttpResponse<String> parmmstresponse) {
		
	}
}
