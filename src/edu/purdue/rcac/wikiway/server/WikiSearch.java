package edu.purdue.rcac.wikiway.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;


public class WikiSearch {
	public String search;
	public URL wikiPage;
	public ArrayList<String> resultArr;

	
	/**
	 * Searches wikipedia. Creates an instance of that search with an ArrayList of result pages
	 * @param searchParam
	 */
	WikiSearch(String searchParam)	{
		//searchParam
		this.search = searchParam.replace(" ", "_");
		
		try {
			wikiPage = new URL("http://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srprop=&srsearch=" + search);
			BufferedReader contentURL = new BufferedReader(new InputStreamReader(wikiPage.openStream()));
			
			String curr;
			String rawString = "";
			//Scrape JSON returned by wikimedia API
			while ((curr = contentURL.readLine()) != null)	{
				rawString += curr;
			}
			
			//Read the specific Json supplied by Wiki
			//Note: If Wiki changes API, this may no longer function.
			JsonParser parse = new JsonParser();
			JsonElement elem = parse.parse(rawString);
			
			JsonObject searchResults = elem.getAsJsonObject();;
		    searchResults = (JsonObject) (searchResults.get("query"));
			JsonArray searchArray = (JsonArray) (searchResults.getAsJsonArray("search"));
			ArrayList<String> results = new ArrayList<String>();
			for(int i = 0; i < searchArray.size(); i++)	{
				results.add(((JsonElement) ((JsonObject) searchArray.get(i)).get("title")).getAsString());
			}
			resultArr = results;

			
		} catch (Exception e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/*
	public static void main(String[] args) {
		WikiSearch search = new WikiSearch("Purdue");
		System.out.println(search.resultArr.toString());
	}
	*/
	
}
