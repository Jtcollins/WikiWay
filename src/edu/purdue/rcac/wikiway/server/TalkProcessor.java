package edu.purdue.rcac.wikiway.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.nio.channels.Channels;
//import java.text.DateFormat;
//import java.text.SimpleDateFormat;
import java.util.ArrayList;
//import java.util.Date;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.wikimodel.wem.IWemListener;
import org.wikimodel.wem.WikiFormat;
import org.wikimodel.wem.WikiParameters;
import org.wikimodel.wem.WikiParserException;
import org.wikimodel.wem.WikiReference;
import org.wikimodel.wem.mediawiki.MediaWikiParser;
import org.apache.commons.lang.StringEscapeUtils;

import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.appengine.tools.cloudstorage.GcsFileOptions.Builder;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;

public class TalkProcessor {

	private static final String PAGE_TAG = "page";
	private static final String REVISION_TAG = "revision";
	private static final String TITLE_TAG = "title";
	private static final String ID_TAG = "id";
	private static final String CONTRIBUTOR_TAG = "contributor";
	private static final String IP_TAG = "ip";
	private static final String TIMESTAMP_TAG = "timestamp";
	private static final String TEXT_TAG = "text";
	private static final String USERNAME_TAG = "username";
	//public static final String BUCKET_NAME = "process_output";

	private PageInfo page;
	
	private GcsFilename outFile;
	private GcsOutputChannel outputChannel;
	private ObjectOutputStream oout;

	private String outputFile = "process";

	public GcsFilename getOutputFile() {
		return outFile;
	}
	
	public void setOutputFile(String file) {
		outputFile = "process";
	}


	/*
	 * public static void main(String[] args) {
	 * 
	 * try { outputFile = args[1]; //outputFile =
	 * "Z:\\wiki\\testrun\\outlog.txt"; TalkProcessor tp = new TalkProcessor();
	 * tp.process(args[0]);
	 * //tp.process("Z:\\wiki\\WikipediaNPOVTALKdumplastversion02192010allpages.xml"
	 * ); } catch (Exception e) { e.printStackTrace(); } }
	 */

	//private BufferedWriter debug_out_file;

	private class PageInfo {

		private String title;
		private String id;

		/**
		 * @return the title
		 */
		public String getTitle() {
			return title;
		}

		/**
		 * @param title
		 *            the title to set
		 */
		public void setTitle(String title) {
			this.title = title;
		}

		/**
		 * @return the id
		 */
		public String getId() {
			return id;
		}

		/**
		 * @param id
		 *            the id to set
		 */
		public void setId(String id) {
			this.id = id;
		}

		@Override
		public String toString() {
			return id + "," + title;
		}
	}

	public void process(GcsFilename srcFile, GcsService gcsService)
			throws Exception {

		GcsInputChannel readChannel = gcsService.openPrefetchingReadChannel(
				srcFile, 0, 1024 * 1024);

		try (ObjectInputStream oin = new ObjectInputStream( Channels.newInputStream(readChannel))) {

			BufferedInputStream bin = new BufferedInputStream(
					(InputStream) oin);
			XMLInputFactory inputFactory = XMLInputFactory.newInstance();
			XMLStreamReader streamReader = inputFactory
					.createXMLStreamReader(bin);
			
			Builder optionsBuild = new GcsFileOptions.Builder().acl("public-read");
			GcsFileOptions options = optionsBuild.build();
			
			outFile = new GcsFilename("process_output", outputFile);
			outputChannel = gcsService.createOrReplace(outFile,
					options);

			oout = new ObjectOutputStream(
					Channels.newOutputStream(outputChannel));
			// out_file = new BufferedWriter(new FileWriter(outputFile));

			page = null;

			while (streamReader.hasNext()) {
				int event = streamReader.next();
				if (event == streamReader.START_ELEMENT) {
					String tag = streamReader.getLocalName();

					if (PAGE_TAG.equalsIgnoreCase(tag)) {
						// at the start of a page
						page = new PageInfo();
					} else if (TITLE_TAG.equalsIgnoreCase(tag)) {
						// start of title
						while (streamReader.hasNext()) {
							if (streamReader.CHARACTERS == streamReader.next()) {
								break;
							}
						}
						page.setTitle(streamReader.getText());
					} else if (ID_TAG.equalsIgnoreCase(tag)) {
						// start of id tag
						while (streamReader.hasNext()) {
							if (streamReader.CHARACTERS == streamReader.next()) {
								break;
							}
						}
						page.setId(streamReader.getText());
					} else if (TEXT_TAG.equalsIgnoreCase(tag)) {
						// at the tag of interest
						String s = streamReader.getElementText();

						s = StringEscapeUtils.unescapeHtml(s);
						s = RemoveErrorSources(s);

						// debug_out_file = new BufferedWriter(new
						// FileWriter("Z:\\wiki\\out_string\\" +
						// WikiGraph.fileNameSanitize(page.getTitle()) +
						// ".txt"));
						// debug_out_file.write(s);
						// debug_out_file.close();

						tagstack.add(new wikiThread("Default_"
								+ page.getTitle()));

						processText_wikimodel(s);

					}
				} else if (event == streamReader.END_ELEMENT) {
					String tag = streamReader.getLocalName();
					if (PAGE_TAG.equalsIgnoreCase(tag)) {
						// end of the page element
						// System.out.println(page);
						page = null;
						// System.exit(0);
					} else if (TEXT_TAG.equalsIgnoreCase(tag)) {
						// end of the text element
					}

				}
			}

			oout.close();
		}
	}

	private void except(Exception e) {
		// TODO Auto-generated method stub

	}

	public String csvSanitize(String inString) {
		return inString.replace(',', '.');
	}

	private String stripHTML(String inString) {
		String outString = new String();

		int open = 0, close = 0;

		for (int i = 0; i < inString.length(); i++) {
			if (inString.charAt(i) == '<')
				open++;
			if (inString.charAt(i) == '>')
				close++;
		}

		if (open == close && open == 0) {
			return inString;
		}

		ArrayList<Character> passString = new ArrayList<Character>();
		ArrayList<Character> evalString = new ArrayList<Character>();

		boolean evaluate = false;

		for (int i = 0; i < inString.length(); i++) {
			if (evaluate) {
				if (inString.charAt(i) == '>') {
					evalString.clear();
				} else if (inString.charAt(i) == '<') {
					Iterator<Character> tempIterate = evalString.iterator();
					while (tempIterate.hasNext()) {
						passString.add(tempIterate.next());
					}
					evalString.clear();
					evalString.add('<');
				} else {
					evalString.add(inString.charAt(i));
				}
			} else {
				if (inString.charAt(i) == '<') {
					evaluate = true;
					evalString.add('<');
				} else {
					passString.add(inString.charAt(i));
				}
			}

		}

		return outString;
	}

	// RemoveErrorSources - note
	// This routine is sort of a hack, but the newline followed by |+ causes
	// the parser to do something stupid and crash - I noticed that the parser
	// generates its own html/xml tags, and in this case it makes one that
	// it doesn't know what to do with later on; good design, there, I suppose -
	// mpenderg
	private String RemoveErrorSources(String inString) {
		String splitStrings[] = inString.split("\\r?\\n\\|\\+");

		inString = new String();
		for (int i = 0; i < splitStrings.length; i++) {
			inString = inString.concat(splitStrings[i]);
		}

		return inString;

	}

	private abstract class wikiCloneable extends Object {
		public Object getClone() throws CloneNotSupportedException {
			return this.clone();
		}
	}

	private class wikiFormatOpen extends wikiCloneable {
		@Override
		public String toString() {
			return "wikiFormatOpen";
		}
	}

	private class wikiFormatClose extends wikiCloneable {
		@Override
		public String toString() {
			return "wikiFormatClose";
		}
	}

	private class wikiThread extends wikiCloneable {
		private String title;

		public wikiThread() {
			this.title = new String();
		}

		public wikiThread(String newTitle) {
			this.title = newTitle;
		}

		public wikiThread(wikiThread wT) {
			this.title = new String(wT.toString());
		}

		@Override
		public String toString() {
			return title;
		}

		public void append(String addString) {
			title = title.concat(addString);
		}

		public void setTitle(String newTitle) {
			title = newTitle;
		}

	}

	private class wikiUser extends wikiCloneable {
		public String userName;

		public wikiUser() {
			this.userName = new String();
		}

		public wikiUser(String newName) {
			String temp;
			String[] newName_parts = newName.split("/");
			temp = newName_parts[0];
			this.userName = temp.trim();
		}

		@Override
		public String toString() {
			return userName;
		}

	}

	private class wikiString extends wikiCloneable {
		private String content;
		private int wordcount;
		private int charcount;

		public wikiString() {
			this.content = new String();
		}

		public wikiString(wikiString old_string) {
			this.content = old_string.toString();
			this.wordcount = old_string.getWordCount();
			this.charcount = old_string.getCharCount();
		}

		public wikiString(wikiUser wkU) {
			this.content = wkU.userName;

			this.charcount = this.content.length();

			this.wordcount = 0;
			for (int i = 1; i < this.content.length(); i++) {
				if ((this.content.charAt(i) != ' ')
						&& (this.content.charAt(i - 1) == ' ')) {
					this.wordcount = this.wordcount + 1;
				}
			}
		}

		@Override
		public String toString() {
			return content;
		}

		public int getWordCount() {
			return this.wordcount;
		}

		public int getCharCount() {
			return this.charcount;
		}

		public void append(String addString) {
			content = content.concat(addString);
			wordcount = wordcount + 1;
			charcount = charcount + addString.length();
		}

		public void append(char addChar) {
			content = content.concat(Character.toString(addChar));
			charcount = charcount + 1;
		}

		public void addspace() {
			content = content.concat(" ");
			charcount = charcount + 1;
		}

		public boolean contains(String compstr) {
			return this.content.contains(compstr);
		}

		public boolean isEmpty() {
			return this.content.trim().isEmpty();
		}

		public void setWordCount(int newCount) {
			this.wordcount = newCount;
		}

		public void setCharCount(int newCount) {
			this.charcount = newCount;
		}

		public void setContent(String newContent) {
			this.content = newContent;

			this.charcount = this.content.length();

			this.wordcount = 0;
			for (int i = 1; i < this.content.length(); i++) {
				if ((this.content.charAt(i) != ' ')
						&& (this.content.charAt(i - 1) == ' ')) {
					this.wordcount = this.wordcount + 1;
				}
			}

		}

		public void clean() {
			this.charcount = this.content.length();

			this.wordcount = 0;
			for (int i = 1; i < this.content.length(); i++) {
				if ((this.content.charAt(i) != ' ')
						&& (this.content.charAt(i - 1) == ' ')) {
					this.wordcount = this.wordcount + 1;
				}
			}
		}

		public void merge(wikiString WS1) {
			String newContent = new String();
			int newWCount;
			int newCCount;

			newContent = WS1.toString().toString();
			newWCount = WS1.getWordCount();
			newCCount = WS1.getCharCount();

			this.content = this.content.concat(newContent);
			this.wordcount = this.wordcount + newWCount;
			this.charcount = this.charcount + newCCount;

		}
	}

	private class wikiDate extends wikiCloneable {
		public String date;
		public int hour;
		public int minute;
		public int day;
		public String month;
		public int year;
		public boolean proper_date;
		public String zone;

		public wikiDate() {
			this.date = new String();
			this.proper_date = false;
		}

		public wikiDate(String DT) {
			this.date = DT.trim();
			this.proper_date = false;
		}

		@Override
		public String toString() {
			this.properDate();
			String datestring = new String();
			String yearstring = Integer.toString(year);
			if (yearstring.length() != 4) {
				yearstring = "0000";
			}
			datestring = datestring.concat(yearstring);

			if (month.contains("Jan")) {
				datestring = datestring.concat("01");
			} else if (month.contains("Feb")) {
				datestring = datestring.concat("02");
			} else if (month.contains("Mar")) {
				datestring = datestring.concat("03");
			} else if (month.contains("Apr")) {
				datestring = datestring.concat("04");
			} else if (month.contains("May")) {
				datestring = datestring.concat("05");
			} else if (month.contains("Jun")) {
				datestring = datestring.concat("06");
			} else if (month.contains("Jul")) {
				datestring = datestring.concat("07");
			} else if (month.contains("Aug")) {
				datestring = datestring.concat("08");
			} else if (month.contains("Sep")) {
				datestring = datestring.concat("09");
			} else if (month.contains("Oct")) {
				datestring = datestring.concat("10");
			} else if (month.contains("Nov")) {
				datestring = datestring.concat("11");
			} else if (month.contains("Dec")) {
				datestring = datestring.concat("12");
			} else {
				datestring = datestring.concat("MM");
			}

			String daystring = Integer.toString(day);
			// if(daystring.length() > 2) System.out.println("Overlong");
			if (daystring.length() != 2)
				daystring = "0" + daystring;
			datestring = datestring.concat(daystring);

			String hourstring = Integer.toString(hour);
			// if(hourstring.length() > 2) System.out.println("Overlong");
			if (hourstring.length() != 2)
				hourstring = "0" + hourstring;
			datestring = datestring.concat(hourstring);

			String minutestring = Integer.toString(minute);
			// if(minutestring.length() > 2) System.out.println("Overlong");
			if (minutestring.length() != 2)
				minutestring = "0" + minutestring;
			datestring = datestring.concat(minutestring);

			return datestring;
		}

		public void properDate() {
			this.date = this.date.trim();
			// Every time I think I have this figured out, those troublesome
			// wikipedians think of /yet another way/ to format their timestamp

			this.date = this.date.replace(",", "");
			this.date = this.date.trim();

			String[] date_arr = this.date.split(" ");

			// System.out.println(date);

			proper_date = true;

			if (date_arr.length != 5) {
				// System.out.println("error");
				hour = minute = day = year = 0;
				month = zone = "err";
			} else {
				ArrayList<String> date_array_pieces = new ArrayList<String>();
				for (int i = 0; i < date_arr.length; i++) {
					date_array_pieces.add(date_arr[i]);
				}

				// pull the time out
				for (int i = 0; i < date_array_pieces.size(); i++) {
					if (date_array_pieces.get(i).contains(":")) {
						String[] time_parts = date_array_pieces.get(i).trim()
								.split(":");
						hour = Integer.parseInt(time_parts[0]);
						minute = Integer.parseInt(time_parts[1]);
						date_array_pieces.remove(i);
					}
				}

				// pull the time zone
				for (int i = 0; i < date_array_pieces.size(); i++) {
					if (date_array_pieces.get(i).contains("(")) {
						zone = date_array_pieces.get(i).trim();
						date_array_pieces.remove(i);
						break;
					}
				}

				for (int i = 0; i < date_array_pieces.size(); i++) {
					if (date_array_pieces.get(i).matches("[A-Za-z]*")) {
						month = date_array_pieces.get(i);
					} else {
						int temp_int = Integer.parseInt(date_array_pieces
								.get(i).trim());

						if (temp_int > 31)
							year = temp_int;
						else
							day = temp_int;
					}
				}
			}
		}

		public boolean sameDate(wikiDate wkD) {
			this.properDate();
			wkD.properDate();

			if (this.toString().compareTo(wkD.toString()) != 0)
				return false;
			else
				return true;
		}

	}

	private class wikiIntervention extends wikiCloneable {

		public wikiUser owner;
		public wikiString content;
		public wikiDate date;
		public wikiThread thread;
		public String page;

		public wikiIntervention() {

			owner = null;
			content = new wikiString();
			date = null;
		}

		public wikiIntervention(wikiUser wkU) {
			owner = wkU;
			content = new wikiString();
			date = null;
		}

		public wikiIntervention(wikiDate wkD) {
			owner = null;
			content = new wikiString();
			date = wkD;
		}

		public wikiIntervention(wikiUser wkU, wikiDate wkD) {
			owner = wkU;
			content = new wikiString();
			date = wkD;
		}

		@Override
		public String toString() {
			String retVal = new String();
			retVal = retVal.concat("Owner: ");
			if (owner == null) {
				retVal = retVal.concat("null --- ");
			} else {
				retVal = retVal.concat(owner.toString() + " --- ");
			}
			retVal = retVal.concat("Date: ");
			if (date == null) {
				retVal = retVal.concat("null --- ");
			} else {
				retVal = retVal.concat(date.toString() + " --- ");
			}
			// retVal = retVal.concat("Content: ");
			// if(content.isEmpty())
			// {
			// retVal = retVal.concat("empty --- ");
			// }
			// else
			// {
			// retVal = retVal.concat(content.toString() + " --- ");
			// }
			retVal = retVal
					.concat("Word count: " + this.content.getWordCount());

			return retVal;
			// return content.toString() + "    " + owner.toString() + "    " +
			// date.toString();
		}

		public String printInfo() {
			if (date == null) {
				date = new wikiDate();
				date.date = "00:00, 0 Nomonth 0000 (XYZ)";
				date.properDate();
			}
			return "\"" + csvSanitize(page) + "\",\""
					+ csvSanitize(thread.toString()) + "\",\""
					+ csvSanitize(owner.toString()) + "\"," + content.wordcount
					+ "," + content.charcount + "," + date.toString();
		}

		public void appendContentFront(wikiString add_content) {
			wikiString temp = new wikiString(add_content);
			temp.merge(content);
			content = temp;
		}

		public void merge(wikiIntervention wkI) {
			if (this.owner == null && wkI.owner != null)
				this.owner = wkI.owner;
			if (this.date == null && wkI.date != null)
				this.date = wkI.date;
			if (this.content == null && wkI.content != null)
				this.content = wkI.content;
			else if (this.content != null && wkI.content != null)
				this.content.merge(wkI.content);
		}

		public void clean() {
			if (this.date != null)
				this.date.properDate();
			if (this.content != null)
				this.content.clean();
		}

	}

	private class wikiNewLine extends wikiCloneable {
		@Override
		public String toString() {
			return "wikiNewLine";
		}
	}

	Deque<Object> tagstack = new LinkedList<Object>();
	Deque<Object> tagstack2 = new LinkedList<Object>();

	EnvisionListener EL;
	StringReader StrRead;

	// BufferedWriter out_file;

	private void processText_wikimodel(String inString)
			throws WikiParserException, FileNotFoundException, IOException,
			CloneNotSupportedException {
		StrRead = new StringReader(inString);
		EL = new EnvisionListener();
		MediaWikiParser CWKP = new MediaWikiParser();

		CWKP.parse(StrRead, EL); // Build the tagstack

		// Debug printing
		// Print to file - currently not complete
		// debug_out_file = new BufferedWriter(new
		// FileWriter("Z:\\wiki\\tags_out\\tagstack"+WikiGraph.fileNameSanitize(page.getTitle())+".txt"));
		//
		// while(tagstack.isEmpty() == false)
		// {
		// Object temp;
		// temp = tagstack.pollFirst();
		// debug_out_file.write(temp.getClass().toString());
		// debug_out_file.write("  --  ");
		// debug_out_file.write(temp.toString());
		// debug_out_file.newLine();
		// tagstack2.addLast(temp);
		// }
		//
		// debug_out_file.close();
		//
		//
		// tagstack = tagstack2;
		// tagstack2 = new LinkedList<Object>();

		// Strip out empty wikiStrings, empty Threads, HTML tags
		while (tagstack.isEmpty() == false) {
			Object temp;
			temp = tagstack.pollFirst();
			if (temp.getClass() == wikiString.class) {
				wikiString tempWikiString = (wikiString) temp;
				tempWikiString.setContent(stripHTML(tempWikiString.toString()));
				if (tempWikiString.isEmpty()) {
					continue;
				}
				tagstack2.addLast(tempWikiString);

			} else if (temp.getClass() == wikiThread.class) {
				wikiThread tempWikiThread = (wikiThread) temp;
				tempWikiThread.setTitle(stripHTML(tempWikiThread.toString()));
				if (tempWikiThread.toString().isEmpty()) {
					continue;
				}
				tagstack2.addLast(tempWikiThread);
			} else {
				tagstack2.addLast(temp);
			}
		}

		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();

		// Eliminate extraneous format markers, convert some strings to dates
		while (tagstack.isEmpty() == false) {
			Object temp;
			temp = tagstack.pollFirst();
			if (temp.getClass() == wikiFormatOpen.class) {
				if (tagstack.peekFirst().getClass() == wikiFormatClose.class) {
					tagstack.pollFirst();
					continue;
				}
			}
			if (temp.getClass() == wikiString.class) {
				wikiString tempWikiString = (wikiString) temp;
				String tempStr = tempWikiString.toString();
				if (tempStr.contains("(UTC)")) {
					tempStr = tempStr.trim();
					int i = tempStr.length();
					char check;
					boolean colon = false;
					boolean isnum = true;

					while (true) {
						i--;
						if (i < 0)
							break;
						check = tempStr.charAt(i);
						if (colon) {
							try {
								int garbage = Integer.parseInt(Character
										.toString(check));
							} catch (NumberFormatException nFE) {
								isnum = false;
							}
							if (isnum)
								continue;
							else
								break;
						} else {
							if (check == ':')
								colon = true;
						}

					}

					if (i <= 0) {
						temp = new wikiDate(tempStr);
					} else {
						tempWikiString = new wikiString();
						tempWikiString.setContent(tempStr.substring(0, i));
						tagstack2.addLast(tempWikiString);
						temp = new wikiDate(tempStr.substring(i + 1));
					}
				}
			}
			if (temp.getClass() == wikiUser.class) // delete duplicate user
													// entries
			{
				if (tagstack.peekFirst().getClass() == wikiUser.class) {
					continue;
				}
			}
			tagstack2.addLast(temp);
		}

		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();

		// Run from the back of the deque to the front, creating interventions
		// Currently, I think interventions are marked by a user tag or a date
		// tag preceded by a wikiFormatClose or a wikiNewLine

		while (tagstack.isEmpty() == false) {
			Object temp = tagstack.pollLast();
			if (temp.getClass() == wikiFormatClose.class) {
				if (tagstack.peekLast().getClass() == wikiDate.class) {
					temp = new wikiIntervention((wikiDate) tagstack.pollLast());
				} else if (tagstack.peekLast().getClass() == wikiUser.class) {
					temp = new wikiIntervention((wikiUser) tagstack.pollLast());
				}
			} else if (temp.getClass() == wikiNewLine.class) {
				if (tagstack.peekLast().getClass() == wikiDate.class) {
					temp = new wikiIntervention((wikiDate) tagstack.pollLast());
				} else if (tagstack.peekLast().getClass() == wikiUser.class) {
					temp = new wikiIntervention((wikiUser) tagstack.pollLast());
				}
			}
			tagstack2.addFirst(temp);
		}

		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();
		// Remove all wikiFormat objects and wikiNewLines
		while (tagstack.isEmpty() == false) {
			Object temp = tagstack.pollFirst();
			if (temp.getClass() == wikiFormatOpen.class
					|| temp.getClass() == wikiFormatClose.class
					|| temp.getClass() == wikiNewLine.class) {
				continue;
			}
			tagstack2.addLast(temp);
		}

		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();

		// Combine adjacent wikiStrings
		while (tagstack.isEmpty() == false) {
			Object temp = tagstack.pollFirst();
			if (temp.getClass() == wikiString.class) {
				if (tagstack.isEmpty())
					break; // lose the string if there's nothing left to attach
							// it to
				if (tagstack.peekFirst().getClass() == wikiString.class) {
					((wikiString) tagstack.peekFirst())
							.merge((wikiString) temp);
					continue;
				}
			}
			tagstack2.addLast(temp);
		}
		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();

		// Attach loose wikiStrings and wikiUsers to interventions
		while (tagstack.isEmpty() == false) {
			Object temp = tagstack.pollLast();
			Object temp2;
			wikiIntervention tempIntervention;

			if (temp.getClass() != wikiIntervention.class) {
				tagstack2.addFirst(temp);
			} else {
				tempIntervention = (wikiIntervention) temp;

				if (tagstack.isEmpty()) {
					tagstack2.addFirst(tempIntervention);
					break;
				}
				while (!(tagstack.peekLast().getClass() == wikiIntervention.class || tagstack
						.peekLast().getClass() == wikiThread.class)) {
					temp2 = tagstack.pollLast();

					if (temp2.getClass() == wikiUser.class) {
						if (tempIntervention.owner == null) {
							tempIntervention.owner = (wikiUser) temp2;
						} else {
							tempIntervention.appendContentFront(new wikiString(
									(wikiUser) temp2));
						}
					}
					if (temp2.getClass() == wikiString.class) {
						tempIntervention.appendContentFront((wikiString) temp2);
					}

					if (tagstack.isEmpty()) {
						tagstack2.addFirst(tempIntervention);
						break;
					}
				}
				tagstack2.addFirst(tempIntervention);

			}
		}
		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();

		// Clean out unclaimed wikiStrings

		while (tagstack.isEmpty() == false) {
			Object temp = tagstack.pollFirst();
			if (temp.getClass() == wikiString.class
					|| temp.getClass() == wikiUser.class)
				continue;
			else
				tagstack2.addLast(temp);
		}

		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();

		// Combine incomplete interventions - part 1 - attach unowned dates to
		// users without dates

		while (tagstack.isEmpty() == false) {
			Object temp = tagstack.pollLast();

			if (tagstack.isEmpty()) {
				tagstack2.addFirst(temp);
			} else {
				if (temp.getClass() == wikiIntervention.class) {
					if (((wikiIntervention) temp).date == null
							|| ((wikiIntervention) temp).owner != null) {
						tagstack2.addFirst(temp);
						continue;
					}

					Object temp2 = tagstack.peekLast();
					if (temp2.getClass() != wikiIntervention.class) {
						tagstack2.addFirst(temp);
						continue;
					} else {
						wikiIntervention current = (wikiIntervention) temp;
						wikiIntervention next = (wikiIntervention) temp2;

						if (next.date != null) {
							if (next.date.sameDate(current.date)) {
								next.merge(current);
								continue;
							} else {
								tagstack2.addFirst(current);
							}
						}
					}
				} else if (temp.getClass() == wikiThread.class) {
					tagstack2.addFirst(temp);
				} else // We shouldn't have anything left at this point, but
						// explicitly discard it anyway
				{
					continue;
				}
			}

		}

		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();

		// Combine adjacent posts that have the same user and same date (or the
		// same user and no date)
		while (tagstack.isEmpty() == false) {
			Object temp = tagstack.pollFirst();
			if (tagstack.isEmpty()) {
				tagstack2.addLast(temp);
				break;
			}

			if (temp.getClass() == wikiIntervention.class) {
				if (tagstack.peekFirst().getClass() == wikiIntervention.class) {
					wikiIntervention wkI_1 = (wikiIntervention) temp;
					wikiIntervention wkI_2 = (wikiIntervention) tagstack
							.peekFirst();

					if (wkI_1.owner == null) {
						// Lose unowned interventions at this point
						// System.out.println("Intervention lost");
						continue;
					}
					if (wkI_2.owner == null) {
						tagstack2.addLast(temp);
						continue;
					}

					if (wkI_1.owner.userName.compareTo(wkI_2.owner.userName) == 0) {
						if (wkI_1.date == null || wkI_2.date == null
								|| wkI_1.date.sameDate(wkI_2.date)) {
							wkI_2.merge(wkI_1);
							continue;
						}
					}
				}
			}
			tagstack2.addLast(temp);
		}

		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();

		// Scrap unowned/empty interventions
		while (tagstack.isEmpty() == false) {
			Object temp = tagstack.pollFirst();
			if (temp.getClass() == wikiIntervention.class) {
				if (((wikiIntervention) temp).owner == null
						|| ((wikiIntervention) temp).content.wordcount == 0) {
					// System.out.println("lost intervention");
					continue;
				}
			}
			tagstack2.addLast(temp);
		}

		tagstack = tagstack2;
		tagstack2 = new LinkedList<Object>();

		// Produce output for wikiGraph

		ArrayList<wikiIntervention> thread_interventions = new ArrayList<wikiIntervention>();

		wikiThread current_thread_temp = new wikiThread("Default_"
				+ page.getTitle());

		while (tagstack.isEmpty() == false) {
			Object temp = tagstack.pollFirst();
			if (temp.getClass() == wikiThread.class) {
				Iterator<wikiIntervention> thread_iterator = thread_interventions
						.iterator();
				while (thread_iterator.hasNext()) {
					wikiIntervention wkI = thread_iterator.next();
					wkI.clean();
					oout.writeBytes("\"" + csvSanitize(page.getTitle()) + "\",");
					oout.writeBytes("\""
							+ csvSanitize(current_thread_temp.toString())
							+ "\",");
					oout.writeBytes("\"" + csvSanitize(wkI.owner.userName)
							+ "\",");
					oout.writeBytes(wkI.content.getWordCount() + ",");
					oout.writeBytes(wkI.content.getCharCount() + ",");
					if (wkI.date != null) {
						oout.writeBytes(wkI.date.toString());
					} else {
						oout.writeBytes("0000MM000000"); // Error time stamp
					}
					oout.write('\n');

				}
				current_thread_temp = (wikiThread) temp;
				thread_interventions = new ArrayList<wikiIntervention>();
			} else {
				thread_interventions.add((wikiIntervention) temp);
			}

		}
	}

	boolean print = false;
	boolean Header2 = false;

	private class EnvisionListener implements IWemListener {

		public void beginHeader(int arg0, WikiParameters arg1) {
			if (print)
				System.out.println("beginHeader");
			// arg0 is header level
			Header2 = true;
			tagstack.add(new wikiThread());
		}

		public void endHeader(int arg0, WikiParameters arg1) {
			if (print)
				System.out.println("endHeader");
			if (print)
				System.out.println("    " + arg0 + " " + arg1.toString());
			Header2 = false;
			tagstack.add(new wikiString());

		}

		public void onVerbatimBlock(String arg0, WikiParameters arg1) {
			if (print)
				System.out.println("onVerbatimBlock" + arg0 + "   +   "
						+ arg1.toString());
			try {
				(new MediaWikiParser()).parse(new StringReader(arg0), this);
			} catch (WikiParserException WPE) {
				WPE.printStackTrace();
				System.exit(1);
			}
		}

		public void beginFormat(WikiFormat arg0) {
			tagstack.add(new wikiFormatOpen());
			fixTagStack();
		}

		public void endFormat(WikiFormat arg0) {
			tagstack.add(new wikiFormatClose());
		}

		public void onReference(String arg0) {
			if (print)
				System.out.println("onReference-2");
			if (print)
				System.out.println("    " + arg0);

			if (Header2) {
				fixHeader();
				((wikiThread) tagstack.peekLast()).append(arg0);
			} else if (arg0.contains("User") || arg0.contains("user")) {
				if (userReferenceDisqualify(arg0) == false) // if you can't
															// disqualify the
															// user reference
				{
					if (arg0.contains("User:") || arg0.contains("user:")) {
						tagstack.add(new wikiUser(arg0.substring(5)));
					}
					if (arg0.contains("User Talk:")
							|| arg0.contains("User talk:")
							|| arg0.contains("User_talk:")) {
						tagstack.add(new wikiUser(arg0.substring(10)));
					}
					fixTagStack();
					// add new wikistring?
				} else {

					if (tagstack.peekLast().getClass() == wikiString.class) {
						wikiString tempWStr = (wikiString) tagstack.peekLast();
						if (tempWStr.toString().trim().contains("|")) {
							tagstack.pollLast();
						}
					}
					// lose the tag - for our purposes, I don't think we care
					// which wikipedians have eight million links in their
					// signature
				}
			} else if (otherReferenceDisqualify(arg0)) {
				// Do nothing - lose disqualified references, as before
			} else if (tagstack.peekLast().getClass() == wikiString.class) {
				((wikiString) tagstack.peekLast()).append(arg0);
			} else {
				wikiString temp = new wikiString();
				temp.append(arg0);
				tagstack.add(temp);
			}
		}

		public void onReference(WikiReference arg0) {
			if (print)
				System.out.println("onReference");
			if (print)
				System.out.println(arg0.getLink() + "    " + arg0.getLabel()
						+ "    " + arg0.toString());

			String tempStr = arg0.getLink();

			if (Header2) {
				fixHeader();
				((wikiThread) tagstack.peekLast()).append(tempStr);
			} else if (tempStr.contains("User") || tempStr.contains("user")) {
				if (userReferenceDisqualify(tempStr) == false) {
					if (tempStr.contains("User:") || tempStr.contains("user:")) {
						tagstack.add(new wikiUser(tempStr.substring(5)));
					}
					if (tempStr.contains("User Talk:")
							|| tempStr.contains("User talk:")
							|| tempStr.contains("User_talk:")) {
						tagstack.add(new wikiUser(tempStr.substring(10)));
					}
					fixTagStack();
					// add new wikistring?
				} else {
					if (tagstack.peekLast().getClass() == wikiString.class) {
						wikiString tempWStr = (wikiString) tagstack.peekLast();
						if (tempWStr.toString().trim().contains("|")) {
							tagstack.pollLast();
						}
					}
					// lose the tag - for our purposes, I don't think we care
					// which wikipedians have eight million links in their
					// signature
				}
			} else if (otherReferenceDisqualify(tempStr)) {
				// Do nothing - lose disqualified references, as before
			} else if (tagstack.peekLast().getClass() == wikiString.class) {
				((wikiString) tagstack.peekLast()).append(tempStr);
			} else {
				wikiString temp = new wikiString();
				temp.append(tempStr);
				tagstack.add(temp);
			}
		}

		public void onSpace(String arg0) {
			if (print)
				System.out.println("onSpace");
			if (print)
				System.out.println("    " + arg0);
			if ((Header2 == true)) {
				fixHeader();
				((wikiThread) tagstack.peekLast()).append(arg0);
			}
			if ((tagstack.peekLast().getClass() == wikiString.class)) {
				((wikiString) tagstack.peekLast()).append(' ');
			}
		}

		public void onSpecialSymbol(String arg0) {
			if (print)
				System.out.println("onSpecialSymbol");
			if (print)
				System.out.println("    " + arg0);
			if (Header2 == true) {
				fixHeader();
				((wikiThread) tagstack.peekLast()).append(arg0);
			}
			if (tagstack.peekLast().getClass() == wikiString.class) {
				if (((wikiString) tagstack.peekLast()).isEmpty() == false
						|| arg0.contains("<") || arg0.contains(">")) {
					((wikiString) tagstack.peekLast()).append(arg0);
				}
			}
		}

		public void onWord(String arg0) {
			if (Header2 == true) {
				fixHeader();
				((wikiThread) tagstack.peekLast()).append(arg0);
			} else if (tagstack.peekLast().getClass() == wikiString.class) {
				((wikiString) tagstack.peekLast()).append(arg0);
			} else {
				wikiString temp = new wikiString();
				temp.append(arg0);
				tagstack.add(temp);
			}

			if (print)
				System.out.println("onWord");
			if (print)
				System.out.println("    " + arg0);
		}

		public void onNewLine() {
			if (print)
				System.out.println("onNewLine");
			tagstack.add(new wikiNewLine());
			fixTagStack();
		}

		// // userReferenceDisqualify - Helper function that disqualifies
		// references containing the word user when appropriate
		private boolean userReferenceDisqualify(String tag) {
			// tag.contains("User talk:") || tag.contains("User Talk:")|| -
			// these will be used as user references, too
			return (tag.contains("http://en.wikipedia.org")
					|| tag.contains("Special:Contributions")
					|| tag.contains("/news") || tag.contains("/poll"));
		}

		private boolean otherReferenceDisqualify(String tag) {
			return (tag.contains("Special:Contributions") || tag
					.contains("Image:"));
		}

		// // fixHeader - Helper function that adds a wikiThread to the tagstack
		// if it needs it
		private void fixHeader() {
			if (tagstack.peekLast().getClass() != wikiThread.class)
				tagstack.addLast(new wikiThread());
		}

		// // fixTagStack - Helper function that puts wikiStrings and
		// wikiThreads on the tagstack when appropriate
		private void fixTagStack() {
			if (Header2 && tagstack.peekLast().getClass() != wikiThread.class)
				tagstack.addLast(new wikiThread());
			else if (tagstack.peekLast().getClass() != wikiString.class)
				tagstack.addLast(new wikiString());
		}

		// ////////////////////////////////////////////////////// The does
		// almost nothing block - now part of the does nothing block
		public void beginDefinitionDescription() {
			if (print)
				System.out.println("beginDefinitionDescription");
			// if(Header2 == false) tagstack.add(new wikiString());
		}

		public void beginDefinitionList(WikiParameters arg0) {
			if (print)
				System.out.println("beginDefinitionList");
			// if(Header2 == false) tagstack.add(new wikiString());
		}

		public void beginList(WikiParameters arg0, boolean arg1) {
			if (print)
				System.out.println("beginList");
			// if(Header2 == false) tagstack.add(new wikiString());
		}

		public void endDefinitionDescription() {
			if (print)
				System.out.println("endDefinitionDescription");
			// if(Header2 == false) tagstack.add(new wikiString());
		}

		public void endListItem() {
			if (print)
				System.out.println("endListItem");
			// if(Header2 == false) tagstack.add(new wikiString());
		}

		// ////////////////////////////////////////////////////// The does
		// nothing block
		public void beginDocument(WikiParameters arg0) {
			// Do nothing
		}

		public void beginSection(int arg0, int arg1, WikiParameters arg2) {
			if (print)
				System.out.println("beginSection");
			// .out.println("    " + arg0 + " " + arg1 + " " + arg2.getSize());
			// do nothing
		}

		public void beginSectionContent(int arg0, int arg1, WikiParameters arg2) {
			if (print)
				System.out.println("beginSectionContent");
			if (print)
				System.out.println("    " + arg0 + " - " + arg1 + " "
						+ arg2.getSize());
			// do nothing
		}

		public void endDocument(WikiParameters arg0) {
			// do nothing
		}

		public void endSection(int arg0, int arg1, WikiParameters arg2) {
			if (print)
				System.out.println("endSection");
			// do nothing
		}

		public void endSectionContent(int arg0, int arg1, WikiParameters arg2) {
			if (print)
				System.out.println("endSectionContent");
			// do nothing
		}

		public void beginInfoBlock(String arg0, WikiParameters arg1) {
			// do nothing
		}

		public void beginParagraph(WikiParameters arg0) {
			if (print)
				System.out.println("beginParagraph");
			if (print)
				System.out.println("    " + arg0.toString());
			// do nothing
		}

		public void endInfoBlock(String arg0, WikiParameters arg1) {
			// do nothing
		}

		public void endParagraph(WikiParameters arg0) {
			if (print)
				System.out.println("endParagraph");
			if (print)
				System.out.println("    " + arg0.toString());
			// if(Header2 == false) tagstack.add(new wikiString());
			// do nothing
		}

		public void onEmptyLines(int arg0) {
			// I haven't encountered this yeet, but I suspect it'll be a
			// "Do nothing" case
			if (print)
				System.out.println("Empty Line");
			// do nothing
		}

		public void onHorizontalLine(WikiParameters arg0) {
			if (print)
				System.out.println("onHorizontalLine" + arg0.toString());
			// do nothing
		}

		public void onEscape(String arg0) {
			// do nothing
		}

		public void onImage(String arg0) {
			// do nothing
		}

		public void onImage(WikiReference arg0) {
			// do nothing
		}

		public void onLineBreak() {
			if (print)
				System.out.println("onLineBreak");
			// do nothing
		}

		public void onVerbatimInline(String arg0, WikiParameters arg1) {
			// do nothing
		}

		public void beginTable(WikiParameters arg0) {
			if (print)
				System.out.println(arg0.toString());
		}

		public void beginTableCell(boolean arg0, WikiParameters arg1) {
			if (print)
				System.out.println("beginTableCell" + " + + " + arg0
						+ " + + + " + arg1.toString());
		}

		public void beginTableRow(WikiParameters arg0) {
			if (print)
				System.out.println(arg0.toString());
		}

		public void endTable(WikiParameters arg0) {
			if (print)
				System.out.println("endTable" + arg0.toString());
		}

		public void endTableCell(boolean arg0, WikiParameters arg1) {
			if (print)
				System.out.println("endTableCell" + arg0 + "     "
						+ arg1.toString());
		}

		public void endTableRow(WikiParameters arg0) {
			if (print)
				System.out.println("endTableRow" + arg0.toString());
		}

		public void onTableCaption(String arg0) {
			// do nothing
		}

		public void beginDefinitionTerm() {
			if (print)
				System.out.println("beginDefinitionTerm");
		}

		public void beginListItem() {
			if (print)
				System.out.println("beginListItem");
		}

		public void beginQuotation(WikiParameters arg0) {
			if (print)
				System.out.println(arg0.toString());
		}

		public void beginQuotationLine() {
			if (print)
				System.out.println("beginQuotationLine");
		}

		public void endDefinitionList(WikiParameters arg0) {
			if (print)
				System.out.println("endDefinitionList");
		}

		public void endDefinitionTerm() {
			if (print)
				System.out.println("endDefinitionTerm");
		}

		public void endList(WikiParameters arg0, boolean arg1) {
			if (print)
				System.out.println("endList");
		}

		public void endQuotation(WikiParameters arg0) {
			if (print)
				System.out.println("endQuotation");
		}

		public void endQuotationLine() {
			if (print)
				System.out.println("endQuotationLine");
		}

		public void beginPropertyBlock(String arg0, boolean arg1) {
			// do nothing
		}

		public void beginPropertyInline(String arg0) {
			// do nothing
		}

		public void endPropertyBlock(String arg0, boolean arg1) {
			// do nothing
		}

		public void endPropertyInline(String arg0) {
			// do nothing
		}

		public void onExtensionBlock(String arg0, WikiParameters arg1) {
			// do nothing
		}

		public void onExtensionInline(String arg0, WikiParameters arg1) {
			// do nothing
		}

		public void onMacroBlock(String arg0, WikiParameters arg1, String arg2) {
			// do nothing
		}

		public void onMacroInline(String arg0, WikiParameters arg1, String arg2) {
			// do nothing
		}
	}
}
