package edu.purdue.rcac.wikiway.server;

import java.io.File;
import java.io.IOException;
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
		try {
			final GcsService gcsService = GcsServiceFactory
					.createGcsService(RetryParams.getDefaultInstance());
			status = "Downloading Data from Wikipedia";
			String outputName = "talkoutput" + ".txt";
			System.out.println(status + " pageName " + pageName);
			//GcsFilename deletion = new GcsFilename("processbucket" ,outputName);
			//gcsService.delete(deletion);
			ResultXML xml = new ResultXML(pageName, gcsService);
			TalkProcessor tp = new TalkProcessor();
			tp.setOutputFile(outputName);
			tp.process(xml.getOutput(), gcsService);
			gcsService.delete(xml.getOutput());
			status = "Compiling Graph";
			System.out.println(status);
			WikiGraph graph = new WikiGraph(gcsService, tp.getOutputFile());
			//gcsService.delete(tp.getOutputFile());
			status = "Preparing Analytics";
			System.out.println(status);
			return graph.getOutputLocation();
			//return status;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("makeTxt failed");
		return null;
	}

	public String getStatus() {
		// TODO Auto-generated method stub
		return status;
	}
}
