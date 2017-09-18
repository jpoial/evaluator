
// file: Evaluator.java

package evaluator;

import java.util.Iterator;

/**
 * The main class of the stack-effect calculus framework.
 * @author Jaanus Poial
 * @version 0.6  26.09.2008
 * @since 1.5
 */
public class Evaluator {

   /**
    * Main method that runs examples only.
    * @param params  command-line parameters (program text)
    */
   public static void main (String[] params) {
      TypeSystem ex1types = new TypeSystem ("ex1types.txt");
      System.out.println ("TypeSystem: " + ex1types.toString());
      SpecSet ex1specs = new SpecSet ("ex1specs.txt", ex1types);
      System.out.println ("SpecSet:" + ex1specs.toString());
      ProgText ex1prog1 = new ProgText (params, ex1specs);
      System.out.println ("Program: " + ex1prog1.toString());
      SpecList ex1list1 = new SpecList (ex1prog1, ex1types, ex1specs);
      // System.out.println ("Sp.sequence: " + ex1list1.toString());
      Spec s1 = (Spec)((Spec)ex1list1.getFirst()).clone();
      Spec s2 = (Spec)((Spec)ex1list1.getLast()).clone();
      Spec resultspec = ex1list1.evaluate (ex1types, ex1specs);
      System.out.println (annotate (ex1prog1, ex1list1, resultspec));
      System.out.println();
      Spec r = s1.glb (s2, ex1types, ex1specs);
      System.out.println ("GLB ( " + s1.toString() + ", " + s2.toString() +
         ") = ");
      if (r==null) {
         System.out.println (" null ");
      }  else {
         System.out.println (r.toString());
      }
      System.out.println();
      s1 = resultspec;
      Spec ide = s1.idemp (ex1types, ex1specs);
      System.out.print ("Idempotent: " + s1.toString() + " -> ");
      if (ide==null) {
         System.out.println (" null");
      } else {
         System.out.println (ide.toString());
      }
      System.out.println();
      ide = s1.piStar (ex1types, ex1specs);
      System.out.print ("Pi-star: " + s1.toString() + " -> ");
      if (ide==null) {
         System.out.println (" null");
      } else {
         System.out.println (ide.toString());
      }

   } // end of main()

   /**
    * Demo that outputs an annotated program (operations and 
    * specifications).
    * @param p  list of operations
    * @param l  list of stack-effects
    * @param s  stack-effect of the whole program
    * @return   mixed text
    */
   public static String annotate (ProgText p, SpecList l, Spec s) {
      StringBuffer result = new StringBuffer ("");
      String nl = System.getProperty ("line.separator");
      if (s != null) {
         result.append ("> " + s.leftSide.toString() + nl);
      } else { result.append ("unknown state " + nl); };
      if (p.size() > 0) {
         Iterator<String> pit = p.iterator();
         Iterator<Spec> lit = l.iterator();
         while (pit.hasNext()) {
            result.append ("    " + (String)pit.next());
            if (lit.hasNext()) {
              result.append (" \t" + ((Spec)lit.next()).toString() + nl);
            } else { throw new RuntimeException ("trouble!!..."); };
         }
      } else {result.append (nl);};
      if (s != null) {
         result.append ("< " + s.rightSide.toString() + nl);
      } else { result.append ("unknown state " + nl); };
      return result.toString();
   } // end of annotate()

} // end of Evaluator

// end of file


