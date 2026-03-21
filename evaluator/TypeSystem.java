
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

   /** original source lines for structured diagnostics */
   LinkedList<String> sourceLines;

   /** actual number of types in use */
   int actSize = 0;

   /** array of relations between types (partial order) */
   int [][] rel;

   /** source span of each direct declared subtype relation */
   SourceSpan [][] relSource;

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
      sourceLines = new LinkedList<String>();
      LinkedList<SourceSpan> typeDefSpans = new LinkedList<SourceSpan>();
      LinkedList<SourceSpan> relDefSpans = new LinkedList<SourceSpan>();
      LinkedList<SourceSpan> scannerDefSpans = new LinkedList<SourceSpan>();
      try {
         TextScanner scanner = TextScanner.fromFile (fileName);
         sourceLines = scanner.sourceLines();
         LinkedList<SourceWord> tokens = null;
         while ((tokens = scanner.nextLineAtoms()) != null) {
            if (tokens.size() == 0) continue;
            SourceWord directive = (SourceWord)tokens.removeFirst();
            SourceSpan lineSpan = SourceSpan.covering (directive.span,
               tokens.size() > 0 ? ((SourceWord)tokens.getLast()).span :
               directive.span);
            if ("type".equals (directive.text)) {
               if (tokens.size() == 0)
                  throw typeError ("types.malformed-type",
                     "Type definition is too short", "", directive.span);
               String[] def = new String [tokens.size() + 1];
               def [0] = directive.text;
               for (int i = 0; i < tokens.size(); i++) {
                  def [i + 1] = ((SourceWord)tokens.get (i)).text;
               }
               typeDefs.add (def);
               typeDefSpans.add (lineSpan);
            } else {
               if ("rel".equals (directive.text)) {
                  if ((tokens.size() != 3) |
                      (!"<".equals (((SourceWord)tokens.get (1)).text)))
                     throw typeError ("types.malformed-relation",
                        "Malformed relation", "", directive.span);
                  relDefs.add (new String[] {((SourceWord)tokens.get (0)).text,
                     ((SourceWord)tokens.get (2)).text});
                  relDefSpans.add (lineSpan);
               } else if ("scanner".equals (directive.text)) {
                  if (tokens.size() != 2)
                     throw typeError ("types.malformed-scanner",
                        "Malformed scanner definition", "", directive.span);
                  scannerDefs.add (new String[] {
                     ((SourceWord)tokens.get (0)).text,
                     ((SourceWord)tokens.get (1)).text});
                  scannerDefSpans.add (lineSpan);
               } else {
                  throw typeError ("types.unknown-directive",
                     "Unknown directive " + directive.text, "",
                     directive.span);
               }
            }
         }
      } catch (ProgramException e) {
         throw e;
      } catch (IOException e) {
         throw new ProgramException (new ProgramDiagnostic (
            "types.read-failed", ProgramDiagnostic.SEVERITY_ERROR,
            "Unable to read type system from " + fileName, "",
            fileName, 0, 0, 0, 0, null, null), e);
      }
      actSize = typeDefs.size();
      rel = new int [actSize][actSize];
      relSource = new SourceSpan [actSize][actSize];
      for (int i=0; i < actSize; i++) {
         for (int j = 0; j < actSize; j++) {
            rel [i][j] = 0;
         };
      }
      int nextIndex = 0;
      Iterator<String[]> typeIt = typeDefs.iterator();
      Iterator<SourceSpan> typeSpanIt = typeDefSpans.iterator();
      while (typeIt.hasNext()) {
         String[] def = (String[])typeIt.next();
         SourceSpan defSpan = typeSpanIt.hasNext() ?
            (SourceSpan)typeSpanIt.next() : null;
         for (int i = 1; i < def.length; i++) {
            if (typeIndices.put (def [i], Integer.valueOf (nextIndex)) != null)
               throw typeError ("types.duplicate-name",
                  "Duplicate type name " + def [i], "", defSpan);
         }
         nextIndex++;
      }
      Iterator<String[]> relIt = relDefs.iterator();
      Iterator<SourceSpan> relSpanIt = relDefSpans.iterator();
      while (relIt.hasNext()) {
         String[] def = (String[])relIt.next();
         SourceSpan relSpan = relSpanIt.hasNext() ?
            (SourceSpan)relSpanIt.next() : null;
         addRelation (def [0], def [1], relSpan);
      }
      Iterator<String[]> scannerIt = scannerDefs.iterator();
      Iterator<SourceSpan> scannerSpanIt = scannerDefSpans.iterator();
      while (scannerIt.hasNext()) {
         String[] def = (String[])scannerIt.next();
         SourceSpan scannerSpan = scannerSpanIt.hasNext() ?
            (SourceSpan)scannerSpanIt.next() : null;
         addScanner (def [0], def [1], scannerSpan);
      }
      validateAcyclicRelations (fileName);
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
    * Rejects strict subtype cycles between distinct canonical types.
    * Aliases must be declared on the same TYPE line rather than via REL.
    * @param fileName source file name for diagnostics
    */
   void validateAcyclicRelations (String fileName) {
      int [] state = new int [actSize];
      int [] path = new int [actSize];
      for (int i = 0; i < actSize; i++) {
         if (state [i] == 0)
            validateAcyclicRelationsFrom (i, state, path, 0, fileName);
      }
   } // end of validateAcyclicRelations()

   /**
    * Depth-first search helper for strict subtype cycle detection.
    * @param node current type index
    * @param state DFS color map
    * @param path active recursion path
    * @param depth current path depth
    * @param fileName source file name for diagnostics
    */
   void validateAcyclicRelationsFrom (int node, int [] state, int [] path,
      int depth, String fileName) {
      state [node] = 1;
      path [depth] = node;
      for (int next = 0; next < actSize; next++) {
         if ((node == next) || (rel [node][next] != 1)) continue;
         if (state [next] == 0) {
            validateAcyclicRelationsFrom (next, state, path, depth + 1,
               fileName);
            continue;
         }
         if (state [next] == 1)
            throw typeError ("types.cyclic-relation",
               "Cyclic subtype relation " +
               relationCycleText (path, depth, next), "",
               relSource [node][next]);
      }
      state [node] = 2;
   } // end of validateAcyclicRelationsFrom()

   /**
    * Formats one detected strict subtype cycle for diagnostics.
    * @param path active recursion path
    * @param depth current path depth
    * @param repeated repeated node that closes the cycle
    * @return readable cycle text
    */
   String relationCycleText (int [] path, int depth, int repeated) {
      int start = 0;
      while ((start <= depth) && (path [start] != repeated)) start++;
      StringBuffer result = new StringBuffer ("");
      for (int i = start; i <= depth; i++) {
         if (result.length() > 0) result.append (" < ");
         result.append (canonicalTypeName (path [i]));
      }
      if (result.length() > 0) result.append (" < ");
      result.append (canonicalTypeName (repeated));
      return result.toString();
   } // end of relationCycleText()

   /**
    * Returns one representative name of the canonical type at the index.
    * @param index canonical type index
    * @return display name
    */
   String canonicalTypeName (int index) {
      if ((index < 0) || (index >= typeDefs.size())) return "?";
      String [] def = (String [])typeDefs.get (index);
      if ((def == null) || (def.length < 2)) return "?";
      return def [1];
   } // end of canonicalTypeName()

   /**
    * Adds one subtype relation to the relation matrix.
    * @param subtype more exact type
    * @param supertype less exact type
    * @param fileName source file name for diagnostics
    */
   void addRelation (String subtype, String supertype, SourceSpan span) {
      if (!containsType (subtype))
         throw typeError ("types.unknown-subtype",
            "Unknown type " + subtype, "", span);
      if (!containsType (supertype))
         throw typeError ("types.unknown-supertype",
            "Unknown type " + supertype, "", span);
      int i1 = ((Integer)typeIndices.get (subtype)).intValue();
      int i2 = ((Integer)typeIndices.get (supertype)).intValue();
      if (i1 != i2) {
         rel [i1][i2] = 1;
         relSource [i1][i2] = span;
      }
   } // end of addRelation()

   /**
    * Adds one named scanner delimiter.
    * @param name scanner name
    * @param delimiter terminating delimiter
    * @param fileName source file name for diagnostics
    */
   void addScanner (String name, String delimiter, SourceSpan span) {
      String key = canonicalScannerName (name);
      if ((key == null) | (key.length() == 0))
         throw typeError ("types.empty-scanner-name",
            "Empty scanner name", "", span);
      if ((delimiter == null) | (delimiter.length() == 0))
         throw typeError ("types.empty-scanner-delimiter",
            "Empty scanner delimiter for " + name, "", span);
      if (scanners.put (key, delimiter) != null)
         throw typeError ("types.duplicate-scanner",
            "Duplicate scanner name " + name, "", span);
   } // end of addScanner()

   /**
    * Builds one structured diagnostic for a type-system source file.
    * @param code diagnostic code
    * @param message summary message
    * @param reason optional detail
    * @param span source span
    * @return diagnostic exception
    */
   ProgramException typeError (String code, String message, String reason,
      SourceSpan span) {
      return new ProgramException (new ProgramDiagnostic (code,
         ProgramDiagnostic.SEVERITY_ERROR, message, reason, span,
         sourceLineText (span), markerLineText (span)));
   } // end of typeError()

   /**
    * Returns the raw source line for one span.
    * @param span source span
    * @return source line or null
    */
   String sourceLineText (SourceSpan span) {
      if ((span == null) || !span.hasLocation()) return null;
      if ((span.startLine < 1) || (span.startLine > sourceLines.size()))
         return null;
      return (String)sourceLines.get (span.startLine - 1);
   } // end of sourceLineText()

   /**
    * Builds one caret marker line for the given span.
    * @param span source span
    * @return marker line or null
    */
   String markerLineText (SourceSpan span) {
      String line = sourceLineText (span);
      if (line == null) return null;
      StringBuffer result = new StringBuffer ("");
      int limit = Math.max (0, span.startColumn - 1);
      for (int i = 0; (i < limit) & (i < line.length()); i++) {
         if (line.charAt (i) == '\t') {
            result.append ('\t');
         } else {
            result.append (' ');
         }
      }
      int width = 1;
      if (span.startLine == span.endLine)
         width = Math.max (1, span.endColumn - span.startColumn + 1);
      for (int i = 0; i < width; i++) result.append ('^');
      return result.toString();
   } // end of markerLineText()

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
