// file: ProgText.java

package evaluator;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

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

   /** original program source split into lines */
   LinkedList<String> sourceLines = new LinkedList<String>();

   /** source spans of top-level program words */
   LinkedList<SourceSpan> wordSpans = new LinkedList<SourceSpan>();

   /** already resolved stack effects for top-level program words */
   LinkedList<Spec> wordSpecs = new LinkedList<Spec>();

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
      try {
         TextScanner scanner = TextScanner.fromFile (fileName);
         sourceText = scanner.sourceText();
         sourceLines = scanner.sourceLines();
         parseTokens (scanProgramWords (scanner, ss), ts, ss, fileName);
      } catch (IOException e) {
         throw new ProgramException (new ProgramDiagnostic (
            "program.read-failed", ProgramDiagnostic.SEVERITY_ERROR,
            "Unable to read program text from " + fileName, "", null, null,
            null), e);
      }
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
      StringBuffer source = new StringBuffer ("");
      for (int i=0; i<text.length; i++) {
         if (i > 0) source.append (" ");
         source.append (text [i]);
      }
      TextScanner scanner = new TextScanner ("<command line>",
         source.toString());
      sourceText = scanner.sourceText();
      sourceLines = scanner.sourceLines();
      parseTokens (scanProgramWords (scanner, ss), ts, ss, "<command line>");
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
    * Returns the resolved stack effect of a top-level word.
    * @param index program word index
    * @return stack effect or null
    */
   Spec wordSpec (int index) {
      if ((index < 0) | (index >= wordSpecs.size())) return null;
      return (Spec)wordSpecs.get (index);
   } // end of wordSpec()

   /**
    * Resolves one source token as either a known word or a decimal integer
    * literal.
    * @param word source token text
    * @param span source location of the token
    * @param context surrounding context for diagnostics
    * @param ts current type system
    * @param ss current specification set
    * @return stack effect of the token
    */
   Spec resolveWordSpec (String word, SourceSpan span, String context,
      TypeSystem ts, SpecSet ss) {
      Spec spec = (Spec)ss.get (word);
      if (spec != null) return spec;
      if (SpecSet.isDecimalIntegerLiteral (word)) {
         Spec literalSpec = ss.getLiteral (SpecSet.INTEGER_LITERAL_KIND);
         if (literalSpec == null)
            throw programError ("lookup.literal-spec-missing",
               "No literal specification found for integer literal " + word +
               " in " + context,
               "define LITERAL INTEGER ( -- <type> ) in the current specs file",
               span);
         return literalSpec;
      }
      throw missingWord (word, span, context);
   } // end of resolveWordSpec()

   /**
    * Scans program words sequentially, letting parser words consume their
    * following source text according to the loaded specification set.
    * @param scanner source scanner
    * @param ss current specification set
    * @return tokenized program words
    */
   LinkedList<SourceWord> scanProgramWords (TextScanner scanner, SpecSet ss) {
      LinkedList<SourceWord> result = new LinkedList<SourceWord>();
      SourceWord token = null;
      while ((token = scanner.nextWord()) != null) {
         result.add (consumeScannerTail (token, scanner, ss));
      }
      return result;
   } // end of scanProgramWords()

   /**
    * Lets one parser word consume its following raw source text.
    * @param token already scanned word token
    * @param scanner source scanner
    * @param ss current specification set
    * @return original token or token widened to cover the consumed text
    */
   SourceWord consumeScannerTail (SourceWord token, TextScanner scanner,
      SpecSet ss) {
      Spec spec = (Spec)ss.get (token.text);
      if ((spec == null) || !spec.consumesUntil())
         return token;
      scanner.skipWhitespace();
      SourceWord parsed = scanner.parseUntil (spec.parseString);
      if (parsed == null)
         throw programError ("parse.missing-scanner-end",
            "Missing closing " + TextScanner.quotedText (spec.parseString) +
            " for scanner word " + token.text, "", token.span);
      return new SourceWord (token.text, SourceSpan.covering (token.span,
         scanner.lastConsumedSpan()));
   } // end of consumeScannerTail()

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
      String currentDefTerminator = null;
      LinkedList<SourceWord> defBody = null;
      while (tokens.size() > 0) {
         SourceWord token = (SourceWord)tokens.removeFirst();
         String word = canonicalWord (token.text);
         Spec parserSpec = parserWordSpec (token.text, ss);
         Spec controlSpec = controlWordSpec (token.text, ss);
         if (currentDef == null) {
            if ((parserSpec != null) && parserSpec.definesWord()) {
               if (!parserSpec.allowedInInterpretState())
                  throw programError ("parse.unsupported-interpret-word",
                     token.text + " is not supported in interpretation state",
                     "",
                     token.span);
               if (Spec.DEFINE_COLON.equals (parserSpec.defineMode)) {
                  currentDefToken = nextDefinedName (tokens, token, token.text,
                     ss);
                  currentDef = currentDefToken.text.trim();
                  currentDefTerminator = definitionTerminator (parserSpec);
                  defBody = new LinkedList<SourceWord>();
               } else if (Spec.DEFINE_CONSTANT.equals
                     (parserSpec.defineMode)) {
                  defineConstant (tokens, token, parserSpec, ts, ss);
               } else if (Spec.DEFINE_VARIABLE.equals
                     (parserSpec.defineMode)) {
                  defineVariable (tokens, token, parserSpec, ts, ss);
               } else {
                  throw programError ("parse.unsupported-defining-word",
                     token.text + " is not a supported defining word", "",
                     token.span);
               }
            } else {
               if ((parserSpec != null) && !parserSpec.allowedInInterpretState())
                  throw programError ("parse.unsupported-interpret-word",
                     token.text + " is not supported in interpretation state",
                     "",
                     token.span);
               if (controlSpec != null)
                  throw unexpectedToken (token.text, token.span, sourceName);
               if (isDefinitionTerminatorWord (word, ss))
                  throw unexpectedToken (token.text, token.span, sourceName);
               addTopLevelWord (token.text, token.span,
                  resolveWordSpec (token.text, token.span,
                     "top-level program", ts, ss));
            }
         } else {
            if ((parserSpec != null) && !parserSpec.allowedInCompileState())
               throw programError ("parse.unsupported-compile-word",
                  token.text + " is not supported in compilation state of " +
                  currentDef, "", token.span);
            if ((parserSpec != null) && parserSpec.definesWord()) {
               if (Spec.DEFINE_COLON.equals (parserSpec.defineMode))
                  throw programError ("parse.nested-definition",
                     "Nested definitions are not supported in definition of " +
                     currentDef, "", token.span);
               throw programError ("parse.unsupported-defining-word",
                  token.text + " is not supported inside definition of " +
                  currentDef, "", token.span);
            }
            if (word.equals (currentDefTerminator)) {
               defineWord (currentDef, defBody, ts, ss, sourceName);
               currentDef = null;
               currentDefToken = null;
               currentDefTerminator = null;
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
    * Adds one top-level runtime word together with its resolved stack effect.
    * Hidden bookkeeping operations may use an empty display word.
    * @param word display text
    * @param span source span
    * @param spec resolved stack effect
    */
   void addTopLevelWord (String word, SourceSpan span, Spec spec) {
      add (word == null ? "" : word);
      wordSpans.add (span);
      if (spec == null) {
         wordSpecs.add (null);
      } else {
         wordSpecs.add (((Spec)spec.clone()).withOrigin (span, word));
      }
   } // end of addTopLevelWord()

   /**
    * Evaluates the already parsed top-level runtime sequence so far.
    * @param ts type system to use
    * @param ss current specification set
    * @return current cumulative effect
    */
   Spec currentTopLevelEffect (TypeSystem ts, SpecSet ss) {
      SpecList prefix = new SpecList();
      Iterator<Spec> it = wordSpecs.iterator();
      while (it.hasNext()) {
         Spec sp = (Spec)it.next();
         if (sp != null) prefix.add ((Spec)sp.clone());
      }
      Spec result = prefix.evaluate (ts, ss);
      if (result == null)
         throw prefix.typeClash ("linear part of the top-level program", this);
      return result;
   } // end of currentTopLevelEffect()

   /**
    * Reads the next definition name after a defining word.
    * @param tokens remaining top-level tokens
    * @param definingToken defining word token
    * @param definingWord text such as CONSTANT or VARIABLE
    * @return parsed name token
    */
   SourceWord nextDefinedName (LinkedList<SourceWord> tokens,
      SourceWord definingToken, String definingWord, SpecSet ss) {
      if (tokens.size() == 0)
         throw programError ("parse.missing-word-name",
            "Missing word name after " + definingWord, "",
            definingToken.span);
      SourceWord result = (SourceWord)tokens.removeFirst();
      String name = result.text == null ? "" : result.text.trim();
      if (name.length() == 0)
         throw programError ("parse.empty-word-name",
            "Empty word name after " + definingWord, "", result.span);
      if (isDefinitionStarterOrTerminatorWord (canonicalWord (name), ss))
         throw programError ("parse.illegal-word-name",
            "Illegal word name " + name, "", result.span);
      return result;
   } // end of nextDefinedName()

   /**
    * Handles top-level CONSTANT by consuming one runtime value and defining a
    * new zero-argument word that returns a value of the consumed type.
    * @param tokens remaining top-level tokens
    * @param constantToken CONSTANT token
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName file name or other source label for diagnostics
    */
   void defineConstant (LinkedList<SourceWord> tokens, SourceWord constantToken,
      Spec definerSpec, TypeSystem ts, SpecSet ss) {
      SourceWord nameToken = nextDefinedName (tokens, constantToken,
         constantToken.text, ss);
      SourceSpan definerSpan = SourceSpan.covering (constantToken.span,
         nameToken.span);
      if ((definerSpec.leftSide.size() != 1) |
          (definerSpec.rightSide.size() != 0))
         throw programError ("define.constant-shape",
            constantToken.text + " must have defining shape ( x -- )", "",
            definerSpan);
      Spec prefixEffect = currentTopLevelEffect (ts, ss);
      if (prefixEffect.rightSide.size() == 0)
         throw programError ("define.constant-underflow",
            constantToken.text + " " + nameToken.text +
            " requires one value on the stack",
            "", definerSpan);
      TypeSymbol top = (TypeSymbol)prefixEffect.rightSide.lastElement();
      TypeSymbol expected = (TypeSymbol)definerSpec.leftSide.firstElement();
      if (ts.relation (top.ftype, expected.ftype) == 0)
         throw programError ("define.constant-type",
            constantToken.text + " " + nameToken.text +
            " expects a value comparable with " + expected.ftype +
            " but the current stack provides " + top.ftype,
            "", definerSpan);
      Spec constSpec = SpecSet.parseSpec ("-- " + top.ftype, ts,
         nameToken.span);
      ss.put (nameToken.text, constSpec);
      Spec consumeSpec = SpecSet.parseSpec (top.ftype + " --", ts,
         definerSpan);
      addTopLevelWord ("", constantToken.span,
         consumeSpec.withOrigin (constantToken.span,
            constantToken.text + " " + nameToken.text));
   } // end of defineConstant()

   /**
    * Handles top-level VARIABLE by defining a new word that returns an
    * aligned data-space address.
    * @param tokens remaining top-level tokens
    * @param variableToken VARIABLE token
    * @param ts type system to use
    * @param ss current specification set
    */
   void defineVariable (LinkedList<SourceWord> tokens,
      SourceWord variableToken, Spec definerSpec, TypeSystem ts, SpecSet ss) {
      SourceWord nameToken = nextDefinedName (tokens, variableToken,
         variableToken.text, ss);
      ss.put (nameToken.text, runtimeSpecClone (definerSpec, ts)
         .withOrigin (nameToken.span, nameToken.text));
   } // end of defineVariable()

   /**
    * Returns parser metadata for the given word, when present.
    * @param word word text
    * @param ss current specification set
    * @return parser spec or null
    */
   Spec parserWordSpec (String word, SpecSet ss) {
      Spec spec = (Spec)ss.get (word);
      if ((spec == null) || !spec.isParserWord()) return null;
      return spec;
   } // end of parserWordSpec()

   /**
    * Returns structured-control metadata for the given word, when present.
    * @param word word text
    * @param ss current specification set
    * @return control spec or null
    */
   Spec controlWordSpec (String word, SpecSet ss) {
      Spec spec = (Spec)ss.get (word);
      if ((spec == null) || !spec.isControlWord()) return null;
      return spec;
   } // end of controlWordSpec()

   /**
    * Creates a plain runtime clone of a parser-word specification.
    * @param spec source specification
    * @param ts type system to use
    * @return clone without parser metadata
    */
   Spec runtimeSpecClone (Spec spec, TypeSystem ts) {
      Spec result = new Spec ((Tvector)spec.leftSide.clone(),
         (Tvector)spec.rightSide.clone(), ts, "", 0);
      result.maxPos();
      return result;
   } // end of runtimeSpecClone()

   /**
    * Returns the terminator word of a definition-starting parser word.
    * @param spec definition parser spec
    * @return canonical terminating word
    */
   String definitionTerminator (Spec spec) {
      if ((spec == null) || (spec.parseString == null) ||
          (spec.parseString.length() == 0))
         return ";";
      return canonicalWord (spec.parseString);
   } // end of definitionTerminator()

   /**
    * Tells whether the given word opens a named definition.
    * @param word canonical word text
    * @param ss current specification set
    * @return true for definition starters
    */
   boolean isDefinitionStarterWord (String word, SpecSet ss) {
      if (word == null) return false;
      Iterator<Map.Entry<String, Spec>> it = ss.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<String, Spec> entry = (Map.Entry<String, Spec>)it.next();
         Spec spec = (Spec)entry.getValue();
         if ((spec != null) && Spec.DEFINE_COLON.equals (spec.defineMode) &&
             word.equals (canonicalWord ((String)entry.getKey())))
            return true;
      }
      return false;
   } // end of isDefinitionStarterWord()

   /**
    * Tells whether the given word is a known definition terminator.
    * @param word canonical word text
    * @param ss current specification set
    * @return true when the word closes a definition
    */
   boolean isDefinitionTerminatorWord (String word, SpecSet ss) {
      if (word == null) return false;
      Iterator<Spec> it = ss.values().iterator();
      while (it.hasNext()) {
         Spec spec = (Spec)it.next();
         if ((spec != null) && Spec.DEFINE_COLON.equals (spec.defineMode) &&
             word.equals (definitionTerminator (spec)))
            return true;
      }
      return false;
   } // end of isDefinitionTerminatorWord()

   /**
    * Tells whether the word is reserved as a definition opener or terminator.
    * @param word canonical word text
    * @param ss current specification set
    * @return true if the word is reserved
    */
   boolean isDefinitionStarterOrTerminatorWord (String word, SpecSet ss) {
      return isDefinitionStarterWord (word, ss) ||
         isDefinitionTerminatorWord (word, ss);
   } // end of isDefinitionStarterOrTerminatorWord()

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
      String[] stopModes, int doDepth) {
      SpecList seq = new SpecList();
      SourceSpan seqSpan = null;
      while (tokens.size() > 0) {
         SourceWord token = (SourceWord)tokens.removeFirst();
         Spec controlSpec = controlWordSpec (token.text, ss);
         String controlMode = controlSpec == null ? "" : controlSpec.controlMode;
         if (isStopControlMode (controlMode, stopModes))
            return new ParseResult (evaluateSpecList (seq, ts, ss,
               "linear part of definition " + wordName), token, seqSpan);
         if (Spec.CONTROL_IF.equals (controlMode)) {
            ParseResult thenPart = parseDefinitionSequence (tokens, ts, ss,
               sourceName, wordName, new String [] {
               Spec.CONTROL_ELSE, Spec.CONTROL_FI}, doDepth);
            boolean hasElse = false;
            ParseResult elsePart = new ParseResult ((new Spec (ts)).withOrigin
               (null, "empty branch"), thenPart.stopToken, null);
            if (tokenHasControlMode (thenPart.stopToken, ss,
                  Spec.CONTROL_ELSE)) {
               hasElse = true;
               elsePart = parseDefinitionSequence (tokens, ts, ss, sourceName,
                  wordName, new String [] {Spec.CONTROL_FI}, doDepth);
            }
            if (!tokenHasControlMode (elsePart.stopToken, ss,
                  Spec.CONTROL_FI))
               throw missingTerminator (Spec.CONTROL_FI, Spec.CONTROL_IF,
                  token.span, wordName, ss);
            SourceSpan ifSpan = SourceSpan.covering (token.span,
               elsePart.stopToken.span);
            Spec ifEffect = buildIfEffect (thenPart.effect, elsePart.effect,
               ts, ss, wordName, ifSpan, hasElse);
            seq.add (ifEffect);
            seqSpan = SourceSpan.covering (seqSpan, ifSpan);
         } else if (Spec.CONTROL_BEGIN.equals (controlMode)) {
            ParseResult alphaPart = parseDefinitionSequence (tokens, ts, ss,
               sourceName, wordName,
               new String [] {Spec.CONTROL_WHILE, Spec.CONTROL_AGAIN,
               Spec.CONTROL_UNTIL}, doDepth);
            if (tokenHasControlMode (alphaPart.stopToken, ss,
                  Spec.CONTROL_WHILE)) {
               ParseResult betaPart = parseDefinitionSequence (tokens, ts, ss,
                  sourceName, wordName, new String [] {Spec.CONTROL_REPEAT},
                  doDepth);
               if (!tokenHasControlMode (betaPart.stopToken, ss,
                     Spec.CONTROL_REPEAT))
                  throw missingTerminator (Spec.CONTROL_REPEAT,
                     Spec.CONTROL_BEGIN, token.span, wordName, ss);
               SourceSpan loopSpan = SourceSpan.covering (token.span,
                  betaPart.stopToken.span);
               Spec loopEffect = buildWhileEffect (alphaPart.effect,
                  betaPart.effect, ts, ss, wordName, loopSpan);
               seq.add (loopEffect);
               seqSpan = SourceSpan.covering (seqSpan, loopSpan);
            } else if (tokenHasControlMode (alphaPart.stopToken, ss,
                  Spec.CONTROL_AGAIN)) {
               SourceSpan loopSpan = SourceSpan.covering (token.span,
                  alphaPart.stopToken.span);
               Spec loopEffect = buildAgainEffect (alphaPart.effect, ts, ss,
                  wordName, loopSpan);
               seq.add (loopEffect);
               seqSpan = SourceSpan.covering (seqSpan, loopSpan);
            } else if (tokenHasControlMode (alphaPart.stopToken, ss,
                  Spec.CONTROL_UNTIL)) {
               SourceSpan loopSpan = SourceSpan.covering (token.span,
                  alphaPart.stopToken.span);
               Spec loopEffect = buildUntilEffect (alphaPart.effect, ts, ss,
                  wordName, loopSpan);
               seq.add (loopEffect);
               seqSpan = SourceSpan.covering (seqSpan, loopSpan);
            } else {
               throw missingTerminator (new String [] {Spec.CONTROL_WHILE,
                  Spec.CONTROL_AGAIN, Spec.CONTROL_UNTIL},
                  Spec.CONTROL_BEGIN, token.span, wordName, ss);
            }
         } else if (Spec.CONTROL_DO.equals (controlMode)) {
            ParseResult bodyPart = parseDefinitionSequence (tokens, ts, ss,
               sourceName, wordName, new String [] {Spec.CONTROL_LOOP},
               doDepth + 1);
            if (!tokenHasControlMode (bodyPart.stopToken, ss,
                  Spec.CONTROL_LOOP))
               throw missingTerminator (Spec.CONTROL_LOOP, Spec.CONTROL_DO,
                  token.span, wordName, ss);
            SourceSpan loopSpan = SourceSpan.covering (token.span,
               bodyPart.stopToken.span);
            Spec loopEffect = buildDoLoopEffect (bodyPart.effect, ts, ss,
               wordName, loopSpan);
            seq.add (loopEffect);
            seqSpan = SourceSpan.covering (seqSpan, loopSpan);
         } else {
            Spec parserSpec = parserWordSpec (token.text, ss);
            if ((parserSpec != null) && !parserSpec.allowedInCompileState())
               throw programError ("parse.unsupported-compile-word",
                  token.text + " is not supported in compilation state of " +
                  wordName, "", token.span);
            if ((parserSpec != null) && parserSpec.definesWord()) {
               if (Spec.DEFINE_COLON.equals (parserSpec.defineMode))
                  throw programError ("parse.nested-definition",
                     "Nested definitions are not supported in definition of " +
                     wordName, "", token.span);
               throw programError ("parse.unsupported-defining-word",
                  token.text + " is not supported inside definition of " +
                  wordName, "", token.span);
            }
            if ((controlSpec != null) &&
                !Spec.CONTROL_INDEX.equals (controlMode))
               throw unexpectedToken (token.text, token.span,
                  "definition of " + wordName);
            Spec sp = null;
            if (Spec.CONTROL_INDEX.equals (controlMode)) {
               if (doDepth <= 0)
                  throw unexpectedToken (token.text, token.span,
                     "definition of " + wordName);
               sp = controlRuntimeSpec (Spec.CONTROL_INDEX, ts, ss,
                  token.span);
            } else {
               sp = resolveWordSpec (token.text, token.span,
                  "definition of " + wordName, ts, ss);
            }
            seq.add (((Spec)sp.clone()).withOrigin (token.span, token.text));
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
      String label = hasElse ? structureLabel (ss, new String [] {
         Spec.CONTROL_IF, Spec.CONTROL_ELSE, Spec.CONTROL_FI}) :
         structureLabel (ss, new String [] {Spec.CONTROL_IF,
         Spec.CONTROL_FI});
      Spec merged = thenEffect.glb (elseEffect, ts, ss);
      if (merged == null)
         throw programError ("type.if-branch-clash",
            "Non-comparable branches in " + label + " of definition " +
            wordName, "then branch " + thenEffect.toString().trim() +
            ", else branch " + elseEffect.toString().trim() +
            " cannot be merged", structureSpan);
      SpecList seq = new SpecList();
      seq.add (controlRuntimeSpec (Spec.CONTROL_IF, ts, ss, structureSpan)
         .withOrigin (structureSpan, label));
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
      String whileWord = controlWordName (Spec.CONTROL_WHILE, ss);
      String label = structureLabel (ss, new String [] {Spec.CONTROL_BEGIN,
         Spec.CONTROL_WHILE, Spec.CONTROL_REPEAT});
      SpecList alphaSeq = new SpecList();
      alphaSeq.add ((Spec)alphaEffect.clone());
      alphaSeq.add (controlRuntimeSpec (Spec.CONTROL_WHILE, ts, ss,
         structureSpan).withOrigin (structureSpan, whileWord));
      Spec alphaTest = evaluateSpecList (alphaSeq, ts, ss,
         whileWord + " loop test in definition " + wordName);
      Spec alphaLoop = alphaTest.piStar (ts, ss);
      if (alphaLoop == null)
         throw programError ("type.loop-prefix-non-idempotent",
            "Non-idempotent loop prefix in " + label + " of definition " +
            wordName, "prefix effect " +
            alphaTest.toString().trim(), structureSpan);
      Spec betaLoop = betaEffect.piStar (ts, ss);
      if (betaLoop == null)
         throw programError ("type.loop-body-non-idempotent",
            "Non-idempotent loop body in " + label + " of definition " +
            wordName, "body effect " +
            betaEffect.toString().trim(), structureSpan);
      SpecList loopSeq = new SpecList();
      loopSeq.add (((Spec)alphaLoop.clone()).withOrigin (structureSpan,
         label));
      loopSeq.add (((Spec)betaLoop.clone()).withOrigin (structureSpan,
         label));
      return evaluateSpecList (loopSeq, ts, ss,
         label + " in definition " + wordName).withOrigin (
         structureSpan, label);
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
      String label = structureLabel (ss, new String [] {Spec.CONTROL_BEGIN,
         Spec.CONTROL_AGAIN});
      Spec bodyLoop = bodyEffect.piStar (ts, ss);
      if (bodyLoop == null)
         throw programError ("type.loop-body-non-idempotent",
            "Non-idempotent loop body in " + label + " of definition " +
            wordName, "body effect " + bodyEffect.toString().trim(),
            structureSpan);
      return bodyLoop.withOrigin (structureSpan, label);
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
      String untilWord = controlWordName (Spec.CONTROL_UNTIL, ss);
      String label = structureLabel (ss, new String [] {Spec.CONTROL_BEGIN,
         Spec.CONTROL_UNTIL});
      SpecList testSeq = new SpecList();
      testSeq.add ((Spec)bodyEffect.clone());
      testSeq.add (controlRuntimeSpec (Spec.CONTROL_UNTIL, ts, ss,
         structureSpan).withOrigin (structureSpan, untilWord));
      Spec loopTest = evaluateSpecList (testSeq, ts, ss,
         untilWord + " loop test in definition " + wordName);
      Spec untilLoop = loopTest.piStar (ts, ss);
      if (untilLoop == null)
         throw programError ("type.loop-body-non-idempotent",
            "Non-idempotent loop body in " + label + " of definition " +
            wordName, "body and flag-test effect " +
            loopTest.toString().trim(), structureSpan);
      return untilLoop.withOrigin (structureSpan, label);
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
      String label = structureLabel (ss, new String [] {Spec.CONTROL_DO,
         Spec.CONTROL_LOOP});
      Spec bodyLoop = bodyEffect.piStar (ts, ss);
      if (bodyLoop == null)
         throw programError ("type.loop-body-non-idempotent",
            "Non-idempotent loop body in " + label + " of definition " +
            wordName, "body effect " + bodyEffect.toString().trim(),
            structureSpan);
      SpecList loopSeq = new SpecList();
      loopSeq.add (controlRuntimeSpec (Spec.CONTROL_DO, ts, ss,
         structureSpan).withOrigin (structureSpan, label));
      loopSeq.add (((Spec)bodyLoop.clone()).withOrigin (structureSpan, label));
      return evaluateSpecList (loopSeq, ts, ss,
         label + " in definition " + wordName).withOrigin (structureSpan,
         label);
   } // end of buildDoLoopEffect()

   /**
    * Returns the runtime specification associated with one control role.
    * @param role control role
    * @param ts type system to use
    * @param ss current specification set
    * @param span source span for fallback diagnostics
    * @return runtime effect
    */
   Spec controlRuntimeSpec (String role, TypeSystem ts, SpecSet ss,
      SourceSpan span) {
      Spec spec = controlWordSpecByRole (role, ss);
      if (spec != null) return runtimeSpecClone (spec, ts);
      if (Spec.CONTROL_DO.equals (role))
         return SpecSet.parseSpec ("n[2] n[1] --", ts, span);
      if (Spec.CONTROL_INDEX.equals (role))
         return SpecSet.parseSpec ("-- n", ts, span);
      return SpecSet.parseSpec ("flag --", ts, span);
   } // end of controlRuntimeSpec()

   /**
    * Returns the specification that declares the requested control role.
    * @param role control role
    * @param ss current specification set
    * @return control spec or null
    */
   Spec controlWordSpecByRole (String role, SpecSet ss) {
      Iterator<Spec> it = ss.values().iterator();
      while (it.hasNext()) {
         Spec spec = (Spec)it.next();
         if ((spec != null) && spec.hasControlMode (role)) return spec;
      }
      return null;
   } // end of controlWordSpecByRole()

   /**
    * Returns one configured surface word for the requested control role.
    * @param role control role
    * @param ss current specification set
    * @return configured word text or the role itself
    */
   String controlWordName (String role, SpecSet ss) {
      Iterator<Map.Entry<String, Spec>> it = ss.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<String, Spec> entry = (Map.Entry<String, Spec>)it.next();
         Spec spec = (Spec)entry.getValue();
         if ((spec != null) && spec.hasControlMode (role))
            return (String)entry.getKey();
      }
      return role;
   } // end of controlWordName()

   /**
    * Builds a readable label for one control structure.
    * @param ss current specification set
    * @param roles control roles in structural order
    * @return label such as IF...ELSE...FI
    */
   String structureLabel (SpecSet ss, String[] roles) {
      StringBuffer result = new StringBuffer ("");
      for (int i = 0; i < roles.length; i++) {
         if (i > 0) result.append ("...");
         result.append (controlWordName (roles [i], ss));
      }
      return result.toString();
   } // end of structureLabel()

   /**
    * Builds readable alternative text for diagnostics.
    * @param ss current specification set
    * @param roles control roles
    * @return comma-separated alternatives
    */
   String controlAlternativesText (SpecSet ss, String[] roles) {
      StringBuffer result = new StringBuffer ("");
      for (int i = 0; i < roles.length; i++) {
         if (i > 0) {
            if (i == roles.length - 1) {
               result.append (roles.length == 2 ? " or " : ", or ");
            } else {
               result.append (", ");
            }
         }
         result.append (controlWordName (roles [i], ss));
      }
      return result.toString();
   } // end of controlAlternativesText()

   /**
    * Tells whether the current control role ends the parsed sequence.
    * @param controlMode current control role
    * @param stopModes active closing roles
    * @return true if the role closes the sequence
    */
   boolean isStopControlMode (String controlMode, String[] stopModes) {
      for (int i = 0; i < stopModes.length; i++) {
         if (stopModes [i].equals (controlMode)) return true;
      }
      return false;
   } // end of isStopControlMode()

   /**
    * Canonicalizes one program word for case-insensitive Forth parsing.
    * @param word original token text
    * @return canonical uppercase form
    */
   static String canonicalWord (String word) {
      return SpecSet.canonicalWord (word);
   } // end of canonicalWord()

   /**
    * Tells whether a parsed token matches the given keyword.
    * @param token parsed token
    * @param expected canonical keyword
    * @return true if token matches keyword case-insensitively
    */
   boolean tokenHasControlMode (SourceWord token, SpecSet ss, String expected) {
      if (token == null) return false;
      Spec spec = controlWordSpec (token.text, ss);
      if (spec == null) return false;
      return spec.hasControlMode (expected);
   } // end of tokenHasControlMode()

   /**
    * Creates a missing-terminator diagnostic.
    * @param terminator required closing token
    * @param opener opening structure
    * @param openerSpan opening token span
    * @param wordName current definition
    * @return diagnostic exception
    */
   ProgramException missingTerminator (String terminatorRole,
      String openerRole, SourceSpan openerSpan, String wordName, SpecSet ss) {
      return programError ("parse.missing-terminator", "Missing " +
         controlWordName (terminatorRole, ss) + " for " +
         controlWordName (openerRole, ss) + " in definition of " + wordName,
         "", openerSpan);
   } // end of missingTerminator()

   /**
    * Creates a missing-terminator diagnostic for several alternatives.
    * @param terminatorRoles acceptable closing roles
    * @param openerRole opening role
    * @param openerSpan opening token span
    * @param wordName current definition
    * @param ss current specification set
    * @return diagnostic exception
    */
   ProgramException missingTerminator (String[] terminatorRoles,
      String openerRole, SourceSpan openerSpan, String wordName, SpecSet ss) {
      return programError ("parse.missing-terminator", "Missing " +
         controlAlternativesText (ss, terminatorRoles) + " for " +
         controlWordName (openerRole, ss) + " in definition of " + wordName,
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
         String current = it.next().toString();
         if (current.trim().length() == 0) continue;
         result.append (current + " ");
      }
      return result.toString();
   } // end of toString()

} // end of ProgText

// end of file
