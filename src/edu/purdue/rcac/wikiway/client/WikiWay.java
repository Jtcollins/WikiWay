package edu.purdue.rcac.wikiway.client;

import java.util.ArrayList;

import edu.purdue.rcac.wikiway.server.*;
import edu.purdue.rcac.wikiway.shared.FieldVerifier;



//import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class WikiWay implements EntryPoint {
	
	public ListBox lb = new ListBox();
	public String[] outputLocation;
	//public File outText;
	
	/**
	 * The message displayed to the user when the server cannot be reached or
	 * returns an error.
	 */
	private static final String SERVER_ERROR = "An error occurred while "
			+ "attempting to contact the server. Please check your network "
			+ "connection and try again.";

	/**
	 * Create a remote service proxy to talk to the server-side Greeting service.
	 */
	private final GreetingServiceAsync greetingService = GWT
			.create(GreetingService.class);

	/**
	 * This is the entry point method.
	 */
	public void onModuleLoad() {
		final Button searchButton = new Button("Search");
		final Button compileButton = new Button("Compile");
		final Button downloadButton = new Button("Download");
		final TextBox searchField = new TextBox();
		final TextBox status = new TextBox();
		final TextBox nodes = new TextBox();
		final TextBox topC = new TextBox();
		status.setVisible(false);
		searchField.setText("Search Parameters");
		final Label errorLabel = new Label();

		// We can add style names to widgets
		searchButton.addStyleName("searchButton");

		// Add the nameField and sendButton to the RootPanel
		// Use RootPanel.get() to get the entire body element
		RootPanel.get("searchFieldContainer").add(searchField);
		RootPanel.get("searchButtonContainer").add(searchButton);
		RootPanel.get("errorLabelContainer").add(errorLabel);

		// Focus the cursor on the name field when the app loads
		searchField.setFocus(true);
		searchField.selectAll();

		// Create the search dialog box
		final DialogBox dialogBox = new DialogBox();
		dialogBox.setText("Search Results");
		dialogBox.setAnimationEnabled(true);
		dialogBox.setGlassEnabled(true);
		final Button closeButton = new Button("Close");
		// We can set the id of a widget by accessing its Element
		closeButton.getElement().setId("closeButton");
		final Label textToServerLabel = new Label();
		final HTML serverResponseLabel = new HTML();
		VerticalPanel dialogVPanel = new VerticalPanel();
		dialogVPanel.addStyleName("dialogVPanel");
		//dialogVPanel.add(new HTML("<br><b>Results:</b>"));
		lb.setVisibleItemCount(10);
		lb.setSize("290px", "210px");
		dialogVPanel.add(lb);
		dialogVPanel.setHorizontalAlignment(VerticalPanel.ALIGN_RIGHT);
		dialogVPanel.add(compileButton);
		downloadButton.setVisible(false);
		dialogVPanel.add(closeButton);
		dialogVPanel.setHorizontalAlignment(VerticalPanel.ALIGN_LEFT);
		dialogBox.setWidget(dialogVPanel);
		
		//Create the Progress dialog box
		final DialogBox progressBox = new DialogBox();
		VerticalPanel progressVPanel = new VerticalPanel();
		final Button cancelButton = new Button("Cancel");
		progressVPanel.add(status);
		progressVPanel.add(cancelButton);
		progressBox.setWidget(progressVPanel);
		
		
		//Create the Result Dialog box
		final DialogBox resultsBox = new DialogBox();
		VerticalPanel resultVPanel = new VerticalPanel();
		resultsBox.setText("Results");
		resultVPanel.add(downloadButton);
		nodes.setText("Nodes: ");
		topC.setText("Top Contributor: ");
		final Button resetButton = new Button("Close");
		resultVPanel.add(nodes);
		resultVPanel.add(topC);
		resultVPanel.add(resetButton);
		resultsBox.setWidget(resultVPanel);
		
		compileButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				int select = lb.getSelectedIndex();
				String selected = lb.getValue(select);
				compileButton.setEnabled(false);
				status.setText("Compiling... This may take a few minutes.");
				dialogBox.hide();
				progressBox.setVisible(true);
				progressBox.center();
				status.setVisible(true);
				greetingService.makeTxt(selected, new AsyncCallback<String[]>()	{

					public void onFailure(Throwable caught) {
						// TODO Auto-generated method stub
						
					}

					public void onSuccess(String[] output) {
						// TODO Auto-generated method stub
						progressBox.hide();
						resultsBox.center();
						compileButton.setVisible(false);
						outputLocation = output;
						downloadButton.setVisible(true);
						downloadButton.setFocus(true);
						topC.setVisible(true);
						
					}

					
				});
				
			}
		});

		// Add a handler to close the DialogBox
		closeButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				dialogBox.hide();
				progressBox.setVisible(true);
				searchButton.setEnabled(true);
				downloadButton.setVisible(false);
				cancelButton.setFocus(true);
			}
		});
		
		resetButton.addClickHandler(new ClickHandler() {
			public void onClick(ClickEvent event) {
				resultsBox.hide();
				searchButton.setEnabled(true);
				downloadButton.setVisible(false);
				compileButton.setEnabled(true);
				compileButton.setVisible(true);
				searchButton.setFocus(true);
				searchField.setText("Search Parameters");
			}
		});

		// Create a handler for the sendButton and nameField
		class MyHandler implements ClickHandler, KeyUpHandler {
			/**
			 * Fired when the user clicks on the sendButton.
			 */
			public void onClick(ClickEvent event) {
				sendNameToServer();
			}

			/**
			 * Fired when the user types in the nameField.
			 */
			public void onKeyUp(KeyUpEvent event) {
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					sendNameToServer();
				}
			}

			/**
			 * Send the name from the nameField to the server and wait for a response.
			 */
			private void sendNameToServer() {
				// First, we validate the input.
				errorLabel.setText("");
				String textToServer = searchField.getText();
				if (!FieldVerifier.isValidName(textToServer)) {
					errorLabel.setText("Please enter at least four characters");
					return;
				}

				// Then, we send the input to the server.
				searchButton.setEnabled(false);
				textToServerLabel.setText(textToServer);
				serverResponseLabel.setText("");
				greetingService.greetServer(textToServer,
						new AsyncCallback<String>() {
							public void onFailure(Throwable caught) {
								// Show the RPC error message to the user
								dialogBox
										.setText("Remote Procedure Call - Failure");
								serverResponseLabel
										.addStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(SERVER_ERROR);
								dialogBox.center();
								closeButton.setFocus(true);
							}

							public void onSuccess(String result) {
								dialogBox.setText("Search Results");
								serverResponseLabel
										.removeStyleName("serverResponseLabelError");
								serverResponseLabel.setHTML(result);
								
								greetingService.getResults(new AsyncCallback<ArrayList>()	{

									@Override
									public void onFailure(Throwable caught) {
										// Auto-generated method stub
										
									}

									@Override
									public void onSuccess(ArrayList result) {
										// Auto-generated method stub
										lb.clear();
										for(int i = 0; i < result.size(); i++)	{
											lb.addItem((String) result.get(i));
										}
									}
									
								});
								dialogBox.center();
								compileButton.setFocus(true);
							}
						});
			}
		}
		
		class CompHandler implements ClickHandler, KeyUpHandler {

			public void onKeyUp(KeyUpEvent event) {
				// TODO Auto-generated method stub
				
			}

			public void onClick(ClickEvent event) {
				// TODO Auto-generated method stub
				
			}
			
		}
		
		class DownHandler implements ClickHandler, KeyUpHandler {

			public void onKeyUp(KeyUpEvent event) {
				// TODO Auto-generated method stub
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					Window.open("http://storage.googleapis.com/" +outputLocation[0] + "/" + outputLocation[1], "_self", "enabled");
				}
			}

			public void onClick(ClickEvent event) {
				//links to the final completed graph.
				Window.open("http://storage.googleapis.com/" +outputLocation[0] + "/" + outputLocation[1], "_self", "enabled");
				
			}
			
		}

		// Add a handler to send the name to the server
		MyHandler handler = new MyHandler();
		CompHandler comphandle = new CompHandler();
		DownHandler downhandle = new DownHandler();
		searchButton.addClickHandler(handler);
		
		compileButton.addClickHandler(comphandle);
		downloadButton.addClickHandler(downhandle);
		searchField.addKeyUpHandler(handler);
	}
}
