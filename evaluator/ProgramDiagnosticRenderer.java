// file: ProgramDiagnosticRenderer.java

package evaluator;

/**
 * Renders structured program diagnostics for terminal output.
 * @author Codex
 * @version 0.1
 * @since 1.5
 */
public class ProgramDiagnosticRenderer {

   /**
    * Formats a diagnostic for CLI output.
    * @param diagnostic structured diagnostic
    * @return rendered text
    */
   public static String format (ProgramDiagnostic diagnostic) {
      if (diagnostic == null) return "Unknown program error.";
      StringBuffer result = new StringBuffer ("");
      result.append (diagnostic.message);
      if (diagnostic.hasLocation()) {
         result.append (" at ");
         result.append (diagnostic.startText());
      }
      if ((diagnostic.reason != null) & (diagnostic.reason.length() > 0)) {
         result.append (": ");
         result.append (diagnostic.reason);
      }
      ensureTerminalPunctuation (result);
      if (diagnostic.hasSourceContext()) {
         String nl = System.getProperty ("line.separator");
         result.append (nl);
         if (diagnostic.hasLocation()) {
            result.append ("    --> ");
            result.append (diagnostic.startText());
            result.append (nl);
         }
         result.append ("    ");
         result.append (diagnostic.sourceLine);
         result.append (nl);
         result.append ("    ");
         result.append (diagnostic.markerLine);
      }
      return result.toString();
   } // end of format()

   /**
    * Makes sure rendered one-line summaries end in a terminal punctuation.
    * @param text rendered diagnostic buffer
    */
   static void ensureTerminalPunctuation (StringBuffer text) {
      if (text.length() == 0) return;
      char last = text.charAt (text.length() - 1);
      if ((last != '.') & (last != '!') & (last != '?'))
         text.append ('.');
   } // end of ensureTerminalPunctuation()

} // end of ProgramDiagnosticRenderer

// end of file
