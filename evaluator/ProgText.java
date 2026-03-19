
// file: ProgText.java

package evaluator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Inner representation for the program that is analysed.
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class ProgText extends LinkedList<String> {

    static final long serialVersionUID = 0xaabbcc;

   /** original source text before parsing removes definitions */
   String sourceText = "";

   static class ParseResult {
      Spec effect;
      String stopWord;

      ParseResult (Spec s, String stop) {
         effect = s;
         stopWord = stop;
      } // end of constructor
   } // end of ParseResult
    
   ProgText() {
      super();
   } // end of constructor

   /**
    * Reads program text from the file and parses it using given
    * specifications.
    * @param fileName  local file name (program text)
    * @param ts  type system used to evaluate definitions
    * @param ss  set of specifications to use
    */
   ProgText (String fileName, TypeSystem ts, SpecSet ss) {
      this();
      LinkedList<String> tokens = new LinkedList<String>();
      StringBuffer source = new StringBuffer ("");
      String nl = System.getProperty ("line.separator");
      boolean firstLine = true;
      BufferedReader reader = null;
      try {
         reader = new BufferedReader (new FileReader (fileName));
         String line;
         while ((line = reader.readLine()) != null) {
            if (!firstLine) source.append (nl);
            source.append (line);
            firstLine = false;
            addWords (TypeSystem.stripComment (line), tokens);
         }
      } catch (IOException e) {
         throw new RuntimeException ("Unable to read program text from " +
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
      sourceText = source.toString();
      parseTokens (tokens, ts, ss, fileName);
   } // end of constructor

   /**
    * Creates inner representation from the given array of strings
    * using given specifications.
    * @param text  program text
    * @param ts  type system used to evaluate definitions
    * @param ss  set of specifications to use
    */
   ProgText (String[] text, TypeSystem ts, SpecSet ss) {
      this();
      LinkedList<String> tokens = new LinkedList<String>();
      StringBuffer source = new StringBuffer ("");
      for (int i=0; i<text.length; i++) {
         if (i > 0) source.append (" ");
         source.append (text [i]);
         addWords (text [i], tokens);
      }
      sourceText = source.toString();
      parseTokens (tokens, ts, ss, "<command line>");
   } // end of constructor

   /**
    * Returns original source text before parsing.
    * @return source text
    */
   String sourceText() {
      return sourceText;
   } // end of sourceText()

   /**
    * Adds all words from the given chunk of text to the target list.
    * @param text whitespace separated words
    * @param target target list for the parsed words
    */
   static void addWords (String text, LinkedList<String> target) {
      String trimmed = text.trim();
      if (trimmed.length() == 0) return;
      String[] words = trimmed.split ("\\s+");
      for (int i = 0; i < words.length; i++) {
         target.add (words [i]);
      }
   } // end of addWords()

   /**
    * Parses top-level program tokens and handles linear colon definitions.
    * @param tokens tokenized program text
    * @param ts type system used to evaluate definitions
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    */
   void parseTokens (LinkedList<String> tokens, TypeSystem ts, SpecSet ss,
      String sourceName) {
      String currentDef = null;
      LinkedList<String> defBody = null;
      while (tokens.size() > 0) {
         String word = (String)tokens.removeFirst();
         if (currentDef == null) {
            if (":".equals (word)) {
               if (tokens.size() == 0)
                  throw new RuntimeException ("Missing word name after : in " +
                     sourceName);
               currentDef = ((String)tokens.removeFirst()).trim();
               if (currentDef.length() == 0)
                  throw new RuntimeException ("Empty word name after : in " +
                     sourceName);
               if (":".equals (currentDef) | ";".equals (currentDef))
                  throw new RuntimeException ("Illegal word name " + currentDef +
                     " in " + sourceName);
               defBody = new LinkedList<String>();
            } else {
               if (";".equals (word))
                  throw new RuntimeException ("Unexpected ; in " + sourceName);
               add (word);
            }
         } else {
            if (":".equals (word))
               throw new RuntimeException ("Nested definitions are not " +
                  "supported in " + sourceName);
            if (";".equals (word)) {
               defineWord (currentDef, defBody, ts, ss, sourceName);
               currentDef = null;
               defBody = null;
            } else {
               defBody.add (word);
            }
         }
      }
      if (currentDef != null)
         throw new RuntimeException ("Unterminated definition for " +
            currentDef + " in " + sourceName);
   } // end of parseTokens()

   /**
    * Evaluates one colon definition and stores its effect in the current
    * specification set.
    * @param wordName name of the defined word
    * @param body tokenized body of the definition
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    */
   void defineWord (String wordName, LinkedList<String> body, TypeSystem ts,
      SpecSet ss, String sourceName) {
      LinkedList<String> tokens = new LinkedList<String> (body);
      ParseResult parsed = parseDefinitionSequence (tokens, ts, ss,
         sourceName, wordName, new String [0], 0);
      Spec defSpec = parsed.effect;
      if (parsed.stopWord != null)
         throw new RuntimeException ("Unexpected terminator " +
            parsed.stopWord + " in definition of " + wordName + " in " +
            sourceName);
      ss.put (wordName, defSpec);
   } // end of defineWord()

   /**
    * Parses one sequence inside a colon definition. Sequence items may be
    * words, IF..FI / IF..ELSE..FI structures, BEGIN-based loops,
    * or DO..LOOP structures.
    * @param tokens remaining definition tokens
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    * @param wordName current definition name
    * @param stopWords delimiters that end this sequence
    * @param doDepth number of surrounding DO..LOOP structures
    * @return parsed effect and the delimiter that ended the sequence
    */
   ParseResult parseDefinitionSequence (LinkedList<String> tokens,
      TypeSystem ts, SpecSet ss, String sourceName, String wordName,
      String[] stopWords, int doDepth) {
      SpecList seq = new SpecList();
      while (tokens.size() > 0) {
         String word = (String)tokens.removeFirst();
         if (isStopWord (word, stopWords))
            return new ParseResult (evaluateSpecList (seq, ts, ss,
               contextLabel ("sequence", wordName, sourceName)), word);
         if ("IF".equals (word)) {
            ParseResult thenPart = parseDefinitionSequence (tokens, ts, ss,
               sourceName, wordName, new String [] {"ELSE", "FI"}, doDepth);
            ParseResult elsePart = new ParseResult (new Spec (ts), "FI");
            if ("ELSE".equals (thenPart.stopWord)) {
               elsePart = parseDefinitionSequence (tokens, ts, ss,
                  sourceName, wordName, new String [] {"FI"}, doDepth);
            }
            if (!"FI".equals (elsePart.stopWord))
               throw new RuntimeException ("Missing FI for IF in definition " +
                  "of " + wordName + " in " + sourceName);
            seq.add (buildIfEffect (thenPart.effect, elsePart.effect, ts, ss,
               sourceName, wordName));
         } else if ("BEGIN".equals (word)) {
            ParseResult alphaPart = parseDefinitionSequence (tokens, ts, ss,
               sourceName, wordName,
               new String [] {"WHILE", "AGAIN", "UNTIL"}, doDepth);
            if ("WHILE".equals (alphaPart.stopWord)) {
               ParseResult betaPart = parseDefinitionSequence (tokens, ts, ss,
                  sourceName, wordName, new String [] {"REPEAT"}, doDepth);
               if (!"REPEAT".equals (betaPart.stopWord))
                  throw new RuntimeException ("Missing REPEAT for BEGIN in " +
                     "definition of " + wordName + " in " + sourceName);
               seq.add (buildWhileEffect (alphaPart.effect, betaPart.effect,
                  ts, ss, sourceName, wordName));
            } else if ("AGAIN".equals (alphaPart.stopWord)) {
               seq.add (buildAgainEffect (alphaPart.effect, ts, ss,
                  sourceName, wordName));
            } else if ("UNTIL".equals (alphaPart.stopWord)) {
               seq.add (buildUntilEffect (alphaPart.effect, ts, ss,
                  sourceName, wordName));
            } else {
               throw new RuntimeException ("Missing WHILE, AGAIN, or UNTIL " +
                  "for BEGIN in definition of " + wordName + " in " +
                  sourceName);
            }
         } else if ("DO".equals (word)) {
            ParseResult bodyPart = parseDefinitionSequence (tokens, ts, ss,
               sourceName, wordName, new String [] {"LOOP"}, doDepth + 1);
            if (!"LOOP".equals (bodyPart.stopWord))
               throw new RuntimeException ("Missing LOOP for DO in definition "
                  + "of " + wordName + " in " + sourceName);
            seq.add (buildDoLoopEffect (bodyPart.effect, ts, ss, sourceName,
               wordName));
         } else {
            if (":".equals (word))
               throw new RuntimeException ("Nested definitions are not " +
                  "supported in definition of " + wordName + " in " +
                  sourceName);
            if ("ELSE".equals (word) | "FI".equals (word) |
                "WHILE".equals (word) | "REPEAT".equals (word) |
                "AGAIN".equals (word) | "UNTIL".equals (word) |
                "LOOP".equals (word))
               throw new RuntimeException ("Unexpected " + word +
                  " in definition of " + wordName + " in " + sourceName);
            Spec sp = null;
            if ("I".equals (word) & (doDepth > 0)) {
               sp = indexSpec (ts, sourceName);
            } else {
               sp = (Spec)ss.get (word);
            }
            if (sp == null)
               throw new RuntimeException ("no specif. found for " + word +
                  " in definition of " + wordName + " in " + sourceName);
            seq.add ((Spec)sp.clone());
         }
      }
      return new ParseResult (evaluateSpecList (seq, ts, ss,
         contextLabel ("sequence", wordName, sourceName)), null);
   } // end of parseDefinitionSequence()

   /**
    * Evaluates a sequence of already parsed stack effects.
    * @param seq sequence to evaluate
    * @param ts type system to use
    * @param ss current specification set
    * @param context textual description for diagnostics
    * @return resulting stack effect
    */
   Spec evaluateSpecList (SpecList seq, TypeSystem ts, SpecSet ss,
      String context) {
      Spec result = seq.evaluate (ts, ss);
      if (result == null)
         throw new RuntimeException ("Type conflict in " + context);
      return result;
   } // end of evaluateSpecList()

   /**
    * Creates the effect of IF..ELSE..FI by consuming a flag and merging
    * the two alternative branch effects via glb.
    * @param thenEffect effect of the true branch
    * @param elseEffect effect of the false branch
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    * @param wordName current definition name
    * @return resulting IF effect
    */
   Spec buildIfEffect (Spec thenEffect, Spec elseEffect, TypeSystem ts,
      SpecSet ss, String sourceName, String wordName) {
      Spec merged = thenEffect.glb (elseEffect, ts, ss);
      if (merged == null)
         throw new RuntimeException ("Incompatible IF branches in definition "
            + "of " + wordName + " in " + sourceName);
      SpecList seq = new SpecList();
      seq.add (flagConsumeSpec (ts, sourceName));
      seq.add ((Spec)merged.clone());
      return evaluateSpecList (seq, ts, ss,
         contextLabel ("IF structure", wordName, sourceName));
   } // end of buildIfEffect()

   /**
    * Creates the effect of BEGIN..WHILE..REPEAT using the must-analysis
    * approximation described in the papers.
    * @param alphaEffect effect of the sequence before WHILE
    * @param betaEffect effect of the sequence after WHILE
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    * @param wordName current definition name
    * @return resulting loop effect
    */
   Spec buildWhileEffect (Spec alphaEffect, Spec betaEffect, TypeSystem ts,
      SpecSet ss, String sourceName, String wordName) {
      SpecList alphaSeq = new SpecList();
      alphaSeq.add ((Spec)alphaEffect.clone());
      alphaSeq.add (flagConsumeSpec (ts, sourceName));
      Spec alphaTest = evaluateSpecList (alphaSeq, ts, ss,
         contextLabel ("loop test", wordName, sourceName));
      Spec alphaLoop = alphaTest.piStar (ts, ss);
      if (alphaLoop == null)
         throw new RuntimeException ("Loop prefix is not idempotent in " +
            "definition of " + wordName + " in " + sourceName);
      Spec betaLoop = betaEffect.piStar (ts, ss);
      if (betaLoop == null)
         throw new RuntimeException ("Loop suffix is not idempotent in " +
            "definition of " + wordName + " in " + sourceName);
      SpecList loopSeq = new SpecList();
      loopSeq.add ((Spec)alphaLoop.clone());
      loopSeq.add ((Spec)betaLoop.clone());
      return evaluateSpecList (loopSeq, ts, ss,
         contextLabel ("WHILE loop", wordName, sourceName));
   } // end of buildWhileEffect()

   /**
    * Creates the effect of BEGIN..AGAIN using the same fixed-point
    * approximation as the other loop forms.
    * @param bodyEffect effect of the loop body
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    * @param wordName current definition name
    * @return resulting loop effect
    */
   Spec buildAgainEffect (Spec bodyEffect, TypeSystem ts, SpecSet ss,
      String sourceName, String wordName) {
      Spec bodyLoop = bodyEffect.piStar (ts, ss);
      if (bodyLoop == null)
         throw new RuntimeException ("BEGIN..AGAIN body is not idempotent " +
            "in definition of " + wordName + " in " + sourceName);
      return bodyLoop;
   } // end of buildAgainEffect()

   /**
    * Creates the effect of BEGIN..UNTIL by evaluating the loop body
    * together with the terminating flag test.
    * @param bodyEffect effect of the loop body before UNTIL
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    * @param wordName current definition name
    * @return resulting loop effect
    */
   Spec buildUntilEffect (Spec bodyEffect, TypeSystem ts, SpecSet ss,
      String sourceName, String wordName) {
      SpecList testSeq = new SpecList();
      testSeq.add ((Spec)bodyEffect.clone());
      testSeq.add (flagConsumeSpec (ts, sourceName));
      Spec loopTest = evaluateSpecList (testSeq, ts, ss,
         contextLabel ("UNTIL test", wordName, sourceName));
      Spec untilLoop = loopTest.piStar (ts, ss);
      if (untilLoop == null)
         throw new RuntimeException ("BEGIN..UNTIL body is not idempotent " +
            "in definition of " + wordName + " in " + sourceName);
      return untilLoop;
   } // end of buildUntilEffect()

   /**
    * Creates the effect of DO..LOOP using a conservative counted-loop
     * approximation: the loop body must be idempotent and the structure
    * consumes Forth-style limit and start parameters.
    * @param bodyEffect effect of the loop body
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    * @param wordName current definition name
    * @return resulting DO..LOOP effect
    */
   Spec buildDoLoopEffect (Spec bodyEffect, TypeSystem ts, SpecSet ss,
      String sourceName, String wordName) {
      Spec bodyLoop = bodyEffect.piStar (ts, ss);
      if (bodyLoop == null)
         throw new RuntimeException ("DO..LOOP body is not idempotent in " +
            "definition of " + wordName + " in " + sourceName);
      SpecList loopSeq = new SpecList();
      loopSeq.add (doConsumeSpec (ts, sourceName));
      loopSeq.add ((Spec)bodyLoop.clone());
      return evaluateSpecList (loopSeq, ts, ss,
         contextLabel ("DO loop", wordName, sourceName));
   } // end of buildDoLoopEffect()

   /**
    * Returns the specification that consumes one flag.
    * @param ts type system to use
    * @param sourceName file name or other source label for diagnostics
    * @return spec ( flag -- )
    */
   Spec flagConsumeSpec (TypeSystem ts, String sourceName) {
      return SpecSet.parseSpec ("flag --", ts, sourceName, 0);
   } // end of flagConsumeSpec()

   /**
    * Returns the specification that consumes loop limit and start values.
    * @param ts type system to use
    * @param sourceName file name or other source label for diagnostics
    * @return spec ( n[2] n[1] -- ) where n[2] is the exclusive loop limit
    *   and n[1] is the start index; with step 1 the last executed index is
    *   limit-1, as in 7 0 DO I . LOOP -> 0 1 2 3 4 5 6
    */
   Spec doConsumeSpec (TypeSystem ts, String sourceName) {
      return SpecSet.parseSpec ("n[2] n[1] --", ts, sourceName, 0);
   } // end of doConsumeSpec()

   /**
    * Returns the specification of the innermost DO..LOOP index.
    * @param ts type system to use
    * @param sourceName file name or other source label for diagnostics
    * @return spec ( -- n )
    */
   Spec indexSpec (TypeSystem ts, String sourceName) {
      return SpecSet.parseSpec ("-- n", ts, sourceName, 0);
   } // end of indexSpec()

   /**
    * Tells whether the given token ends the current parsed sequence.
    * @param word current token
    * @param stopWords active delimiters
    * @return true if the token is a delimiter
    */
   boolean isStopWord (String word, String[] stopWords) {
      for (int i = 0; i < stopWords.length; i++) {
         if (stopWords [i].equals (word)) return true;
      }
      return false;
   } // end of isStopWord()

   /**
    * Creates a short diagnostic label for sequence evaluation errors.
    * @param what current structure kind
    * @param wordName current definition name
    * @param sourceName file name or other source label
    * @return context label
    */
   String contextLabel (String what, String wordName, String sourceName) {
      return what + " in definition of " + wordName + " in " + sourceName;
   } // end of contextLabel()

   /**
    * Converts inner representation back to string.
    */
   public String toString() {
      StringBuffer result = new StringBuffer ("");
      Iterator<String> it = iterator();
      while (it.hasNext()) {
         result.append (it.next().toString() + " ");
      }
      return result.toString();
   } // end of toString()

} // end of ProgText

// end of file
