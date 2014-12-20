package edu.purdue.rcac.wikiway.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.ListResult;
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
	public WikiGraph graph;
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

	public ArrayList makeTxt(String pageName, int id) {
		try {
			final GcsService gcsService = GcsServiceFactory
					.createGcsService(RetryParams.getDefaultInstance());
			status = "Downloading Data from Wikipedia";
			String outputName = "talkoutput" + ".txt";
			System.out.println(status + " pageName " + pageName);
			ArrayList output = new ArrayList();
			//ListResult bucketList = new list(WikiGraph.BUCKET_NAME, );
			pageName.replaceAll(" ", "");
			//GcsFilename deletion = new GcsFilename("processbucket" ,outputName);
			//gcsService.delete(deletion);
			ResultXML xml = new ResultXML(pageName, gcsService);
			
			TalkProcessor tp = new TalkProcessor();
			tp.setOutputFile(outputName);
			tp.process(xml.getOutput(), gcsService);
			
			gcsService.delete(xml.getOutput());
			status = "Compiling Graph";
			System.out.println(status);
			this.graph = new WikiGraph(gcsService, tp.getOutputFile(), pageName, id);
			//gcsService.delete(tp.getOutputFile());
			status = "Preparing Analytics";
			System.out.println(status);
			output.add(graph.getOutputLocation()[0]);
			output.add(graph.getOutputLocation()[1]);
			output.add(graph.topUsers);
			output.add(tp.firstRevision);
			output.add(this.graph.nodes);
			output.add(this.graph.numEdits);
			gcsService.delete(tp.getOutputFile());
			
			return output;
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
	
	public int[] data()	{
		int[] data = new int[0];
		
		return data;
	}
	
	public boolean delete(int id)	{
		try {
			final GcsService gcsService = GcsServiceFactory
					.createGcsService(RetryParams.getDefaultInstance());
			GcsFilename curr = new GcsFilename("graphbucket", "" + id +"/");
			gcsService.delete(curr);
			return true;
		} catch (Exception e)	{
			e.printStackTrace();
			return false;
		}
	}

	public String getStatus() {
		// TODO Auto-generated method stub
		return status;
	}
}
