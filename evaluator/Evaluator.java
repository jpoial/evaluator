
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

   static class DemoProfile {
      String name;
      String typesFile;
      String specsFile;
      String progFile;

      DemoProfile (String n, String tf, String sf, String pf) {
         name = n;
         typesFile = tf;
         specsFile = sf;
         progFile = pf;
      } // end of constructor
   } // end of DemoProfile

   static class RunConfig {
      DemoProfile profile;
      String typesFile;
      String specsFile;
      String progFile;
      String[] programParams;

      boolean customFiles() {
         return ! profile.typesFile.equals (typesFile) ||
            ! profile.specsFile.equals (specsFile) ||
            ! profile.progFile.equals (progFile);
      } // end of customFiles()
   } // end of RunConfig

   /**
    * Main method that runs examples only.
    * @param params  command-line parameters (program text)
   */
   public static void main (String[] params) {
      try {
         if (!run (params)) System.exit (1);
      } catch (ProgramException e) {
         System.err.println ("Error: " +
            ProgramDiagnosticRenderer.format (e.diagnostic()));
         System.exit (1);
      }
   } // end of main()

   /**
    * Executes one evaluator run.
    * @param params command-line parameters
    */
   static boolean run (String[] params) {
      RunConfig cfg = parseArgs (params);
      String profileInfo = cfg.profile.name;
      if (cfg.customFiles())
         profileInfo = profileInfo + " (custom files)";
      System.out.println ("Profile: " + profileInfo);
      System.out.println ("Types file: " + cfg.typesFile);
      System.out.println ("Specs file: " + cfg.specsFile);
      if (cfg.programParams.length > 0) {
         System.out.println ("Program source: command line");
      } else {
         System.out.println ("Program file: " + cfg.progFile);
      }
      TypeSystem ex1types = new TypeSystem (cfg.typesFile);
      // System.out.println ("TypeSystem: " + ex1types.toString());
      SpecSet ex1specs = new SpecSet (cfg.specsFile, ex1types);
      ProgText ex1prog1;
      if (cfg.programParams.length > 0) {
         ex1prog1 = new ProgText (cfg.programParams, ex1types, ex1specs);
      } else {
         ex1prog1 = new ProgText (cfg.progFile, ex1types, ex1specs);
      }
      if (ex1prog1.hasDiagnostics()) {
         printDiagnostics (ex1prog1);
         return false;
      }
      // System.out.println ("SpecSet:" + ex1specs.toString());
      System.out.println ("Program text:");
      System.out.println (ex1prog1.sourceText());
      System.out.println ("Program: " + ex1prog1.toString());
      SpecList ex1list1 = new SpecList (ex1prog1, ex1types, ex1specs);
      // System.out.println ("Sp.sequence: " + ex1list1.toString());
      Spec resultspec = ex1list1.evaluate (ex1types, ex1specs);
      if (resultspec == null)
         throw ex1list1.typeClash ("linear part of the top-level program",
            ex1prog1);
      System.out.println (annotate (ex1prog1, ex1list1, resultspec));
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
      String profileName = "real";
      String typesOverride = null;
      String specsOverride = null;
      String progOverride = null;
      int wordsCount = 0;
      for (int i = 0; i < params.length; i++) {
         String arg = params [i];
         if ("--system".equals (arg)) {
            if (i + 1 >= params.length)
               throw new RuntimeException ("Missing profile name after " +
                  "--system. Use real, legacy, or forth2012.");
            profileName = params [++i];
         } else if ("--types".equals (arg)) {
            if (i + 1 >= params.length)
               throw new RuntimeException ("Missing file name after " +
                  "--types.");
            typesOverride = params [++i];
         } else if ("--specs".equals (arg)) {
            if (i + 1 >= params.length)
               throw new RuntimeException ("Missing file name after " +
                  "--specs.");
            specsOverride = params [++i];
         } else if ("--prog".equals (arg)) {
            if (i + 1 >= params.length)
               throw new RuntimeException ("Missing file name after " +
                  "--prog.");
            progOverride = params [++i];
         } else {
            wordsCount++;
         }
      }
      DemoProfile profile = selectProfile (profileName);
      RunConfig result = new RunConfig();
      result.profile = profile;
      result.typesFile = (typesOverride == null) ? profile.typesFile :
         typesOverride;
      result.specsFile = (specsOverride == null) ? profile.specsFile :
         specsOverride;
      result.progFile = (progOverride == null) ? profile.progFile :
         progOverride;
      result.programParams = collectProgramWords (params, wordsCount);
      return result;
   } // end of parseArgs()

   /**
    * Selects the demo profile by name.
    * @param profileName profile name
    * @return demo profile descriptor
    */
   static DemoProfile selectProfile (String profileName) {
      if ("real".equals (profileName))
         return new DemoProfile ("real", "ex1types.txt", "ex1specs.txt",
            "ex1prog.txt");
      if ("legacy".equals (profileName))
         return new DemoProfile ("legacy", "legacytypes.txt",
            "legacyspecs.txt", "legacyprog.txt");
      if ("forth2012".equals (profileName))
         return new DemoProfile ("forth2012", "forth2012types.txt",
            "forth2012specs.txt", "forth2012prog.txt");
      throw new RuntimeException ("Unknown profile " + profileName +
         ". Use real, legacy, or forth2012.");
   } // end of selectProfile()

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
         if ("--system".equals (arg) || "--types".equals (arg) ||
             "--specs".equals (arg) || "--prog".equals (arg)) {
            i++;
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


