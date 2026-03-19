
// file: TypeSystem.java

package evaluator;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Implements subtyping relationships.
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class TypeSystem {

   /** type name leads to unique number that is used as an array index */
   Hashtable<String, Integer> typeIndices;

   /** original type declarations in file order */
   LinkedList<String[]> typeDefs;

   /** original relation declarations in file order */
   LinkedList<String[]> relDefs;

   /** named scanner delimiters in file order */
   LinkedList<String[]> scannerDefs;

   /** scanner name to delimiter mapping */
   Hashtable<String, String> scanners;

   /** actual number of types in use */
   int actSize = 0;

   /** array of relations between types (partial order) */
   int [][] rel;

   /**
    * Reads the types and subtyping information from the file.
    * @param fileName  local file name
    */
   TypeSystem (String fileName) {
      typeIndices = new Hashtable<String, Integer>();
      typeDefs = new LinkedList<String[]>();
      relDefs = new LinkedList<String[]>();
      scannerDefs = new LinkedList<String[]>();
      scanners = new Hashtable<String, String>();
      try {
         TextScanner scanner = TextScanner.fromFile (fileName);
         LinkedList<SourceWord> tokens = null;
         while ((tokens = scanner.nextLineAtoms()) != null) {
            if (tokens.size() == 0) continue;
            SourceWord directive = (SourceWord)tokens.removeFirst();
            if ("type".equals (directive.text)) {
               if (tokens.size() == 0)
                  throw new RuntimeException ("Type definition is too short " +
                     "in " + directive.span.startText());
               String[] def = new String [tokens.size() + 1];
               def [0] = directive.text;
               for (int i = 0; i < tokens.size(); i++) {
                  def [i + 1] = ((SourceWord)tokens.get (i)).text;
               }
               typeDefs.add (def);
            } else {
               if ("rel".equals (directive.text)) {
                  if ((tokens.size() != 3) |
                      (!"<".equals (((SourceWord)tokens.get (1)).text)))
                     throw new RuntimeException ("Malformed relation in " +
                        directive.span.startText());
                  relDefs.add (new String[] {((SourceWord)tokens.get (0)).text,
                     ((SourceWord)tokens.get (2)).text});
               } else if ("scanner".equals (directive.text)) {
                  if (tokens.size() != 2)
                     throw new RuntimeException ("Malformed scanner " +
                        "definition in " + directive.span.startText());
                  scannerDefs.add (new String[] {
                     ((SourceWord)tokens.get (0)).text,
                     ((SourceWord)tokens.get (1)).text});
               } else {
                  throw new RuntimeException ("Unknown directive " +
                     directive.text + " in " + directive.span.startText());
               }
            }
         }
      } catch (IOException e) {
         throw new RuntimeException ("Unable to read type system from " +
            fileName, e);
      }
      actSize = typeDefs.size();
      rel = new int [actSize][actSize];
      for (int i=0; i < actSize; i++) {
         for (int j = 0; j < actSize; j++) {
            rel [i][j] = 0;
         };
      }
      int nextIndex = 0;
      Iterator<String[]> typeIt = typeDefs.iterator();
      while (typeIt.hasNext()) {
         String[] def = (String[])typeIt.next();
         for (int i = 1; i < def.length; i++) {
            if (typeIndices.put (def [i], Integer.valueOf (nextIndex)) != null)
               throw new RuntimeException ("Duplicate type name " + def [i] +
                  " in " + fileName);
         }
         nextIndex++;
      }
      Iterator<String[]> relIt = relDefs.iterator();
      while (relIt.hasNext()) {
         String[] def = (String[])relIt.next();
         addRelation (def [0], def [1], fileName);
      }
      Iterator<String[]> scannerIt = scannerDefs.iterator();
      while (scannerIt.hasNext()) {
         String[] def = (String[])scannerIt.next();
         addScanner (def [0], def [1], fileName);
      }
      normalize();
   } // end of constructor

   /**
    * Tells whether a given type name is present in this type system.
    * @param t  type name
    * @return true if t is known
    */
   boolean containsType (String t) {
      return typeIndices.get (t) != null;
   } // end of containsType()

   /**
    * Tells whether a named scanner delimiter is present.
    * @param name scanner name
    * @return true if name is known
    */
   boolean containsScanner (String name) {
      return scanners.get (canonicalScannerName (name)) != null;
   } // end of containsScanner()

   /**
    * Returns the delimiter string of a named scanner.
    * @param name scanner name
    * @return delimiter string or null
    */
   String scannerDelimiter (String name) {
      return (String)scanners.get (canonicalScannerName (name));
   } // end of scannerDelimiter()

   /**
    * Returns relationship between given (sub)types.
    * @param t1 first typename
    * @param t2 second typename
    * @return 0 - independent, 1 - subtype, 2 - supertype, 3 - synonym
    */
   public int relation (String t1, String t2) {
      if ((t1 == null) | (t2 == null)) return 0;
      if ((typeIndices.get (t1) == null) | (typeIndices.get (t2) == null))
         return 0;
      int i1 = ((Integer)typeIndices.get (t1)).intValue();
      int i2 = ((Integer)typeIndices.get (t2)).intValue();
      return rel [i1][i2];
   } // end of relation()

   /**
    * Normalizes relations.
    */
   void normalize() {
      int i, j, k;
      for (i=0; i<actSize; i++) {
         for (j=0; j<actSize; j++) {
            if (rel[i][j]==0) {
               if (i==j) rel[i][j] = 3;
               if (rel[j][i]==1) rel[i][j]=2;
               if (rel[j][i]==2) rel[i][j]=1;
               if (rel[j][i]==3) rel[i][j]=3;
            } else {
               if (rel[i][j]==1) {
                  if (rel[j][i]==0) rel[j][i]=2;
                  if (rel[j][i]==1) {rel[i][j]=3; rel[j][i]=3;};
                  if (rel[j][i]==3) rel[i][j]=3;
               } else {
                  if (rel[i][j]==2) {
                     if (rel[j][i]==0) rel[j][i]=1;
                     if (rel[j][i]==2) {rel[i][j]=3; rel[j][i]=3;};
                     if (rel[j][i]==3) rel[i][j]=3;
                  } else {
                     if (rel[i][j]==3) {
                        rel [j][i]=3;
                     } else throw new RuntimeException ("wrong index!!!");
                  }
               }
            }
         }
      }
      for (k=0; k<actSize; k++) // Floyd-Warshall
         for (i=0; i<actSize; i++)
            for (j=0; j<actSize; j++)
               if ((rel[i][k]>0)&(rel[i][k]<4))
                  if (rel[i][k]==rel[k][j]) rel[i][j]=rel[i][k];
   } // end of normalize()

   /**
    * Adds one subtype relation to the relation matrix.
    * @param subtype more exact type
    * @param supertype less exact type
    * @param fileName source file name for diagnostics
    */
   void addRelation (String subtype, String supertype, String fileName) {
      if (!containsType (subtype))
         throw new RuntimeException ("Unknown type " + subtype + " in " +
            fileName);
      if (!containsType (supertype))
         throw new RuntimeException ("Unknown type " + supertype + " in " +
            fileName);
      int i1 = ((Integer)typeIndices.get (subtype)).intValue();
      int i2 = ((Integer)typeIndices.get (supertype)).intValue();
      if (i1 != i2) rel [i1][i2] = 1;
   } // end of addRelation()

   /**
    * Adds one named scanner delimiter.
    * @param name scanner name
    * @param delimiter terminating delimiter
    * @param fileName source file name for diagnostics
    */
   void addScanner (String name, String delimiter, String fileName) {
      String key = canonicalScannerName (name);
      if ((key == null) | (key.length() == 0))
         throw new RuntimeException ("Empty scanner name in " + fileName);
      if ((delimiter == null) | (delimiter.length() == 0))
         throw new RuntimeException ("Empty scanner delimiter for " + name +
            " in " + fileName);
      if (scanners.put (key, delimiter) != null)
         throw new RuntimeException ("Duplicate scanner name " + name +
            " in " + fileName);
   } // end of addScanner()

   /**
    * Canonicalizes one scanner name for case-insensitive lookup.
    * @param name original scanner name
    * @return canonical uppercase name
    */
   static String canonicalScannerName (String name) {
      return SpecSet.canonicalWord (name);
   } // end of canonicalScannerName()

   /**
    * Converts typesystem to string.
    */
   public String toString() {
      StringBuffer result = new StringBuffer ("");
      String nl = System.getProperty ("line.separator");
      Iterator<String[]> typeIt = typeDefs.iterator();
      while (typeIt.hasNext()) {
         String[] def = (String[])typeIt.next();
         result.append (nl);
         for (int i = 0; i < def.length; i++) {
            if (i > 0) result.append (" ");
            result.append (def [i]);
         }
      }
      Iterator<String[]> relIt = relDefs.iterator();
      while (relIt.hasNext()) {
         String[] def = (String[])relIt.next();
         result.append (nl + "rel " + def [0] + " < " + def [1]);
      }
      Iterator<String[]> scannerIt = scannerDefs.iterator();
      while (scannerIt.hasNext()) {
         String[] def = (String[])scannerIt.next();
         result.append (nl + "scanner " + def [0] + " " +
            TextScanner.quotedText (def [1]));
      }
      return result.toString();
   } // end of toString()

} // end of TypeSystem

// end of file
