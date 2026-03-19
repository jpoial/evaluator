// file: ProgramDiagnostic.java

package evaluator;

/**
 * Structured program diagnostic that can be rendered for CLI output
 * or consumed directly by editor integrations.
 * @author Codex
 * @version 0.1
 * @since 1.5
 */
public class ProgramDiagnostic {

   /** severity label for hard failures */
   public static final String SEVERITY_ERROR = "error";

   public final String code;
   public final String severity;
   public final String message;
   public final String reason;
   public final String sourceName;
   public final int startLine;
   public final int startColumn;
   public final int endLine;
   public final int endColumn;
   public final String sourceLine;
   public final String markerLine;

   public ProgramDiagnostic (String c, String sev, String msg, String why,
      String source, int sLine, int sCol, int eLine, int eCol,
      String lineText, String markerText) {
      code = c;
      severity = sev;
      message = (msg == null) ? "" : msg;
      reason = (why == null) ? "" : why;
      sourceName = source;
      startLine = sLine;
      startColumn = sCol;
      endLine = eLine;
      endColumn = eCol;
      sourceLine = lineText;
      markerLine = markerText;
   } // end of constructor

   public ProgramDiagnostic (String c, String sev, String msg, String why,
      SourceSpan span, String lineText, String markerText) {
      this (c, sev, msg, why, spanSource (span), spanStartLine (span),
         spanStartColumn (span), spanEndLine (span), spanEndColumn (span),
         lineText, markerText);
   } // end of constructor

   /**
    * Tells whether this diagnostic has an exact source location.
    * @return true if location fields are present
    */
   public boolean hasLocation() {
      return (sourceName != null) & (startLine > 0) & (startColumn > 0);
   } // end of hasLocation()

   /**
    * Tells whether a source excerpt is attached.
    * @return true if source line and marker are present
    */
   public boolean hasSourceContext() {
      return (sourceLine != null) & (markerLine != null) &
         (sourceLine.length() > 0) & (markerLine.length() > 0);
   } // end of hasSourceContext()

   /**
    * Formats the start location.
    * @return source location text
    */
   public String startText() {
      if (hasLocation())
         return sourceName + ":" + String.valueOf (startLine) + ":" +
            String.valueOf (startColumn);
      if (sourceName != null) return sourceName;
      return "";
   } // end of startText()

   static String spanSource (SourceSpan span) {
      if (span == null) return null;
      return span.sourceName;
   } // end of spanSource()

   static int spanStartLine (SourceSpan span) {
      if (span == null) return 0;
      return span.startLine;
   } // end of spanStartLine()

   static int spanStartColumn (SourceSpan span) {
      if (span == null) return 0;
      return span.startColumn;
   } // end of spanStartColumn()

   static int spanEndLine (SourceSpan span) {
      if (span == null) return 0;
      return span.endLine;
   } // end of spanEndLine()

   static int spanEndColumn (SourceSpan span) {
      if (span == null) return 0;
      return span.endColumn;
   } // end of spanEndColumn()

} // end of ProgramDiagnostic

// end of file
