package edu.purdue.rcac.wikiway.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
//import java.text.DateFormat;
//import java.text.SimpleDateFormat;
import java.util.Arrays;
//import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFileOptions.Builder;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;

public class ResultXML {

	public URL exportPage;
	public String currPages;
	public int totalComments;
	public GcsFilename xName;
	public File tXt;
	public String workingDir;
	public String xMLName;
	public String outputName;
	private String outputLocation;
	
	public String getOutputLoc()	{
		return outputLocation;
	}

	public ResultXML(String pageNames, GcsService service) {
		this(pageNames, service, true, "processorOutput");
	}

	/**
	 * Creates an XML of info requested from wikimedia API
	 * @param pageNames Name of the talkpage we are retrieving
	 * @param service The GCSService that this code is to communicate with.
	 * @param history, if you want complete history
	 * @param fileName output filename
	 */
	public ResultXML(String pageNames, GcsService service, boolean history, String fileName) {
		workingDir = System.getProperty("user.dir");
		this.xMLName = fileName + ".xml";
		this.outputName = fileName + ".txt";
		String finalFileXML = "";
		String finalFiletxt = "";
		currPages = pageNames.replaceAll(" ", "_");
		try {
			String your_os = System.getProperty("os.name").toLowerCase();
			if (your_os.indexOf("win") >= 0) {
				finalFileXML = workingDir + "\\" + this.xMLName;
				finalFiletxt = workingDir + "\\" + this.outputName;
			} else if (your_os.indexOf("nix") >= 0
					|| your_os.indexOf("nux") >= 0) {
				finalFileXML = workingDir + "/" + this.xMLName;
				finalFiletxt = workingDir + "/" + this.outputName;
			} else {
				finalFileXML = workingDir + "/" + this.xMLName;
				finalFiletxt = workingDir + "/" + this.outputName;
			}

			System.out.println("Final filepath : " + finalFileXML);

			if (history) {
				exportPage = new URL(
						"http://en.wikipedia.org/w/index.php?title=Special:Export&history&pages=Talk:"
								+ currPages);
			} else {
				exportPage = new URL(
						"http://en.wikipedia.org/w/index.php?title=Special:Export&pages=Talk:"
								+ currPages);
			}
			

			// Path path = Paths.get(finalFileXML);
			// outputXML = new File("/" + this.xMLName);

			//TODO: mend to have individual xml names
			GcsFilename xName = new GcsFilename("outputxml", "xmlout.xml");
	
			
			//Declares the GCS service
			final GcsService gcsService = GcsServiceFactory
					.createGcsService(RetryParams.getDefaultInstance());
			
			Builder optionsBuild = new GcsFileOptions.Builder().acl("public-read");
			GcsFileOptions options = optionsBuild.build();
			
			
			GcsOutputChannel outputChannel = gcsService.createOrReplace(xName,
					options);
			ObjectOutputStream oout = new ObjectOutputStream(
					Channels.newOutputStream(outputChannel));
			URLConnection connect = exportPage.openConnection();
			connect.setReadTimeout(0);
			InputStream is = connect.getInputStream();
			int nRead;
			byte[] data = new byte[16384];

			while ((nRead = is.read(data, 0, data.length)) != -1) {
				oout.write(data, 0, nRead);
			}
			//oout.flush();
			oout.close();
			System.out.println("Download Complete");

			//Date date = new Date();
			//DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
			
			TalkProcessor tp = new TalkProcessor();
			this.outputName = "talkoutput" + ".txt";
			tp.setOutputFile("talkoutput" + ".txt");
			tp.process(xName, gcsService);
			gcsService.delete(xName);
			outputLocation = tp.getOutputFile().getObjectName();
			
			/*
			GcsInputChannel readChannel = gcsService
					.openPrefetchingReadChannel(tp.getOutputFile(), 0,
							1024 * 1024);
			try (ObjectInputStream oin = new ObjectInputStream(
					Channels.newInputStream(readChannel))) {
				BufferedInputStream bin = new BufferedInputStream(
						(InputStream) oin);
				tXt = (File) bin.;
			}
			*/
			System.out.println("Parsing Complete :) " + outputLocation);

		} catch (Exception e) {
			// Auto-generated catch block
			e.printStackTrace();
		}

	}

	/*
	 * public static void main(String[] args) { ResultXML tem = new
	 * ResultXML("Purdue_University", false, "Purdue");
	 * 
	 * }
	 */
}
