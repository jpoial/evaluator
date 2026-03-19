
// file: TypeSystem.java

package evaluator;

import java.io.BufferedReader;
import java.io.FileReader;
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
      BufferedReader reader = null;
      try {
         reader = new BufferedReader (new FileReader (fileName));
         String line;
         int lineNo = 0;
         while ((line = reader.readLine()) != null) {
            lineNo++;
            line = stripComment (line);
            if (line.length() == 0) continue;
            String[] tokens = line.split ("\\s+");
            if (tokens.length == 0) continue;
            if ("type".equals (tokens [0])) {
               if (tokens.length < 2)
                  throw new RuntimeException ("Type definition is too short " +
                     "in " + fileName + ":" + String.valueOf (lineNo));
               typeDefs.add (tokens);
            } else {
               if ("rel".equals (tokens [0])) {
                  if ((tokens.length != 4) | (!"<".equals (tokens [2])))
                     throw new RuntimeException ("Malformed relation in " +
                        fileName + ":" + String.valueOf (lineNo));
                  relDefs.add (new String[] {tokens [1], tokens [3]});
               } else {
                  throw new RuntimeException ("Unknown directive " +
                     tokens [0] + " in " + fileName + ":" +
                     String.valueOf (lineNo));
               }
            }
         }
      } catch (IOException e) {
         throw new RuntimeException ("Unable to read type system from " +
            fileName, e);
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (IOException e) {
               // ignore close failure in demo code
            }
         }
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
    * Removes trailing comment and trims the line.
    * @param line source text line
    * @return cleaned line
    */
   static String stripComment (String line) {
      int commentPos = line.indexOf ('#');
      String result = line;
      if (commentPos >= 0) result = line.substring (0, commentPos);
      return result.trim();
   } // end of stripComment()

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
      return result.toString();
   } // end of toString()

} // end of TypeSystem

// end of file
