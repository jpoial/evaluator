
// file: SpecSet.java

package evaluator;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Set of specifications hashed using operation names (words).
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class SpecSet extends Hashtable<String, Spec> {
    
    static final long serialVersionUID = 0xaabbcc;

   /**
    * Reads specifications from the file using given type system.
    * @param fileName  local file name
    * @param ts  type system used in specifications
    */
   SpecSet (String fileName, TypeSystem ts) {
      Tvector ls = new Tvector();
      Tvector rs = new Tvector();
      @SuppressWarnings("unused")
      Spec old = null;
      ls.add (new TypeSymbol ("X", 2));
      ls.add (new TypeSymbol ("X", 1));
      rs.add (new TypeSymbol ("X", 2));
      rs.add (new TypeSymbol ("X", 1));
      rs.add (new TypeSymbol ("X", 2));
      old = (Spec)put ("OVER", new Spec(ls, rs, ts, "", 2));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("X", 2));
      ls.add (new TypeSymbol ("X", 1));
      rs.add (new TypeSymbol ("X", 1));
      rs.add (new TypeSymbol ("X", 2));
      old = (Spec)put ("SWAP", new Spec(ls, rs, ts, "", 2));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("X", 1));
      rs.add (new TypeSymbol ("X", 1));
      rs.add (new TypeSymbol ("X", 1));
      old = (Spec)put ("DUP", new Spec(ls, rs, ts, "", 1));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("X", 0));
      old = (Spec)put ("DROP", new Spec(ls, rs, ts, "", 0));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("X", 3));
      ls.add (new TypeSymbol ("X", 2));
      ls.add (new TypeSymbol ("X", 1));
      rs.add (new TypeSymbol ("X", 2));
      rs.add (new TypeSymbol ("X", 1));
      rs.add (new TypeSymbol ("X", 3));
      old = (Spec)put ("ROT", new Spec(ls, rs, ts, "", 3));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("X", 1));
      ls.add (new TypeSymbol ("X", 1));
      rs.add (new TypeSymbol ("X", 1));
      old = (Spec)put ("PLUS", new Spec(ls, rs, ts, "", 1));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("X", 0));
      ls.add (new TypeSymbol ("X", 0));
      rs.add (new TypeSymbol ("X", 0));
      old = (Spec)put ("+", new Spec(ls, rs, ts, "", 0));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("n", 0));
      rs.add (new TypeSymbol ("flag", 0));
      old = (Spec)put ("0=", new Spec(ls, rs, ts, "", 0));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("a-addr", 0));
      rs.add (new TypeSymbol ("X", 0));
      old = (Spec)put ("@", new Spec(ls, rs, ts, "", 0));
      ls = new Tvector();
      rs = new Tvector();
      rs.add (new TypeSymbol ("a-addr", 0));
      old = (Spec)put ("DP", new Spec(ls, rs, ts, "", 0));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("c-addr", 0));
      rs.add (new TypeSymbol ("char", 0));
      old = (Spec)put ("C@", new Spec(ls, rs, ts, "", 0));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("X", 0));
      ls.add (new TypeSymbol ("a-addr", 0));
      old = (Spec)put ("!", new Spec(ls, rs, ts, "", 0));
      ls = new Tvector();
      rs = new Tvector();
      ls.add (new TypeSymbol ("char", 0));
      ls.add (new TypeSymbol ("c-addr", 0));
      old = (Spec)put ("C!", new Spec(ls, rs, ts, "", 0));
   } // end of constructor

   /**
    * Converts set to string.
    */
   public String toString() {
      StringBuffer result = new StringBuffer ("");
      String nl = System.getProperty ("line.separator");
      Enumeration <String> words = keys();
      while (words.hasMoreElements()) {
         String word = (String)words.nextElement();
         result.append (nl + word + "\t" + get (word).toString());
      }
      result.append (nl);
      return result.toString();
   } // end of toString()

} // end of SpecSet

// end of file
