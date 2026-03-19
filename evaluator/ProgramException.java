// file: ProgramException.java

package evaluator;

/**
 * User-facing program exception carrying a structured diagnostic.
 * @author Codex
 * @version 0.1
 * @since 1.5
 */
public class ProgramException extends RuntimeException {

   private final ProgramDiagnostic diagnostic;

   ProgramException (ProgramDiagnostic d) {
      super (summaryText (d));
      diagnostic = d;
   } // end of constructor

   ProgramException (ProgramDiagnostic d, Throwable cause) {
      super (summaryText (d), cause);
      diagnostic = d;
   } // end of constructor

   /**
    * Returns the structured diagnostic payload.
    * @return diagnostic object
    */
   public ProgramDiagnostic diagnostic() {
      return diagnostic;
   } // end of diagnostic()

   /**
    * Formats the diagnostic as a message.
    * @return rendered diagnostic text
    */
   public String getMessage() {
      return ProgramDiagnosticRenderer.format (diagnostic);
   } // end of getMessage()

   static String summaryText (ProgramDiagnostic d) {
      if (d == null) return "Unknown program error";
      return d.message;
   } // end of summaryText()

} // end of ProgramException

// end of file
