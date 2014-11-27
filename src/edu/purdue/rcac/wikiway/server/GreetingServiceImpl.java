package edu.purdue.rcac.wikiway.server;

import java.io.File;
import java.util.ArrayList;


import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import edu.purdue.rcac.wikiway.client.GreetingService;
import edu.purdue.rcac.wikiway.shared.FieldVerifier;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;

/**
 * The server-side implementation of the RPC service.
 */
@SuppressWarnings("serial")
public class GreetingServiceImpl extends RemoteServiceServlet implements
		GreetingService {
	boolean fullHist;
	String pageSelected;
	String outputLoc;
	String status;
	public ArrayList<String> searchResults;
	
	public ArrayList<String> getResults()	{
		return searchResults;
	}

	public String greetServer(String input) {
		// Verify that the input is valid. 
		//ResultXML processor = new ResultXML(pageSelected, fullHist, pageSelected + "_" + dateFormat.format(date));
		
		System.out.println("About to search");
		WikiSearch newSearch = new WikiSearch(input);
		searchResults = newSearch.resultArr;
		
		String serverInfo = getServletContext().getServerInfo();
		String userAgent = getThreadLocalRequest().getHeader("User-Agent");

		// Escape data from the client to avoid cross-site script vulnerabilities.
		input = escapeHtml(input);
		userAgent = escapeHtml(userAgent);
		
		System.out.println("Returning Search");
		return searchResults.toString();
	}

	/**
	 * Escape an html string. Escaping data received from the client helps to
	 * prevent cross-site script vulnerabilities.
	 * 
	 * @param html the html string to escape
	 * @return the escaped string
	 */
	private String escapeHtml(String html) {
		if (html == null) {
			return null;
		}
		return html.replaceAll("&", "&amp;").replaceAll("<", "&lt;")
				.replaceAll(">", "&gt;");
	}

	public String makeTxt(String pageName) {
		final GcsService gcsService = GcsServiceFactory
				.createGcsService(RetryParams.getDefaultInstance());
		status = "Downloading Data from Wikipedia";
		ResultXML xml = new ResultXML(pageName, gcsService);
		status = "Compiling Graph";
		WikiGraph graph = new WikiGraph(gcsService, xml.getOutputLoc());
		status = "Preparing Analytics";
		//return graph.getOutputLocation();
		return status;
	}

	public String getStatus() {
		// TODO Auto-generated method stub
		return status;
	}
}
