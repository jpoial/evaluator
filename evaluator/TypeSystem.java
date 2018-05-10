
// file: TypeSystem.java

package evaluator;

import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Implements subtyping relationships.
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class TypeSystem {

   /** type name leads to unique number that is used as an array index */
   Hashtable<String, Integer> typeIndices;

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
      typeIndices.put ("X", Integer.valueOf (0));
      typeIndices.put ("x", Integer.valueOf (0));
      typeIndices.put ("N", Integer.valueOf (1));
      typeIndices.put ("n", Integer.valueOf (1));
      typeIndices.put ("char", Integer.valueOf (2));
      typeIndices.put ("c", Integer.valueOf (2));
      typeIndices.put ("flag", Integer.valueOf (3));
      typeIndices.put ("f", Integer.valueOf (3));
      typeIndices.put ("addr", Integer.valueOf (4));
      typeIndices.put ("a", Integer.valueOf (4));
      typeIndices.put ("c-addr", Integer.valueOf (5));
      typeIndices.put ("ca", Integer.valueOf (5));
      typeIndices.put ("a-addr", Integer.valueOf (6));
      typeIndices.put ("aa", Integer.valueOf (6));
      actSize = 7;
      rel = new int [actSize][actSize];
      for (int i=0; i < actSize; i++) {
         for (int j = 0; j < actSize; j++) {
            rel [i][j] = 0;
         };
      }
      rel[0][1]=2;
      rel[0][3]=2;
      rel[0][4]=2;
      rel[1][2]=2;
      rel[4][5]=2;
      rel[5][6]=2;
      normalize();
   } // end of constructor

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
    * Converts typesystem to string.
    */
   public String toString() {
      StringBuffer result = new StringBuffer ("");
      String nl = System.getProperty ("line.separator");
      Enumeration<String> typenames = typeIndices.keys();
      while (typenames.hasMoreElements()) {
         result.append (nl); // readability
         String t1 = (String)typenames.nextElement();
         Enumeration<String> inner = typeIndices.keys();
         while (inner.hasMoreElements()) {
            String t2 = (String)inner.nextElement();
            int rel = relation (t1, t2);
            switch (rel) {
               case 0: break;
               case 1: result.append (t1 + " < " + t2 + nl); break;
               case 2: result.append (t2 + " < " + t1 + nl); break;
               case 3:
                  if (!t1.equals (t2))
                        result.append (t1 + " = " + t2 + nl);
                  break;
               default: throw new RuntimeException ("no relation!!!");
            }
         }
      }
      return result.toString();
   } // end of toString()

} // end of TypeSystem

// end of file
