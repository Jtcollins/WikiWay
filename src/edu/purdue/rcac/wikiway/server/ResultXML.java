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
	
	public static final String BUCKET_NAME = "xmlbucket";

	public URL exportPage;
	public String currPages;
	public int totalComments;
	public GcsFilename xName;
	public File tXt;
	public String workingDir;
	public String xMLName;
	public String outputName;
	private String outputLocation;
	
	public GcsFilename getOutput()	{
		return xName;
	}

	public ResultXML(String pageNames, GcsService service) {
		this(pageNames, service, true, "xmlout");
	}

	/**
	 * Creates an XML of info requested from wikimedia API
	 * @param pageNames Name of the talkpage we are retrieving
	 * @param service The GCSService that this code is to communicate with.
	 * @param history, if you want complete history
	 * @param fileName output filename
	 */
	public ResultXML(String pageNames, GcsService service, boolean history, String fileName) {
		this.xMLName = fileName + ".xml";
		currPages = pageNames.replaceAll(" ", "_");
		try {


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
			System.out.println("Exportpage: " + exportPage + " xmlName: " + xMLName);
			this.xName = new GcsFilename(BUCKET_NAME, xMLName);
	
			
			//Declares the GCS service
			final GcsService gcsService = GcsServiceFactory
					.createGcsService(RetryParams.getDefaultInstance());
			
			Builder optionsBuild = new GcsFileOptions.Builder().acl("public-read");
			GcsFileOptions options = optionsBuild.build();
			
			
			GcsOutputChannel outputChannel = gcsService.createOrReplace(this.xName,
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
			oout.flush();
			oout.close();
			System.out.println("Download Complete "+ this.xName.getObjectName());

			//Date date = new Date();
			//DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
			
			
			
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
