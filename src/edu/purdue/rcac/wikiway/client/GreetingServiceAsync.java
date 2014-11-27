package edu.purdue.rcac.wikiway.client;

import java.util.ArrayList;

import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * The async counterpart of <code>GreetingService</code>.
 */
public interface GreetingServiceAsync {
	void greetServer(String input, AsyncCallback<String> callback)
			throws IllegalArgumentException;
	void getResults(AsyncCallback<ArrayList> asyncCallback)
			throws IllegalArgumentException;
	void makeTxt(String pageName, AsyncCallback<String> callback);
}	
