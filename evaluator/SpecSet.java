
// file: SpecSet.java

package evaluator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * Set of specifications hashed using operation names (words).
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class SpecSet extends Hashtable<String, Spec> {
    
    static final long serialVersionUID = 0xaabbcc;

   SpecSet() {
      super();
   } // end of constructor

   /**
    * Reads specifications from the file using given type system.
    * @param fileName  local file name
    * @param ts  type system used in specifications
    */
   SpecSet (String fileName, TypeSystem ts) {
      this();
      load (fileName, ts);
   } // end of constructor

   /**
    * Adds or replaces one specification in a safe way.
    * @param word Forth word
    * @param spec stack effect for the word
    * @return previous specification or null
    */
   public synchronized Spec put (String word, Spec spec) {
      if (word == null)
         throw new RuntimeException ("Word name must not be null.");
      String key = word.trim();
      if (key.length() == 0)
         throw new RuntimeException ("Word name must not be empty.");
      if (spec == null)
         throw new RuntimeException ("Specification for " + key +
            " must not be null.");
      return (Spec)super.put (key, (Spec)spec.clone());
   } // end of put()

   /**
    * Defines one specification using stack effect text.
    * @param word Forth word
    * @param specText stack effect text with or without parentheses
    * @param ts type system used for validation
    * @return previous specification or null
    */
   Spec define (String word, String specText, TypeSystem ts) {
      String body = specText.trim();
      if (body.startsWith ("(") && body.endsWith (")")) {
         body = body.substring (1, body.length()-1).trim();
      }
      return put (word, parseSpec (body, ts, "<memory>", 0));
   } // end of define()

   /**
    * Loads more specifications from a file into this set.
    * @param fileName local file name
    * @param ts type system used for validation
    */
   void load (String fileName, TypeSystem ts) {
      BufferedReader reader = null;
      try {
         reader = new BufferedReader (new FileReader (fileName));
         String line;
         int lineNo = 0;
         while ((line = reader.readLine()) != null) {
            lineNo++;
            line = TypeSystem.stripComment (line);
            if (line.length() == 0) continue;
            int p1 = line.indexOf ('(');
            int p2 = line.lastIndexOf (')');
            if ((p1 <= 0) | (p2 <= p1))
               throw new RuntimeException ("Malformed specification in " +
                  fileName + ":" + String.valueOf (lineNo));
            String word = line.substring (0, p1).trim();
            if (word.length() == 0)
               throw new RuntimeException ("Missing word name in " +
                  fileName + ":" + String.valueOf (lineNo));
            if (p2 != line.length()-1)
               throw new RuntimeException ("Unexpected trailing text in " +
                  fileName + ":" + String.valueOf (lineNo));
            String body = line.substring (p1+1, p2).trim();
            if (containsKey (word))
               throw new RuntimeException ("Duplicate specification for " +
                  word + " in " + fileName + ":" + String.valueOf (lineNo));
            put (word, parseSpec (body, ts, fileName, lineNo));
         }
      } catch (IOException e) {
         throw new RuntimeException ("Unable to read specification set from "
            + fileName, e);
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (IOException e) {
               // ignore close failure in demo code
            }
         }
      }
   } // end of load()

   /**
    * Saves this set to a text file in a stable order.
    * @param fileName local file name
    */
   void save (String fileName) {
      BufferedWriter writer = null;
      String nl = System.getProperty ("line.separator");
      try {
         writer = new BufferedWriter (new FileWriter (fileName));
         Iterator<String> it = sortedWords().iterator();
         while (it.hasNext()) {
            String word = (String)it.next();
            writer.write (word + " " + ((Spec)get (word)).toString().trim());
            writer.write (nl);
         }
      } catch (IOException e) {
         throw new RuntimeException ("Unable to save specification set to " +
            fileName, e);
      } finally {
         if (writer != null) {
            try {
               writer.close();
            } catch (IOException e) {
               // ignore close failure in demo code
            }
         }
      }
   } // end of save()

   /**
    * Parses one stack effect body from text between parentheses.
    * @param body effect text
    * @param ts typesystem to validate against
    * @param fileName source file name for diagnostics
    * @param lineNo source line number for diagnostics
    * @return parsed specification
    */
   static Spec parseSpec (String body, TypeSystem ts, String fileName,
      int lineNo) {
      int arrowPos = body.indexOf ("--");
      if (arrowPos < 0)
         throw new RuntimeException ("Missing -- in " + fileName + ":" +
            String.valueOf (lineNo));
      Tvector left = parseTypeList (body.substring (0, arrowPos).trim(),
         ts, fileName, lineNo);
      Tvector right = parseTypeList (body.substring (arrowPos + 2).trim(),
         ts, fileName, lineNo);
      Spec result = new Spec (left, right, ts, "", 0);
      result.maxPos();
      return result;
   } // end of parseSpec()

   /**
    * Parses one stack-state side of a specification.
    * @param text side of the specification
    * @param ts typesystem to validate against
    * @param fileName source file name for diagnostics
    * @param lineNo source line number for diagnostics
    * @return parsed list of type symbols
    */
   static Tvector parseTypeList (String text, TypeSystem ts, String fileName,
      int lineNo) {
      Tvector result = new Tvector();
      if (text.length() == 0) return result;
      String[] tokens = text.split ("\\s+");
      for (int i = 0; i < tokens.length; i++) {
         result.add (parseTypeSymbol (tokens [i], ts, fileName, lineNo));
      }
      return result;
   } // end of parseTypeList()

   /**
    * Parses one type symbol of the form type or type[index].
    * @param text symbol text
    * @param ts typesystem to validate against
    * @param fileName source file name for diagnostics
    * @param lineNo source line number for diagnostics
    * @return parsed symbol
    */
   static TypeSymbol parseTypeSymbol (String text, TypeSystem ts,
      String fileName, int lineNo) {
      String typeName = text;
      int position = 0;
      int p1 = text.indexOf ('[');
      if (p1 >= 0) {
         int p2 = text.indexOf (']', p1 + 1);
         if ((p2 != text.length()-1) | (p2 <= p1 + 1))
            throw new RuntimeException ("Malformed type symbol " + text +
               " in " + fileName + ":" + String.valueOf (lineNo));
         typeName = text.substring (0, p1);
         try {
            position = Integer.parseInt (text.substring (p1 + 1, p2));
         } catch (NumberFormatException e) {
            throw new RuntimeException ("Malformed wildcard index in " + text +
               " in " + fileName + ":" + String.valueOf (lineNo), e);
         }
         if (position < 0)
            throw new RuntimeException ("Negative wildcard index in " + text +
               " in " + fileName + ":" + String.valueOf (lineNo));
      }
      if (!ts.containsType (typeName))
         throw new RuntimeException ("Unknown type " + typeName + " in " +
            fileName + ":" + String.valueOf (lineNo));
      return new TypeSymbol (typeName, position, position > 0);
   } // end of parseTypeSymbol()

   /**
    * Converts set to string.
    */
   public String toString() {
      StringBuffer result = new StringBuffer ("");
      String nl = System.getProperty ("line.separator");
      Iterator<String> words = sortedWords().iterator();
      while (words.hasNext()) {
         String word = (String)words.next();
         result.append (nl + word + "\t" + get (word).toString());
      }
      result.append (nl);
      return result.toString();
   } // end of toString()

   /**
    * Returns word names in stable sorted order.
    * @return sorted set of keys
    */
   TreeSet<String> sortedWords() {
      return new TreeSet<String> (keySet());
   } // end of sortedWords()
	
} // end of SpecSet

// end of file
