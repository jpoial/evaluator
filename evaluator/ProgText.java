// file: ProgText.java

package evaluator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inner representation for the program that is analysed.
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class ProgText extends LinkedList<String> {

   static final long serialVersionUID = 0xaabbcc;

   static final Pattern WORD_PATTERN = Pattern.compile ("\\S+");

   /** original source text before parsing removes definitions */
   String sourceText = "";

   /** original program source split into lines */
   LinkedList<String> sourceLines = new LinkedList<String>();

   /** source spans of top-level program words */
   LinkedList<SourceSpan> wordSpans = new LinkedList<SourceSpan>();

   static class SourceWord {
      String text;
      SourceSpan span;

      SourceWord (String word, SourceSpan where) {
         text = word;
         span = where;
      } // end of constructor
   } // end of SourceWord

   static class ParseResult {
      Spec effect;
      SourceWord stopToken;
      SourceSpan span;

      ParseResult (Spec s, SourceWord stop, SourceSpan where) {
         effect = s;
         stopToken = stop;
         span = where;
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
      LinkedList<SourceWord> tokens = new LinkedList<SourceWord>();
      StringBuffer source = new StringBuffer ("");
      String nl = System.getProperty ("line.separator");
      boolean firstLine = true;
      BufferedReader reader = null;
      try {
         reader = new BufferedReader (new FileReader (fileName));
         String line;
         int lineNo = 0;
         while ((line = reader.readLine()) != null) {
            lineNo++;
            if (!firstLine) source.append (nl);
            source.append (line);
            sourceLines.add (line);
            firstLine = false;
            addWords (lineBeforeComment (line), tokens, fileName, lineNo);
         }
      } catch (IOException e) {
         throw new ProgramException (new ProgramDiagnostic (
            "program.read-failed", ProgramDiagnostic.SEVERITY_ERROR,
            "Unable to read program text from " + fileName, "", null, null,
            null), e);
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
      LinkedList<SourceWord> tokens = new LinkedList<SourceWord>();
      StringBuffer source = new StringBuffer ("");
      for (int i=0; i<text.length; i++) {
         if (i > 0) source.append (" ");
         source.append (text [i]);
         addWords (text [i], tokens, "<command line>", 0);
      }
      sourceText = source.toString();
      sourceLines.add (sourceText);
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
    * Returns the source span of a top-level word.
    * @param index program word index
    * @return source span or null
    */
   SourceSpan wordSpan (int index) {
      if ((index < 0) | (index >= wordSpans.size())) return null;
      return (SourceSpan)wordSpans.get (index);
   } // end of wordSpan()

   /**
    * Adds all words from the given chunk of text to the target list.
    * @param text whitespace separated words
    * @param target target list for the parsed words
    * @param sourceName source label
    * @param lineNo line number in the source, or 0 if unavailable
    */
   static void addWords (String text, LinkedList<SourceWord> target,
      String sourceName, int lineNo) {
      Matcher matcher = WORD_PATTERN.matcher (text);
      while (matcher.find()) {
         int start = matcher.start() + 1;
         int end = matcher.end();
         target.add (new SourceWord (matcher.group(), new SourceSpan (
            sourceName, lineNo, start, lineNo, end)));
      }
   } // end of addWords()

   /**
    * Removes the trailing comment marker but keeps original indentation.
    * @param line source line
    * @return line prefix before comment
    */
   static String lineBeforeComment (String line) {
      int commentPos = line.indexOf ('#');
      if (commentPos >= 0) return line.substring (0, commentPos);
      return line;
   } // end of lineBeforeComment()

   /**
    * Parses top-level program tokens and handles linear colon definitions.
    * @param tokens tokenized program text
    * @param ts type system used to evaluate definitions
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    */
   void parseTokens (LinkedList<SourceWord> tokens, TypeSystem ts, SpecSet ss,
      String sourceName) {
      String currentDef = null;
      SourceWord currentDefToken = null;
      LinkedList<SourceWord> defBody = null;
      while (tokens.size() > 0) {
         SourceWord token = (SourceWord)tokens.removeFirst();
         String word = token.text;
         if (currentDef == null) {
            if (":".equals (word)) {
               if (tokens.size() == 0)
                  throw programError ("parse.missing-word-name",
                     "Missing word name after :", "", token.span);
               currentDefToken = (SourceWord)tokens.removeFirst();
               currentDef = currentDefToken.text.trim();
               if (currentDef.length() == 0)
                  throw programError ("parse.empty-word-name",
                     "Empty word name after :", "", currentDefToken.span);
               if (":".equals (currentDef) | ";".equals (currentDef))
                  throw programError ("parse.illegal-word-name",
                     "Illegal word name " + currentDef, "",
                     currentDefToken.span);
               defBody = new LinkedList<SourceWord>();
            } else {
               if (";".equals (word))
                  throw unexpectedToken (";", token.span, sourceName);
               add (word);
               wordSpans.add (token.span);
            }
         } else {
            if (":".equals (word))
               throw programError ("parse.nested-definition",
                  "Nested definitions are not supported in definition of " +
                  currentDef, "", token.span);
            if (";".equals (word)) {
               defineWord (currentDef, defBody, ts, ss, sourceName);
               currentDef = null;
               currentDefToken = null;
               defBody = null;
            } else {
               defBody.add (token);
            }
         }
      }
      if (currentDef != null)
         throw programError ("parse.unterminated-definition",
            "Unterminated definition for " + currentDef, "",
            currentDefToken.span);
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
   void defineWord (String wordName, LinkedList<SourceWord> body,
      TypeSystem ts, SpecSet ss, String sourceName) {
      LinkedList<SourceWord> tokens = new LinkedList<SourceWord> (body);
      ParseResult parsed = parseDefinitionSequence (tokens, ts, ss,
         sourceName, wordName, new String [0], 0);
      Spec defSpec = parsed.effect;
      if (parsed.stopToken != null)
         throw unexpectedToken (parsed.stopToken.text, parsed.stopToken.span,
            "definition of " + wordName);
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
   ParseResult parseDefinitionSequence (LinkedList<SourceWord> tokens,
      TypeSystem ts, SpecSet ss, String sourceName, String wordName,
      String[] stopWords, int doDepth) {
      SpecList seq = new SpecList();
      SourceSpan seqSpan = null;
      while (tokens.size() > 0) {
         SourceWord token = (SourceWord)tokens.removeFirst();
         String word = token.text;
         if (isStopWord (word, stopWords))
            return new ParseResult (evaluateSpecList (seq, ts, ss,
               "linear part of definition " + wordName), token, seqSpan);
         if ("IF".equals (word)) {
            ParseResult thenPart = parseDefinitionSequence (tokens, ts, ss,
               sourceName, wordName, new String [] {"ELSE", "FI"}, doDepth);
            boolean hasElse = false;
            ParseResult elsePart = new ParseResult ((new Spec (ts)).withOrigin
               (null, "empty branch"), thenPart.stopToken, null);
            if ((thenPart.stopToken != null) &
                "ELSE".equals (thenPart.stopToken.text)) {
               hasElse = true;
               elsePart = parseDefinitionSequence (tokens, ts, ss, sourceName,
                  wordName, new String [] {"FI"}, doDepth);
            }
            if ((elsePart.stopToken == null) |
                !"FI".equals (elsePart.stopToken.text))
               throw missingTerminator ("FI", "IF", token.span, wordName);
            SourceSpan ifSpan = SourceSpan.covering (token.span,
               elsePart.stopToken.span);
            Spec ifEffect = buildIfEffect (thenPart.effect, elsePart.effect,
               ts, ss, wordName, ifSpan, hasElse);
            seq.add (ifEffect);
            seqSpan = SourceSpan.covering (seqSpan, ifSpan);
         } else if ("BEGIN".equals (word)) {
            ParseResult alphaPart = parseDefinitionSequence (tokens, ts, ss,
               sourceName, wordName,
               new String [] {"WHILE", "AGAIN", "UNTIL"}, doDepth);
            if ((alphaPart.stopToken != null) &
                "WHILE".equals (alphaPart.stopToken.text)) {
               ParseResult betaPart = parseDefinitionSequence (tokens, ts, ss,
                  sourceName, wordName, new String [] {"REPEAT"}, doDepth);
               if ((betaPart.stopToken == null) |
                   !"REPEAT".equals (betaPart.stopToken.text))
                  throw missingTerminator ("REPEAT", "BEGIN", token.span,
                     wordName);
               SourceSpan loopSpan = SourceSpan.covering (token.span,
                  betaPart.stopToken.span);
               Spec loopEffect = buildWhileEffect (alphaPart.effect,
                  betaPart.effect, ts, ss, wordName, loopSpan);
               seq.add (loopEffect);
               seqSpan = SourceSpan.covering (seqSpan, loopSpan);
            } else if ((alphaPart.stopToken != null) &
                "AGAIN".equals (alphaPart.stopToken.text)) {
               SourceSpan loopSpan = SourceSpan.covering (token.span,
                  alphaPart.stopToken.span);
               Spec loopEffect = buildAgainEffect (alphaPart.effect, ts, ss,
                  wordName, loopSpan);
               seq.add (loopEffect);
               seqSpan = SourceSpan.covering (seqSpan, loopSpan);
            } else if ((alphaPart.stopToken != null) &
                "UNTIL".equals (alphaPart.stopToken.text)) {
               SourceSpan loopSpan = SourceSpan.covering (token.span,
                  alphaPart.stopToken.span);
               Spec loopEffect = buildUntilEffect (alphaPart.effect, ts, ss,
                  wordName, loopSpan);
               seq.add (loopEffect);
               seqSpan = SourceSpan.covering (seqSpan, loopSpan);
            } else {
               throw missingTerminator ("WHILE, AGAIN, or UNTIL", "BEGIN",
                  token.span, wordName);
            }
         } else if ("DO".equals (word)) {
            ParseResult bodyPart = parseDefinitionSequence (tokens, ts, ss,
               sourceName, wordName, new String [] {"LOOP"}, doDepth + 1);
            if ((bodyPart.stopToken == null) |
                !"LOOP".equals (bodyPart.stopToken.text))
               throw missingTerminator ("LOOP", "DO", token.span, wordName);
            SourceSpan loopSpan = SourceSpan.covering (token.span,
               bodyPart.stopToken.span);
            Spec loopEffect = buildDoLoopEffect (bodyPart.effect, ts, ss,
               wordName, loopSpan);
            seq.add (loopEffect);
            seqSpan = SourceSpan.covering (seqSpan, loopSpan);
         } else {
            if (":".equals (word))
               throw programError ("parse.nested-definition",
                  "Nested definitions are not supported in definition of " +
                  wordName, "", token.span);
            if ("ELSE".equals (word) | "FI".equals (word) |
                "WHILE".equals (word) | "REPEAT".equals (word) |
                "AGAIN".equals (word) | "UNTIL".equals (word) |
                "LOOP".equals (word))
               throw unexpectedToken (word, token.span,
                  "definition of " + wordName);
            Spec sp = null;
            if ("I".equals (word) & (doDepth > 0)) {
               sp = indexSpec (ts, sourceName);
            } else {
               sp = (Spec)ss.get (word);
            }
            if (sp == null)
               throw missingWord (word, token.span,
                  "definition of " + wordName);
            seq.add (((Spec)sp.clone()).withOrigin (token.span, word));
            seqSpan = SourceSpan.covering (seqSpan, token.span);
         }
      }
      return new ParseResult (evaluateSpecList (seq, ts, ss,
         "linear part of definition " + wordName), null, seqSpan);
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
         throw seq.typeClash (context, this);
      return result;
   } // end of evaluateSpecList()

   /**
    * Creates the effect of IF..ELSE..FI by consuming a flag and merging
    * the two alternative branch effects via glb.
    * @param thenEffect effect of the true branch
    * @param elseEffect effect of the false branch
    * @param ts type system to use
    * @param ss current specification set
    * @param wordName current definition name
    * @param structureSpan source span of the full IF structure
    * @param hasElse true if the source used ELSE explicitly
    * @return resulting IF effect
    */
   Spec buildIfEffect (Spec thenEffect, Spec elseEffect, TypeSystem ts,
      SpecSet ss, String wordName, SourceSpan structureSpan,
      boolean hasElse) {
      String label = hasElse ? "IF...ELSE...FI" : "IF...FI";
      Spec merged = thenEffect.glb (elseEffect, ts, ss);
      if (merged == null)
         throw programError ("type.if-branch-clash",
            "Non-comparable branches in " + label + " of definition " +
            wordName, "then branch " + thenEffect.toString().trim() +
            ", else branch " + elseEffect.toString().trim() +
            " cannot be merged", structureSpan);
      SpecList seq = new SpecList();
      seq.add (flagConsumeSpec (ts, structureSpan.sourceName).withOrigin (
         structureSpan, label));
      seq.add (((Spec)merged.clone()).withOrigin (structureSpan, label));
      return evaluateSpecList (seq, ts, ss,
         label + " in definition " + wordName).withOrigin (structureSpan,
         label);
   } // end of buildIfEffect()

   /**
    * Creates the effect of BEGIN..WHILE..REPEAT using the must-analysis
    * approximation described in the papers.
    * @param alphaEffect effect of the sequence before WHILE
    * @param betaEffect effect of the sequence after WHILE
    * @param ts type system to use
    * @param ss current specification set
    * @param wordName current definition name
    * @param structureSpan source span of the full loop
    * @return resulting loop effect
    */
   Spec buildWhileEffect (Spec alphaEffect, Spec betaEffect, TypeSystem ts,
      SpecSet ss, String wordName, SourceSpan structureSpan) {
      SpecList alphaSeq = new SpecList();
      alphaSeq.add ((Spec)alphaEffect.clone());
      alphaSeq.add (flagConsumeSpec (ts, structureSpan.sourceName).withOrigin
         (structureSpan, "WHILE"));
      Spec alphaTest = evaluateSpecList (alphaSeq, ts, ss,
         "WHILE loop test in definition " + wordName);
      Spec alphaLoop = alphaTest.piStar (ts, ss);
      if (alphaLoop == null)
         throw programError ("type.loop-prefix-non-idempotent",
            "Non-idempotent loop prefix in BEGIN...WHILE...REPEAT of " +
            "definition " + wordName, "prefix effect " +
            alphaTest.toString().trim(), structureSpan);
      Spec betaLoop = betaEffect.piStar (ts, ss);
      if (betaLoop == null)
         throw programError ("type.loop-body-non-idempotent",
            "Non-idempotent loop body in BEGIN...WHILE...REPEAT of " +
            "definition " + wordName, "body effect " +
            betaEffect.toString().trim(), structureSpan);
      SpecList loopSeq = new SpecList();
      loopSeq.add (((Spec)alphaLoop.clone()).withOrigin (structureSpan,
         "BEGIN...WHILE...REPEAT"));
      loopSeq.add (((Spec)betaLoop.clone()).withOrigin (structureSpan,
         "BEGIN...WHILE...REPEAT"));
      return evaluateSpecList (loopSeq, ts, ss,
         "BEGIN...WHILE...REPEAT in definition " + wordName).withOrigin (
         structureSpan, "BEGIN...WHILE...REPEAT");
   } // end of buildWhileEffect()

   /**
    * Creates the effect of BEGIN..AGAIN using the same fixed-point
    * approximation as the other loop forms.
    * @param bodyEffect effect of the loop body
    * @param ts type system to use
    * @param ss current specification set
    * @param wordName current definition name
    * @param structureSpan source span of the full loop
    * @return resulting loop effect
    */
   Spec buildAgainEffect (Spec bodyEffect, TypeSystem ts, SpecSet ss,
      String wordName, SourceSpan structureSpan) {
      Spec bodyLoop = bodyEffect.piStar (ts, ss);
      if (bodyLoop == null)
         throw programError ("type.loop-body-non-idempotent",
            "Non-idempotent loop body in BEGIN...AGAIN of definition " +
            wordName, "body effect " + bodyEffect.toString().trim(),
            structureSpan);
      return bodyLoop.withOrigin (structureSpan, "BEGIN...AGAIN");
   } // end of buildAgainEffect()

   /**
    * Creates the effect of BEGIN..UNTIL by evaluating the loop body
    * together with the terminating flag test.
    * @param bodyEffect effect of the loop body before UNTIL
    * @param ts type system to use
    * @param ss current specification set
    * @param wordName current definition name
    * @param structureSpan source span of the full loop
    * @return resulting loop effect
    */
   Spec buildUntilEffect (Spec bodyEffect, TypeSystem ts, SpecSet ss,
      String wordName, SourceSpan structureSpan) {
      SpecList testSeq = new SpecList();
      testSeq.add ((Spec)bodyEffect.clone());
      testSeq.add (flagConsumeSpec (ts, structureSpan.sourceName).withOrigin
         (structureSpan, "UNTIL"));
      Spec loopTest = evaluateSpecList (testSeq, ts, ss,
         "UNTIL loop test in definition " + wordName);
      Spec untilLoop = loopTest.piStar (ts, ss);
      if (untilLoop == null)
         throw programError ("type.loop-body-non-idempotent",
            "Non-idempotent loop body in BEGIN...UNTIL of definition " +
            wordName, "body and flag-test effect " +
            loopTest.toString().trim(), structureSpan);
      return untilLoop.withOrigin (structureSpan, "BEGIN...UNTIL");
   } // end of buildUntilEffect()

   /**
    * Creates the effect of DO..LOOP using a conservative counted-loop
    * approximation: the loop body must be idempotent and the structure
    * consumes Forth-style limit and start parameters.
    * @param bodyEffect effect of the loop body
    * @param ts type system to use
    * @param ss current specification set
    * @param wordName current definition name
    * @param structureSpan source span of the full loop
    * @return resulting DO..LOOP effect
    */
   Spec buildDoLoopEffect (Spec bodyEffect, TypeSystem ts, SpecSet ss,
      String wordName, SourceSpan structureSpan) {
      Spec bodyLoop = bodyEffect.piStar (ts, ss);
      if (bodyLoop == null)
         throw programError ("type.loop-body-non-idempotent",
            "Non-idempotent loop body in DO...LOOP of definition " +
            wordName, "body effect " + bodyEffect.toString().trim(),
            structureSpan);
      SpecList loopSeq = new SpecList();
      loopSeq.add (doConsumeSpec (ts, structureSpan.sourceName).withOrigin (
         structureSpan, "DO...LOOP"));
      loopSeq.add (((Spec)bodyLoop.clone()).withOrigin (structureSpan,
         "DO...LOOP"));
      return evaluateSpecList (loopSeq, ts, ss,
         "DO...LOOP in definition " + wordName).withOrigin (structureSpan,
         "DO...LOOP");
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
    * Creates a missing-terminator diagnostic.
    * @param terminator required closing token
    * @param opener opening structure
    * @param openerSpan opening token span
    * @param wordName current definition
    * @return diagnostic exception
    */
   ProgramException missingTerminator (String terminator, String opener,
      SourceSpan openerSpan, String wordName) {
      return programError ("parse.missing-terminator", "Missing " +
         terminator + " for " + opener + " in definition of " + wordName,
         "", openerSpan);
   } // end of missingTerminator()

   /**
    * Creates an unknown-word diagnostic.
    * @param word missing word
    * @param span token span
    * @param context surrounding context
    * @return diagnostic exception
    */
   ProgramException missingWord (String word, SourceSpan span, String context) {
      return programError ("lookup.unknown-word",
         "No specification found for " + word + " in " + context, "",
         span);
   } // end of missingWord()

   /**
    * Creates an unexpected-token diagnostic.
    * @param word unexpected token
    * @param span token span
    * @param context surrounding context
    * @return diagnostic exception
    */
   ProgramException unexpectedToken (String word, SourceSpan span,
      String context) {
      return programError ("parse.unexpected-token", "Unexpected " + word +
         " in " + context, "", span);
   } // end of unexpectedToken()

   /**
    * Creates a program error and appends source context when available.
    * @param message main diagnostic text
    * @param span source span of the problem
    * @return diagnostic exception
    */
   ProgramException programError (String code, String message, String reason,
      SourceSpan span) {
      return new ProgramException (programDiagnostic (code, message, reason,
         span));
   } // end of programError()

   /**
    * Creates a structured diagnostic.
    * @param code diagnostic code
    * @param message summary message
    * @param reason detailed reason
    * @param span source span
    * @return structured diagnostic
    */
   ProgramDiagnostic programDiagnostic (String code, String message,
      String reason, SourceSpan span) {
      return new ProgramDiagnostic (code, ProgramDiagnostic.SEVERITY_ERROR,
         message, reason, span, sourceLineText (span), markerLineText (span));
   } // end of programDiagnostic()

   /**
    * Returns the raw source line for a span.
    * @param span source span
    * @return source line or null
    */
   String sourceLineText (SourceSpan span) {
      if ((span == null) | !span.hasLocation()) return null;
      if ((span.startLine < 1) | (span.startLine > sourceLines.size()))
         return null;
      return (String)sourceLines.get (span.startLine - 1);
   } // end of sourceLineText()

   /**
    * Builds a caret marker for one source line.
    * @param span source span on that line
    * @return caret marker
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
      for (int i = 0; i < width; i++) {
         result.append ('^');
      }
      return result.toString();
   } // end of markerLineText()

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
