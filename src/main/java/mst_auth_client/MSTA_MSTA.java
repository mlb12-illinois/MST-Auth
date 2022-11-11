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
import mst_auth_library.MST_Auth_Servlet;
import mst_auth_library.MST_Auth_Microservice;

public class MSTA_MSTA extends MST_Auth_Microservice {
	MSTA_MSTA_Servlet MyServlet;
	public MSTA_MSTA() {
	}

	public void SetServlet(MSTA_MSTA_Servlet myservlet) {
		MyServlet = myservlet;
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response, String trustedbody) throws IOException, MSTAException  {
		//System.out.println("MSTA Server doPost");
		String input;
		if (trustedbody == null)
			input = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
		else input = trustedbody;
		
		//System.out.println("OY0c " + input);
		MyServlet.MSTALog(input);
		
	    //System.out.println(input);
		//System.out.println("Server Exit doPost");
		response.getWriter().append(input);
	}
}
