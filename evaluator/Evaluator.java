
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

   static class RunConfig {
      String typesFile;
      String specsFile;
      String progFile;
      String[] programParams;
   } // end of RunConfig

   /**
    * Main method that runs one evaluator invocation.
    * @param params  command-line parameters (program text)
   */
   public static void main (String[] params) {
      try {
         if (!run (params)) System.exit (1);
      } catch (ProgramException e) {
         System.err.println ("Error: " +
            ProgramDiagnosticRenderer.format (e.diagnostic()));
         System.exit (1);
      } catch (RuntimeException e) {
         System.err.println ("Error: " + e.getMessage());
         System.exit (1);
      }
   } // end of main()

   /**
    * Executes one evaluator run.
    * @param params command-line parameters
   */
   static boolean run (String[] params) {
      RunConfig cfg = parseArgs (params);
      System.out.println ("Types file: " + cfg.typesFile);
      System.out.println ("Specs file: " + cfg.specsFile);
      if (cfg.programParams.length > 0) {
         System.out.println ("Program source: command line");
      } else {
         System.out.println ("Program file: " + cfg.progFile);
      }
      TypeSystem typeSystem = new TypeSystem (cfg.typesFile);
      // System.out.println ("TypeSystem: " + typeSystem.toString());
      SpecSet specSet = new SpecSet (cfg.specsFile, typeSystem);
      ProgText program;
      if (cfg.programParams.length > 0) {
         program = new ProgText (cfg.programParams, typeSystem, specSet);
      } else {
         program = new ProgText (cfg.progFile, typeSystem, specSet);
      }
      if (program.hasDiagnostics()) {
         printDiagnostics (program);
         return false;
      }
      // System.out.println ("SpecSet:" + specSet.toString());
      System.out.println ("Program text:");
      System.out.println (program.sourceText());
      System.out.println ("Program: " + program.toString());
      SpecList specList = new SpecList (program, typeSystem, specSet);
      // System.out.println ("Sp.sequence: " + specList.toString());
      Spec resultspec = specList.evaluate (typeSystem, specSet);
      if (resultspec == null)
         throw specList.typeClash ("linear part of the top-level program",
            program);
      System.out.println (annotate (program, specList, resultspec));
      return true;

   } // end of run()

   /**
    * Outputs all recovered diagnostics collected during parsing.
    * @param prog parsed program with collected diagnostics
    */
   static void printDiagnostics (ProgText prog) {
      Iterator<ProgramDiagnostic> it = prog.diagnostics().iterator();
      System.out.flush();
      while (it.hasNext()) {
         System.err.println ("Error: " + ProgramDiagnosticRenderer.format (
            (ProgramDiagnostic)it.next()));
      }
   } // end of printDiagnostics()

   /**
    * Parses command-line parameters.
    * @param params command-line parameters
    * @return run configuration
   */
   static RunConfig parseArgs (String[] params) {
      String typesFile = null;
      String specsFile = null;
      String progFile = null;
      int wordsCount = 0;
      for (int i = 0; i < params.length; i++) {
         String arg = params [i];
         if ("--system".equals (arg)) {
            throw new RuntimeException ("The Java entrypoint no longer " +
               "accepts --system. Use --types/--specs/--prog directly, " +
               "or use run-evaluator.sh / run-evaluator.bat for the " +
               "bundled demo profiles.");
         } else if ("--help".equals (arg) || "-h".equals (arg)) {
            throw new RuntimeException (usageText());
         } else if ("--types".equals (arg)) {
            if (i + 1 >= params.length)
               throw new RuntimeException ("Missing file name after " +
                  "--types.");
            typesFile = params [++i];
         } else if ("--specs".equals (arg)) {
            if (i + 1 >= params.length)
               throw new RuntimeException ("Missing file name after " +
                  "--specs.");
            specsFile = params [++i];
         } else if ("--prog".equals (arg)) {
            if (i + 1 >= params.length)
               throw new RuntimeException ("Missing file name after " +
                  "--prog.");
            progFile = params [++i];
         } else {
            wordsCount++;
         }
      }
      String[] programParams = collectProgramWords (params, wordsCount);
      if (typesFile == null)
         throw new RuntimeException ("Missing required --types file. " +
            usageText());
      if (specsFile == null)
         throw new RuntimeException ("Missing required --specs file. " +
            usageText());
      if (progFile == null && programParams.length == 0)
         throw new RuntimeException ("Missing program source. Provide " +
            "--prog file or command-line program words. " + usageText());
      RunConfig result = new RunConfig();
      result.typesFile = typesFile;
      result.specsFile = specsFile;
      result.progFile = progFile;
      result.programParams = programParams;
      return result;
   } // end of parseArgs()

   /**
    * Usage text for the command-line interface.
    * @return human-readable usage summary
    */
   static String usageText () {
      return "Usage: java evaluator.Evaluator --types TYPES --specs SPECS " +
         "[--prog PROGRAM] [word ...]";
   } // end of usageText()

   /**
    * Collects non-option words that form the command-line program text.
    * @param params original command-line parameters
    * @param wordsCount number of resulting program words
    * @return program tokens only
    */
   static String[] collectProgramWords (String[] params, int wordsCount) {
      String[] result = new String [wordsCount];
      int pos = 0;
      for (int i = 0; i < params.length; i++) {
         String arg = params [i];
         if ("--types".equals (arg) ||
             "--specs".equals (arg) || "--prog".equals (arg)) {
            i++;
         } else if ("--help".equals (arg) || "-h".equals (arg)) {
            // Help is handled earlier by parseArgs().
         } else {
            result [pos++] = arg;
         }
      }
      return result;
   } // end of collectProgramWords()

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
            String word = (String)pit.next();
            Spec current = lit.hasNext() ? (Spec)lit.next() : null;
            if (word.trim().length() == 0) continue;
            result.append ("    " + word);
            if (lit.hasNext()) {
              result.append (" \t" + current.toString() + nl);
            } else if (current != null) {
              result.append (" \t" + current.toString() + nl);
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


