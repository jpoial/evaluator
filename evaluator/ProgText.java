
// file: ProgText.java

package evaluator;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Inner representation for the program that is analysed.
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class ProgText extends LinkedList<String> {

    static final long serialVersionUID = 0xaabbcc;
   /**
    * Reads program text from the file and parses it using given
    * specifications.
    * @param fileName  local file name (program text)
    * @param ss  set of specifications to use
    */
   ProgText (String fileName, SpecSet ss) {
      add ("@");
      add ("OVER");
      add ("@");
   } // end of constructor

   /**
    * Creates inner representation from the given array of strings
    * using given specifications.
    * @param text  program text
    * @param ss  set of specifications to use
    */
   ProgText (String[] text, SpecSet ss) {
      for (int i=0; i<text.length; i++) {
         add (text [i]);
      }
   } // end of constructor

   /**
    * Converts inner representation back to string.
    */
   public String toString() {
      StringBuffer result = new StringBuffer ("");
      Iterator<String> it = iterator();
      while (it.hasNext()) {
         result.append (it.next().toString() + " ");
      }
      return result.toString();
   } // end of toString()

} // end of ProgText

// end of file
