
// file: TypeSymbol.java

package evaluator;

/**
 * Stack type symbol coupled together with wildcard index.
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class TypeSymbol {

   /** type name */
   String ftype;

   /** position index for wildcards */
   int position = 0;

   TypeSymbol (String s, int i) {
      ftype = s;
      position = i;
   } // end of constructor

   /**
    * Clones this symbol.
    */
   public Object clone() {
      return new TypeSymbol (ftype, position);
   } // end of clone()

   /**
    * Converts stack type symbol to string.
    * @return type
    */
   public String toString() {
      String result;
      if (position > 0) {
         result = ftype + "[" + String.valueOf (position) + "] ";
      } else { result = ftype + " "; }
      return result;
   } // end of toString()

   /**
    * Compares two symbols for equality.
    * @param o second symbol
    * @return true if this is equal to o
    */
   public boolean equals (Object o) {
      return ( ftype.equals (((TypeSymbol)o).ftype) &
         (position == ((TypeSymbol)o).position) );
   } // end of equals()

   /**
    * Produces hashcode for hashtables.
    * @return hashcode
    */
   public int hashCode() {
      return ftype.hashCode()+position;
   } // end of hashCode()

} // end of TypeSymbol

// end of file
