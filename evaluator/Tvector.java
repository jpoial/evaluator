
// file: Tvector.java

package evaluator;

import java.util.Iterator;
import java.util.Vector;

/**
 * List of types.
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class Tvector extends Vector<TypeSymbol> {
    
    static final long serialVersionUID = 0xaabbcc;

   /**
    * Clones this list.
    */
   public Object clone () {
      Tvector result = new Tvector();
      Iterator<TypeSymbol> it = iterator();
      while (it.hasNext()) {
         TypeSymbol s = (TypeSymbol)it.next();
         result.add ((TypeSymbol)s.clone());
      }
      return result;
   } // end of clone()

   /**
    * Shows the type list as string.
    */
   public String toString() {
      StringBuffer result = new StringBuffer ("");
      Iterator<TypeSymbol> it = iterator();
      while (it.hasNext()) {
         result.append (it.next().toString());
      }
      return result.toString();
   } // end of toString()

   /**
    * Substitutes clone of given wildcard symbol to another one in this 
    * list.
    * @param oldsym symbol to find
    * @param newsym replacement symbol
    * @return number of replacements made
    */
   int substitute (TypeSymbol oldsym, TypeSymbol newsym) {
      int count = 0;
      int len = size();
      for (int i = 0; i < len; i++) {
         if (((TypeSymbol)get (i)).equals (oldsym)) {
            remove (i);
            add (i, (TypeSymbol)newsym.clone());
            count++;
         } else {};
      }
      return count;
   } // end of substitute

} // end of Tvector

// end of file
