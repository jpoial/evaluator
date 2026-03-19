// file: SourceSpan.java

package evaluator;

/**
 * Source span for one parsed token or structure.
 * @author Codex
 * @version 0.1
 * @since 1.5
 */
public class SourceSpan {

   String sourceName;
   int startLine;
   int startColumn;
   int endLine;
   int endColumn;

   SourceSpan (String source, int sLine, int sCol, int eLine, int eCol) {
      sourceName = source;
      startLine = sLine;
      startColumn = sCol;
      endLine = eLine;
      endColumn = eCol;
   } // end of constructor

   boolean hasLocation() {
      return (startLine > 0) & (startColumn > 0);
   } // end of hasLocation()

   String startText() {
      if (hasLocation())
         return sourceName + ":" + String.valueOf (startLine) + ":" +
            String.valueOf (startColumn);
      return sourceName;
   } // end of startText()

   static SourceSpan covering (SourceSpan start, SourceSpan end) {
      if (start == null) return end;
      if (end == null) return start;
      return new SourceSpan (start.sourceName, start.startLine,
         start.startColumn, end.endLine, end.endColumn);
   } // end of covering()

} // end of SourceSpan

// end of file
