package edu.purdue.rcac.wikiway.client;

import java.util.ArrayList;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

/**
 * The client-side stub for the RPC service.
 */
@RemoteServiceRelativePath("greet")
public interface GreetingService extends RemoteService {
	String greetServer(String name);
	ArrayList getResults();
	ArrayList makeTxt(String pageName, int id);
	int[] data();
	boolean delete(int id);
}

