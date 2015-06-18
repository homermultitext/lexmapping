/*
collectLemmata.groovy: 

Perseus has added unique IDs to each entryFree in LSJ.
This script indexes lemmata to unique ID.

Copyright: Neel Smith.
License:  GPL 3.

Useful for scrolling through or reading the XML source.
To exclude elements from display, modify statusForName so that
it returns Status.INACTIVE for that element.

Notes on the text display format:
-- indentation w/ pound signs represents depth in the document's XML hierarchy
-- <> marks open elements;  attribute key=value paris are given in parens
-- text nodes are indented to the level of the element they belong to
-- </> marks close elements.  Final text in the element is appended after a :

Notes on algorithm:
-- accumulate text from the SAX interface's characters() method
   	      in a StringBuffer
-- maintain a stack with the names of elements that have 
   	    passed by. Push the stack in the startElement() method,
	    and pop it in the endElement() method.

*/


import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
* Type safe classification of how to respond to different
* kinds of content.
*/
enum Status {
  ACTIVE, INACTIVE
}


/**
* Class implementing a SAX parser to display
* all or some of the content of a TEI document's
* /TEI.2/text/body section.
*/
public class DefinitionMapper extends DefaultHandler {
  /*
   * Local file to read source data from
   */              
  String infileName


  /*
   * Stack of Strings with names of each element seen.
   */
  def elementStack = []

  /*
   * Stack of Status objects, with one entry 
   * for each entry in the elementStack
   */
  def statusStack = []

  /*
   * StringBuffer for accumulating content of text nodes
   * read by SAX character() method.
   */
  StringBuffer buf = new StringBuffer()


  /* Boolean flag controls whether or not text display 
   *  should be output.
   */
  boolean display = false

  /* Boolean flag controls whether or not to process
   *  at all the parsed content.
   */
  boolean inContent = false


  int skipCount = 0

  /**
   *  The SAX interface for handling start of an element needs to:
   *  1) flush the accumulated character buffer, and
   *  2) push the new starting element on to the element stack.
   *  In  addition, this is the one point in the SAX stream where
   *  attribute values are visible.
   * 
   *  For parameters:
   * @see org.xml.sax.ContentHandler#startElement(String,String, org.xml.sax.Attributes)
   */
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    // normalize the accumluated buffer:
    def printable = formatBuffer(buf)

    // any special cases where we need to throw flags?
    switch (qName) {
      // process all body content:
    case "body":
    inContent = true; 	
    display = false
    println "Id\tLemma"
    break

    case "entryFree":
    print attributes.getValue("id")
    println "\t" +  attributes.getValue("key")
    break

    default:
    break
    } //switch


    // 1. flush buffer:
    buf = new StringBuffer()
    // number elements before pushing this one:
    def prevCount = elementStack.size()

    // 2. add new element to stack:
    if (inContent) {
      elementStack << qName
      statusStack << statusForName(qName)
      
      // Do the text display:
      def currStatus = statusStack.get(prevCount)	  
      if ((display) && ( currStatus ==  Status.ACTIVE)) {
	// pretty display:
	def count = prevCount

	// Catch any mixed content:
	if (printable != "") {
	  while (count >0) {
	    print "#"
	    count--
	  }
	  println "${printable}"
	} // printable not ""
	
	// Add new element:
	count = prevCount
	while (count > 0) {
	  print "#"
	  count--
	}
	print "<${qName}> ("
	def attCount = attributes.getLength()
	int num = 0
	while (num < attCount) {
	  print attributes.getQName(num) + "=" + attributes.getValue(num) + " "
	  num++;
	}
	println ") "
      } // if Display
    }// inContent
  } // startElement


  /**
   *  The SAX interface for handling the end of an element
   *  must pop the stack of elements, and check for any
   * residual text content in the accumulating buffer.
   * 
   *  For parameters,
   * @see org.xml.sax.ContentHandler#endElement(String,String)
   */
  public void endElement(String uri, String localName, String qName)
  throws SAXException {
    if (inContent) {
      def poppedName = elementStack.pop()
      def poppedStatus = statusStack.pop()
      def count = elementStack.size()
      def printable = formatBuffer(buf)

      // special flags to check:
      switch (qName) {
      case "body":
      inContent = false
      break

      default:
      break
      }

      if ((display) && (poppedStatus == Status.ACTIVE)) {
	while (count > 0) {
	  print "#"
	  count--
	}
	print "</${poppedName}>: "
	if (printable != "") {
	  print "${printable}"
	}
	print "\n"
      }
    } // inContent
    // prepare fresh buffer:
    buf = new StringBuffer()
  } //endElement



  /**
   *  Sax interface for handling characters from text nodes
   *  just accumulates them in the StringBuffer buf.
   *
   * @see org.xml.sax.ContentHandler#characters(char[], int, int)
   */
  public void characters(char[] ch, int start, int length)
  throws SAXException {
    buf.append(ch, start, length);
  }


  /**
   * Formats the contents of a given StringBuffer
   * by dephyphenating it, replacing runs of while space,
   * and, for LSJ, eliminating (frequent!) nodes consisting
   * solely of a comma (typically resulting from lists of tagged items in
   * mixed content elements).
   * @param buffer The buffer to format.
   * @returns The formatted String, or an empty String if the
   * buffer contains only white space and new line characters.
   */
  String formatBuffer(StringBuffer buffer) {
    String str = buffer.toString()
    def noStr = ""
    def dehyphenated = str.replaceAll("[\\-][ ]*\n[ ]*", noStr)
    def noRuns = dehyphenated.replaceAll("[ \t][ \t]+", " ")
    def printable = noRuns.replaceAll("\n",noStr)
    if (printable ==~ "^[ \t\n,]+\$") {
      return ""
    }
    return printable 
  }

  /**
   * Provides a Status classification for a named element.
   * To omit content from the output of this parser, just
   * have this method return Status.INACTIVE for the 
   * element.
   * @param s Name of the element to classify.
   * @returns A Status object for the named element.
   */
  Status statusForName(String s) {
    switch (s) {

    case "body":
    case "entryFree":
    case "sense":
    case "tr":
    //       return Status.ACTIVE
    //       break


    // You can exclude contents from being output
    // by having them return Status.INACTIVE
    case "gramGrp":
    case "cit":
    case "bibl":
    case "date":
    case "itype":
    case "pb":
       
    default:
    return Status.INACTIVE
    break
    }
  }

  /**
   * Constructor for working with input from a local file.
   * @param inputFileName A well-formed XML file.
   * @param outputFileName Name of a file for resulting PDF.
   */
  DefinitionMapper(String inputFileName) {
    this.infileName = inputFileName
  }
  
} //class SaxAnalyzer
///// END OF CLASS SaxAnalyzer ///////////////////////////////////





// Groovy script using the SaxAnalyzer:

if (args.length !=1) {
   println "Usage: groovy mapDefinitions.groovy <FILENAME>"
   System.exit(0)
}

def dm = new DefinitionMapper(args[0])
println "Analyze xml file ${dm.infileName}"

SAXParser parser = SAXParserFactory.newInstance().newSAXParser()
assert parser != null
InputSource is = new InputSource
       (
       new InputStreamReader( new FileInputStream(dm.infileName),"UTF8")
       )      ; assert is != null
parser.parse(is, dm)
println "Finished"
