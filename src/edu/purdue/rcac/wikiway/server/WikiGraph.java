// WikiGraph.java
// This program takes the output of TalkProcessor.java and converts it to
// UCINET format


package edu.purdue.rcac.wikiway.server;

import edu.uci.ics.jung.graph.DirectedSparseGraph;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsFileOptions.Builder;


public class WikiGraph
{

    //private static char file_separator;
	public static final String BUCKET_NAME = "graphbucket";
    
	private GcsOutputChannel outputChannel;
	private ObjectOutputStream oout;
	private GcsService service;
	private GcsFilename srcFile;
	

	private String graphFile;
	//private String finalFile;
	//private String bucketName;
    
    public String getOutputLocation() {
		// TODO Auto-generated method stub
		return graphFile;
	}
	
	public void setGraphFile(String file) {
		graphFile = file;
	}

    public WikiGraph(GcsService gcsService, String sourceFile)	{
    	System.out.println("Graphing now");
    	service = gcsService;
    	this.srcFile = new GcsFilename(TalkProcessor.BUCKET_NAME, sourceFile);
    	
    	ArrayList<String> page_threads = new ArrayList<String>();
        ArrayList<String> archive_threads = new ArrayList<String>();
        ArrayList<String> archive_pages = new ArrayList<String>();

        BufferedReader in_file = null;

        String in_line;
        String current_thread = null;
        String in_thread;
        String current_page = null;
        String in_page;

        String[] line_fields;

        DirectedSparseGraph<String,Edge> thread_graph = new DirectedSparseGraph<String,Edge>();
        DirectedSparseGraph<String,Edge> page_graph = new DirectedSparseGraph<String,Edge>();
        DirectedSparseGraph<String,Edge> archive_graph = new DirectedSparseGraph<String,Edge>();

        Deque<InterventionAttributes> all_interventions = new LinkedList<InterventionAttributes>();
        
        ArrayList<InterventionAttributes> thread_interventions = new ArrayList<InterventionAttributes>();
        ArrayList<InterventionAttributes> page_interventions = new ArrayList<InterventionAttributes>();
        ArrayList<InterventionAttributes> archive_interventions = new ArrayList<InterventionAttributes>();

        InterventionAttributes intervention_attributes_temp;
        
        GcsInputChannel readChannel = gcsService.openPrefetchingReadChannel(
				srcFile, 0, 1024 * 1024);
        
        try (ObjectInputStream oin = new ObjectInputStream( Channels.newInputStream(readChannel))) {
        
        in_file = new BufferedReader(new InputStreamReader((InputStream) oin));
        
        } catch (Exception e)	{
        	
        }
        
	        try {
				while( (in_line = in_file.readLine()) != null) // read the verbose file into a Double-ended queue
				{
				    line_fields = in_line.split(",");
				    if(line_fields[5].contains("M"))
				    {
				        line_fields[5] = "000000000000";
				    }
				    all_interventions.addLast(new InterventionAttributes(line_fields[0],line_fields[1],line_fields[2],Integer.parseInt(line_fields[3]),Integer.parseInt(line_fields[4]),Long.valueOf(line_fields[5])));
				}
				in_file.close();
			} catch (NumberFormatException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
	        
	        
	        while(true)
	        {
	        	try{
		            if(all_interventions.isEmpty())
		            {
		                // write page_threads
		                writePageThread_File(page_threads,"page_threads",srcFile);
		                // write archive_pages
		                writePageThread_File(archive_pages, "archive_pages", srcFile);
		                // write archive_threads
		                writePageThread_File(archive_threads, "archive_threads", srcFile);
		
		                // prepare and write thread networks
		                thread_graph = buildGraph(thread_interventions);
		                writeUCINET_File(thread_graph, srcFile, current_thread, thread_interventions);
		
		                // prepare and write page networks
		                page_interventions.addAll(thread_interventions);
		                combineNetworks(page_graph,thread_graph);
		                writeUCINET_File(page_graph, srcFile, current_page, page_interventions);
		                
		                // prepare and write archive networks
		                archive_interventions.addAll(page_interventions);
		                combineNetworks(archive_graph,page_graph);
		                writeUCINET_File(archive_graph, srcFile, "archive", archive_interventions);
		
		                current_page = null;
		                current_thread = null;
		
		                break;
		            }
	        	} catch(Exception e)	{
	        		e.printStackTrace();
	        	}
	
	
	            
	            intervention_attributes_temp = all_interventions.pollFirst();
	
	            in_thread = intervention_attributes_temp.thread;
	            in_page = intervention_attributes_temp.page;
	            
	            if(current_page == null)
	            {
	                current_page = new String(intervention_attributes_temp.page);
	                archive_pages.add(new String(fileNameSanitize(in_page)));
	            }
	            if(current_thread == null)
	            {
	                current_thread = new String(intervention_attributes_temp.thread);
	                page_threads.add(new String(fileNameSanitize(in_thread)));
	            }
	            
	            if(in_page.compareTo(current_page) != 0)
	            {
	                try {
						writePageThread_File(page_threads, current_page, srcFile);
					} catch (IOException e) {
						e.printStackTrace();
					}
	                archive_threads.addAll(page_threads);
	                archive_pages.add(new String(fileNameSanitize(in_page)));
	                page_threads.clear();
	            }
	
	            if(in_thread.compareTo(current_thread)!= 0)
	            {
	                page_threads.add(fileNameSanitize(in_thread));      // add thread to page_threads
	                thread_graph = buildGraph(thread_interventions);    // build thread network
	                combineNetworks(page_graph, thread_graph);          // add thread network to page network
	                page_interventions.addAll(thread_interventions);    // add all thread interventions to page interventions
	
	                // write thread UCINET file
	                try {
						writeUCINET_File(thread_graph, srcFile, current_thread, thread_interventions);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	                
	                // clear thread interventions
	                thread_interventions.clear();
	
	                // add new intervention to cleared list and reset thread identifier
	                thread_interventions.add(new InterventionAttributes(intervention_attributes_temp));
	                current_thread = new String(in_thread);
	            }
	            else
	            {
	                // add intervention to thread interventions
	                thread_interventions.add(new InterventionAttributes(intervention_attributes_temp));
	            }
	
	            if(in_page.compareTo(current_page) != 0)
	            {
	                // prepare page file
	                try {
						writeUCINET_File(page_graph, srcFile , current_page ,page_interventions);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	
	                combineNetworks(archive_graph,page_graph);
	                page_graph = new DirectedSparseGraph<String,Edge>();
	
	                archive_interventions.addAll(page_interventions);
	                page_interventions.clear();
	
	                current_page = new String(in_page);
	            }
	        

        } 
        
    }
    
    private static class InterventionAttributes
    {
        public String page;
        public String thread;
        public String user;
        public Integer word_count;
        public Integer char_count;
        public Long date;

        public InterventionAttributes(String p, String t, String u, Integer wc, Integer cc, Long d)
        {
            page = p;
            thread = t;
            user = u;
            word_count = wc;
            char_count = cc;
            date = d;
        }

        public InterventionAttributes(InterventionAttributes ia)
        {
            page = new String(ia.page);
            thread = new String(ia.thread);
            user = new String(ia.user);
            word_count = new Integer(ia.word_count);
            char_count = new Integer(ia.char_count);
            date = new Long(ia.date);
        }
    }
    private static class UserAttributes
    {
        public String name;
        public int post_count;
        public int word_count;
        public int char_count;

        public UserAttributes(String new_name, int new_postcount, int new_wordcount, int new_charcount)
        {
            name = new_name;
            post_count = new_postcount;
            word_count = new_wordcount;
            char_count = new_charcount;
        }

        public void addAttributes(int new_postcount, int new_wordcount, int new_charcount)
        {
            post_count = post_count + new_postcount;
            word_count = word_count + new_wordcount;
            char_count = char_count + new_charcount;
        }

        public void addAttributes(UserAttributes ua)
        {
            post_count = post_count + ua.getPostCount();
            word_count = word_count + ua.getPostCount();
            char_count = char_count + ua.getCharCount();
        }

        public UserAttributes combineAttributes(UserAttributes ua)
        {
            return new UserAttributes(name,post_count+ua.getPostCount(),word_count+ua.getWordCount(),char_count + ua.getCharCount());
        }

        public int getPostCount()
        {
            return post_count;
        }

        public int getWordCount()
        {
            return word_count;
        }

        public int getCharCount()
        {
            return char_count;
        }

        public String getName()
        {
            return name;
        }
    }
    private static class Edge
    {
        public double weight;

        public Edge(double new_weight)
        {
            weight = new_weight;
        }

        public Edge()
        {
            weight = 0.0;
        }
    }
    public static String fileNameSanitize(String str)
    {
        String intermediate;

        if(str.length() > 70)
        {
            intermediate = str.substring(0, 69);
        }
        else
        {
            intermediate = str;
        }
        
        intermediate = intermediate.replace('\\','-');
        intermediate = intermediate.replace('/','-');
        intermediate = intermediate.replace('*','#');
        intermediate = intermediate.replace('<','_');
        intermediate = intermediate.replace('>','_');
        intermediate = intermediate.replace('|','_');
        intermediate = intermediate.replace('"',' ');
        intermediate = intermediate.replace('?', '7');
        intermediate = intermediate.replace(':','-');
        intermediate = intermediate.replace(' ','_');
        intermediate = intermediate.replace('\'','`');
        return intermediate;

    }
    private void writeUCINET_File(DirectedSparseGraph<String,Edge> graph, GcsFilename srcFile, String destFile, ArrayList<InterventionAttributes> interventions) throws IOException
    {
    	
        //BufferedWriter out_file = new BufferedWriter(new FileWriter(directory + fileNameSanitize(current_location) + ".txt"));
        Builder optionsBuild = new GcsFileOptions.Builder().acl("public-read");
		GcsFileOptions options = optionsBuild.build();
		
		GcsFilename outFile = new GcsFilename(BUCKET_NAME, destFile + ".txt");

		outputChannel = service.createOrReplace(outFile,
				options);

		oout = new ObjectOutputStream(
				Channels.newOutputStream(outputChannel));
		
		//BufferedReader bf = new BufferedReader(red);
		
        HashMap<String,UserAttributes> users = new HashMap<String,UserAttributes>();

        InterventionAttributes intervention_temp;
        UserAttributes user_temp;

        ArrayList<String> all_users = new ArrayList<String>();
        Iterator<String> all_users_iterate = null;

        for(int i=0;i<interventions.size();i++)
        {
            intervention_temp = interventions.get(i);
            if(users.containsKey(intervention_temp.user))
            {
                user_temp = users.get(intervention_temp.user);
                user_temp.char_count = user_temp.char_count + intervention_temp.char_count;
                user_temp.word_count = user_temp.word_count + intervention_temp.word_count;
                user_temp.post_count++;
                users.put(user_temp.name, user_temp);
            }
            else
            {
                users.put(intervention_temp.user, new UserAttributes(intervention_temp.user,1,intervention_temp.word_count,intervention_temp.char_count));
            }
        }

        for(int i = 0; i<interventions.size(); i++)
        {
            if(all_users.contains(interventions.get(i).user))
            {
                continue;
            }
            else
            {
                all_users.add(interventions.get(i).user);
            }
        }

        oout.writeChars("DL"); oout.writeChar('\n');
        oout.writeChars(String.format("N=%d",all_users.size())); oout.writeChar('\n');
        oout.writeChars("FORMAT = EDGELIST1"); oout.writeChar('\n');
        oout.writeChars("ROW LABELS:"); oout.writeChar('\n');

        for(int i=0; i < all_users.size() ;i++)
        {
        	oout.writeChars(all_users.get(i));
        	oout.writeChar('\n');
        }
        oout.writeChars("DATA:"); oout.writeChar('\n');
        for(int i=0; i<all_users.size();i++)
        {
            for(int j=0; j<all_users.size();j++)
            {
                if(null != graph.findEdge(all_users.get(i),all_users.get(j)))
                {
                	oout.writeChars(String.format("%d %d %f", (i+1),(j+1),graph.findEdge(all_users.get(i),all_users.get(j)).weight));
                	oout.writeChar('\n');
                }
            }
        }

        oout.close();

        outFile = new GcsFilename(BUCKET_NAME, graphFile+"_attributes.txt");

		outputChannel = service.createOrReplace(outFile,
				options);

		oout = new ObjectOutputStream(
				Channels.newOutputStream(outputChannel));
        
        //out_file = new BufferedWriter(new FileWriter(directory + fileNameSanitize(current_location)+"_attributes.txt"));

        oout.writeChars("*node data"); oout.writeChar('\n');
        oout.writeChars("ID\tPost-count\tWord-count\tCharacter-count"); oout.writeChar('\n');
        for(int i=0;i<all_users.size();i++)
        {
            user_temp = users.get(all_users.get(i));
            oout.writeBytes(String.format("%s\t%d\t%d\t%d",all_users.get(i),user_temp.getPostCount(),user_temp.getWordCount(),user_temp.getCharCount()));
            oout.write('\n');
        }
        oout.close();

        
    }
    private void writePageThread_File(ArrayList<String> page_threads, String current_page, GcsFilename srcName) throws IOException
    {

        //BufferedWriter out_file = new BufferedWriter(new FileWriter(directory + fileNameSanitize(current_page) + "_threads.txt"));
    	Builder optionsBuild = new GcsFileOptions.Builder().acl("public-read");
		GcsFileOptions options = optionsBuild.build();
		
		GcsFilename outFile = new GcsFilename(BUCKET_NAME, fileNameSanitize(current_page) + "_threads.txt");

		outputChannel = service.createOrReplace(outFile,
				options);

		oout = new ObjectOutputStream(
				Channels.newOutputStream(outputChannel));
    	
        for(int i=0; i<page_threads.size();i++)
        {
            oout.writeBytes(page_threads.get(i));
            oout.write('\n');
        }

        oout.close();
    }
    
    private static DirectedSparseGraph<String,Edge> buildGraph(ArrayList<InterventionAttributes> interventions)
    {
        DirectedSparseGraph<String,Edge> graph = new DirectedSparseGraph<String,Edge>();
        InterventionAttributes iv1, iv2;
        Edge temp_edge;

        for(int i =0; i<interventions.size();i++)
        {
            graph.addVertex(interventions.get(i).user);
        }
        for(int i = 0; i < interventions.size();i++)
        {
            iv1 = interventions.get(i);
            for(int j = 0; j < i;j++)
            {
                iv2 = interventions.get(j);
                if(i==j)
                    continue;
                if(j>i)
                {
                    break;
                }

                if(iv2.user.compareTo(iv1.user) == 0)
                    continue;

                if( (temp_edge = graph.findEdge(iv1.user, iv2.user)) == null)
                {
                    float iv1_words = (float) iv1.word_count;
                    float iv2_words = (float) iv2.word_count;
                    float i_calc = (float) i;
                    float j_calc = (float) j;

                    graph.addEdge(new Edge(iv1_words*iv2_words/(i_calc-j_calc)/(i_calc-j_calc)), iv1.user, iv2.user);
                }
                else
                {
                    float iv1_words = (float) iv1.word_count;
                    float iv2_words = (float) iv2.word_count;
                    float i_calc = (float) i;
                    float j_calc = (float) j;
                    temp_edge.weight = temp_edge.weight + iv1_words*iv2_words/(i_calc-j_calc)/(i_calc-j_calc);
                    graph.addEdge(temp_edge, iv1.user,iv2.user);
                }
            }
        }


        return graph;
    }
    private static void combineNetworks(DirectedSparseGraph<String,Edge> target, DirectedSparseGraph<String,Edge> source)
    {
        Iterator<String> from_Iterator = source.getVertices().iterator();
        Iterator<String> to_Iterator;

        Edge sourceEdge;
        Edge targetEdge;

        String from_user;
        String to_user;

        while(from_Iterator.hasNext())
        {
            from_user = from_Iterator.next();

            to_Iterator = source.getVertices().iterator();

            while(to_Iterator.hasNext())
            {
                to_user = to_Iterator.next();
                if(to_user.compareTo(from_user) == 0)
                    continue;

                if(source.findEdge(from_user, to_user) != null)
                {
                    sourceEdge = source.findEdge(from_user, to_user);

                    if(target.findEdge(from_user, to_user) != null)
                    {
                        targetEdge = target.findEdge(from_user,to_user);
                        targetEdge.weight = targetEdge.weight + sourceEdge.weight;
                    }
                    else
                        target.addEdge(sourceEdge, from_user, to_user);
                }
                else
                    continue;

            }

        }

    }

   
}