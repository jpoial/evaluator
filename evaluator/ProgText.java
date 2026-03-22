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

   /** collected diagnostics when recovery continues after an error */
   LinkedList<ProgramDiagnostic> diagnostics =
      new LinkedList<ProgramDiagnostic>();

   static class CompileContext {
      String wordName;
      SourceWord definingWord;
      SourceWord nameToken;
      String legacyTerminator = null;
      SpecList rootSeq = new SpecList();
      LinkedList<CompileFrame> controlStack = new LinkedList<CompileFrame>();

      CompileContext (String name, SourceWord defWord, SourceWord defName,
         String terminator) {
         wordName = name;
         definingWord = defWord;
         nameToken = defName;
         legacyTerminator = terminator;
      } // end of constructor
   } // end of CompileContext

   static abstract class CompileFrame {
      SourceWord openerToken;

      CompileFrame (SourceWord token) {
         openerToken = token;
      } // end of constructor

      abstract SpecList currentSeq();
   } // end of CompileFrame

   static class StructureFrame extends CompileFrame {
      String openRole;
      LinkedList<ControlStructure> candidates =
         new LinkedList<ControlStructure>();
      LinkedList<SpecList> segmentSeqs = new LinkedList<SpecList>();
      int seenBoundaries = 0;

      StructureFrame (SourceWord token, String role,
         LinkedList<ControlStructure> structures) {
         super (token);
         openRole = role == null ? "" : role;
         if (structures != null) candidates.addAll (structures);
         segmentSeqs.add (new SpecList());
      } // end of constructor

      boolean canAdvance (String role) {
         Iterator<ControlStructure> it = candidates.iterator();
         while (it.hasNext()) {
            if (((ControlStructure)it.next()).canAdvanceWithRole (role,
                  seenBoundaries))
               return true;
         }
         return false;
      } // end of canAdvance()

      void advance (String role) {
         LinkedList<ControlStructure> filtered =
            new LinkedList<ControlStructure>();
         Iterator<ControlStructure> it = candidates.iterator();
         while (it.hasNext()) {
            ControlStructure structure = (ControlStructure)it.next();
            if (structure.canAdvanceWithRole (role, seenBoundaries))
               filtered.add (structure);
         }
         candidates = filtered;
         seenBoundaries++;
         segmentSeqs.add (new SpecList());
      } // end of advance()

      ControlStructure resolveClose (String role) {
         ControlStructure result = null;
         Iterator<ControlStructure> it = candidates.iterator();
         while (it.hasNext()) {
            ControlStructure structure = (ControlStructure)it.next();
            if (!structure.canCloseWithRole (role, seenBoundaries)) continue;
            if (result != null) return null;
            result = structure;
         }
         return result;
      } // end of resolveClose()

      String [] expectedNextRoles() {
         LinkedList<String> roles = new LinkedList<String>();
         Iterator<ControlStructure> it = candidates.iterator();
         while (it.hasNext()) {
            ControlStructure structure = (ControlStructure)it.next();
            if (structure.canAdvanceWithRole (
                  structure.boundaryCount() > seenBoundaries ?
                  structure.boundaryRoleAt (seenBoundaries) : "",
                  seenBoundaries) &&
                (structure.boundaryCount() > seenBoundaries))
               addRoleOnce (roles, structure.boundaryRoleAt (seenBoundaries));
            if (structure.canCloseWithRole (structure.closeRole,
                  seenBoundaries))
               addRoleOnce (roles, structure.closeRole);
         }
         String [] result = new String [roles.size()];
         for (int i = 0; i < roles.size(); i++)
            result [i] = (String)roles.get (i);
         return result;
      } // end of expectedNextRoles()

      boolean countsAsDoLoop() {
         return Spec.CONTROL_DO.equals (openRole);
      } // end of countsAsDoLoop()

      SpecList currentSeq() {
         return (SpecList)segmentSeqs.getLast();
      } // end of currentSeq()

      static void addRoleOnce (LinkedList<String> roles, String role) {
         if (role == null || role.length() == 0) return;
         if (!roles.contains (role)) roles.add (role);
      } // end of addRoleOnce()
   } // end of StructureFrame

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
         interpretSource (scanner, ts, ss, fileName);
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
      interpretSource (scanner, ts, ss, "<command line>");
   } // end of constructor

   /**
    * Returns original source text before parsing.
    * @return source text
    */
   String sourceText() {
      return sourceText;
   } // end of sourceText()

   /**
    * Tells whether program checking collected any diagnostics.
    * @return true when at least one diagnostic is present
    */
   boolean hasDiagnostics() {
      return diagnostics.size() > 0;
   } // end of hasDiagnostics()

   /**
    * Returns a copy of the collected diagnostics.
    * @return collected diagnostics
    */
   LinkedList<ProgramDiagnostic> diagnostics() {
      return new LinkedList<ProgramDiagnostic> (diagnostics);
   } // end of diagnostics()

   /**
    * Stores one collected diagnostic when recovery is active.
    * @param diagnostic structured diagnostic
    */
   void addDiagnostic (ProgramDiagnostic diagnostic) {
      if (diagnostic != null) diagnostics.add (diagnostic);
   } // end of addDiagnostic()

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
      if (SpecSet.isDecimalDoubleLiteral (word)) {
         Spec literalSpec = ss.getLiteral (SpecSet.DOUBLE_LITERAL_KIND);
         if (literalSpec == null)
            throw programError ("lookup.literal-spec-missing",
               "No literal specification found for double literal " + word +
               " in " + context,
               "define LITERAL DOUBLE ( -- <type> ) in the current specs file",
               span);
         validateLiteralRuntimeSpec (SpecSet.DOUBLE_LITERAL_KIND, literalSpec,
            word, span, context);
         return literalSpec;
      }
      if (SpecSet.isDecimalIntegerLiteral (word)) {
         Spec literalSpec = ss.getLiteral (SpecSet.INTEGER_LITERAL_KIND);
         if (literalSpec == null)
            throw programError ("lookup.literal-spec-missing",
               "No literal specification found for integer literal " + word +
               " in " + context,
               "define LITERAL INTEGER ( -- <type> ) in the current specs file",
               span);
         validateLiteralRuntimeSpec (SpecSet.INTEGER_LITERAL_KIND, literalSpec,
            word, span, context);
         return literalSpec;
      }
      throw missingWord (word, span, context);
   } // end of resolveWordSpec()

   /**
    * Runs one explicit outer interpreter over the source scanner.
    * @param scanner source scanner
    * @param ts type system used for evaluation
    * @param ss current specification set
    * @param sourceName file name or other source label
    */
   void interpretSource (TextScanner scanner, TypeSystem ts, SpecSet ss,
      String sourceName) {
      CompileContext compile = null;
      SourceWord token = null;
      while ((token = scanner.nextProgramWord()) != null) {
         try {
            if (compile == null) {
               compile = interpretOneWord (token, scanner, ts, ss, sourceName);
            } else {
               compile = compileOneWord (token, scanner, compile, ts, ss,
                  sourceName);
            }
         } catch (ProgramException e) {
            addDiagnostic (e.diagnostic());
            if (compile != null)
               compile = recoverCompileState (scanner, compile, token,
                  (Spec)ss.get (token.text), ss);
         }
      }
      if (compile != null) {
         if (compile.controlStack.size() > 0) {
            addDiagnostic (missingTerminatorForFrame (
               (CompileFrame)compile.controlStack.getLast(), compile.wordName,
               ss).diagnostic());
         } else {
            addDiagnostic (programDiagnostic ("parse.unterminated-definition",
               "Unterminated definition for " + compile.wordName, "",
               compile.nameToken.span));
         }
      }
   } // end of interpretSource()

   /**
    * Executes one word in interpretation state.
    * @param token current source word
    * @param scanner source scanner
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName source label for diagnostics
    * @return new compile context or null when staying in interpretation state
    */
   CompileContext interpretOneWord (SourceWord token, TextScanner scanner,
      TypeSystem ts, SpecSet ss, String sourceName) {
      Spec spec = (Spec)ss.get (token.text);
      if ((spec != null) && !spec.allowedInInterpretState())
         throw programError ("parse.unsupported-interpret-word",
            token.text + " is not supported in interpretation state", "",
            token.span);
      if ((spec != null) && spec.isImmediate())
         return executeImmediateInterpretWord (token, spec, scanner, ts, ss,
            sourceName);
      Spec runtime = resolveRuntimeWordSpec (token, spec,
         "top-level program", ts, ss, 0);
      addCheckedTopLevelWord (token.text, token.span, runtime, ts, ss);
      return null;
   } // end of interpretOneWord()

   /**
    * Executes one word while compiling a colon definition.
    * @param token current source word
    * @param scanner source scanner
    * @param compile current compile context
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName source label for diagnostics
    * @return updated compile context, or null when the definition ends
    */
   CompileContext compileOneWord (SourceWord token, TextScanner scanner,
      CompileContext compile, TypeSystem ts, SpecSet ss, String sourceName) {
      Spec spec = (Spec)ss.get (token.text);
      if (isLegacyDefinitionTerminator (token, spec, compile)) {
         if (compile.controlStack.size() > 0)
            throw missingTerminatorForFrame (
               (CompileFrame)compile.controlStack.getLast(), compile.wordName,
               ss);
         finishDefinition (compile, ts, ss);
         return null;
      }
      if ((spec != null) && !spec.allowedInCompileState())
         throw programError ("parse.unsupported-compile-word",
            token.text + " is not supported in compilation state of " +
            compile.wordName, "", token.span);
      if ((spec != null) && spec.isImmediate())
         return executeImmediateCompileWord (token, spec, scanner, compile,
            ts, ss, sourceName);
      Spec runtime = resolveRuntimeWordSpec (token, spec,
         "definition of " + compile.wordName, ts, ss, currentDoDepth (
         compile));
      appendCompiledWord (compile, token.text, token.span, runtime);
      return compile;
   } // end of compileOneWord()

   /**
    * Executes one immediate word in interpretation state.
    * @param token current source word
    * @param spec specification of the word
    * @param scanner source scanner
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName source label for diagnostics
    * @return new compile context or null
    */
   CompileContext executeImmediateInterpretWord (SourceWord token, Spec spec,
      TextScanner scanner, TypeSystem ts, SpecSet ss, String sourceName) {
      if (spec.definesWord()) {
         if (Spec.DEFINE_COLON.equals (spec.defineMode))
            return startDefinition (token, spec, scanner, ss);
         if (Spec.DEFINE_CONSTANT.equals (spec.defineMode)) {
            defineConstant (scanner, token, spec, ts, ss);
            return null;
         }
         if (Spec.DEFINE_VARIABLE.equals (spec.defineMode)) {
            defineVariable (scanner, token, spec, ts, ss);
            return null;
         }
         throw programError ("parse.unsupported-defining-word",
            token.text + " is not a supported defining word", "",
            token.span);
      }
      if (spec.isControlWord())
         throw unexpectedToken (token.text, token.span, sourceName);
      SourceWord fullToken = consumeImmediateInput (token, spec, scanner);
      addCheckedTopLevelWord (token.text, fullToken.span, runtimeSpecClone (
         spec, ts), ts, ss);
      return null;
   } // end of executeImmediateInterpretWord()

   /**
    * Executes one immediate word in compilation state.
    * @param token current source word
    * @param spec specification of the word
    * @param scanner source scanner
    * @param compile current compile context
    * @param ts type system to use
    * @param ss current specification set
    * @param sourceName source label for diagnostics
    * @return updated compile context, or null when the definition ends
    */
   CompileContext executeImmediateCompileWord (SourceWord token, Spec spec,
      TextScanner scanner, CompileContext compile, TypeSystem ts, SpecSet ss,
      String sourceName) {
      if (spec.definesWord())
         throw programError ("parse.unsupported-defining-word",
            token.text + " is not supported inside definition of " +
            compile.wordName, "", token.span);
      if (spec.isControlWord())
         return executeImmediateControlWord (token, spec, compile, ts, ss);
      if (spec.consumesUntil() || spec.consumesNextWord()) {
         SourceWord fullToken = consumeImmediateInput (token, spec, scanner);
         appendCompiledWord (compile, token.text, fullToken.span,
            runtimeSpecClone (spec, ts));
         return compile;
      }
      throw programError ("parse.unsupported-immediate-word",
         token.text + " is IMMEDIATE but has no compile-time behavior in " +
         "definition of " + compile.wordName, "", token.span);
   } // end of executeImmediateCompileWord()

   /**
    * Executes one control word during compilation.
    * @param token current source word
    * @param spec control-word specification
    * @param compile current compile context
    * @param ts type system to use
    * @param ss current specification set
    * @return updated compile context or null when the definition ends
    */
   CompileContext executeImmediateControlWord (SourceWord token, Spec spec,
      CompileContext compile, TypeSystem ts, SpecSet ss) {
      String role = spec.controlMode;
      if (Spec.CONTROL_END.equals (role)) {
         if (compile.controlStack.size() > 0)
            throw missingTerminatorForFrame (
               (CompileFrame)compile.controlStack.getLast(), compile.wordName,
               ss);
         finishDefinition (compile, ts, ss);
         return null;
      }
      if (ss.hasOpenControlRole (role)) {
         compile.controlStack.add (new StructureFrame (token, role,
            ss.structuresForOpenRole (role)));
         return compile;
      }
      if (compile.controlStack.size() == 0)
         throw unexpectedToken (token.text, token.span,
            "definition of " + compile.wordName);
      CompileFrame frame = (CompileFrame)compile.controlStack.getLast();
      if (!(frame instanceof StructureFrame))
         throw unexpectedToken (token.text, token.span,
            "definition of " + compile.wordName);
      StructureFrame structureFrame = (StructureFrame)frame;
      if (structureFrame.canAdvance (role)) {
         structureFrame.advance (role);
         return compile;
      }
      ControlStructure structure = structureFrame.resolveClose (role);
      if (structure != null) {
         compile.controlStack.removeLast();
         currentCompileSequence (compile).add (buildStructureEffect (structure,
            structureFrame, token, ts, ss, compile.wordName));
         return compile;
      }
      throw unexpectedToken (token.text, token.span,
         "definition of " + compile.wordName);
   } // end of executeImmediateControlWord()

   /**
    * Starts compilation of a new colon definition.
    * @param token defining word token
    * @param spec defining-word specification
    * @param scanner source scanner
    * @param ss current specification set
    * @return new compile context
    */
   CompileContext startDefinition (SourceWord token, Spec spec,
      TextScanner scanner, SpecSet ss) {
      if ((spec.leftSide.size() != 0) || (spec.rightSide.size() != 0))
         throw programError ("define.colon-shape",
            token.text + " must have defining shape ( -- )", "",
            token.span);
      SourceWord nameToken = nextDefinedName (scanner, token, token.text, ss);
      String legacyTerminator = null;
      if (Spec.PARSE_DEFINITION.equals (spec.parseMode))
         legacyTerminator = definitionTerminator (spec);
      return new CompileContext (nameToken.text.trim(), token, nameToken,
         legacyTerminator);
   } // end of startDefinition()

   /**
    * Finishes the current colon definition and stores its effect.
    * @param compile compile context
    * @param ts type system to use
    * @param ss current specification set
    */
   void finishDefinition (CompileContext compile, TypeSystem ts, SpecSet ss) {
      Spec defSpec = evaluateSpecList (compile.rootSeq, ts, ss,
         "linear part of definition " + compile.wordName);
      ss.put (compile.wordName, defSpec);
   } // end of finishDefinition()

   /**
    * Appends one compiled runtime effect to the current active sequence.
    * @param compile current compile context
    * @param word surface word text
    * @param span source span of the compiled word
    * @param spec runtime effect
    */
   void appendCompiledWord (CompileContext compile, String word,
      SourceSpan span, Spec spec) {
      currentCompileSequence (compile).add (((Spec)spec.clone()).withOrigin (
         span, word));
   } // end of appendCompiledWord()

   /**
    * Returns the sequence that currently receives compiled words.
    * @param compile current compile context
    * @return active sequence
    */
   SpecList currentCompileSequence (CompileContext compile) {
      if (compile == null)
         throw new RuntimeException ("Missing compile context.");
      if (compile.controlStack.size() == 0) return compile.rootSeq;
      return ((CompileFrame)compile.controlStack.getLast()).currentSeq();
   } // end of currentCompileSequence()

   /**
    * Counts surrounding DO..LOOP structures in the current compile context.
    * @param compile current compile context
    * @return active counted-loop depth
    */
   int currentDoDepth (CompileContext compile) {
      int result = 0;
      Iterator<CompileFrame> it = compile.controlStack.iterator();
      while (it.hasNext()) {
         CompileFrame frame = (CompileFrame)it.next();
         if ((frame instanceof StructureFrame) &&
             ((StructureFrame)frame).countsAsDoLoop())
            result++;
      }
      return result;
   } // end of currentDoDepth()

   /**
    * Resolves the runtime effect of one source word in the current state.
    * @param token source word
    * @param directSpec already resolved dictionary entry, or null
    * @param context surrounding context for diagnostics
    * @param ts current type system
    * @param ss current specification set
    * @param doDepth active counted-loop depth
    * @return runtime stack effect
    */
   Spec resolveRuntimeWordSpec (SourceWord token, Spec directSpec,
      String context, TypeSystem ts, SpecSet ss, int doDepth) {
      Spec controlSpec = directSpec;
      if ((controlSpec == null) || !controlSpec.isControlWord())
         controlSpec = controlWordSpec (token.text, ss);
      if (controlSpec != null) {
         if (Spec.CONTROL_INDEX.equals (controlSpec.controlMode)) {
            if (doDepth <= 0)
               throw unexpectedToken (token.text, token.span, context);
            return controlRuntimeSpec (Spec.CONTROL_INDEX, ts, ss,
               token.span);
         }
         throw unexpectedToken (token.text, token.span, context);
      }
      return resolveWordSpec (token.text, token.span, context, ts, ss);
   } // end of resolveRuntimeWordSpec()

   /**
    * Lets one immediate parser word consume its following raw source text.
    * @param token already scanned head word
    * @param spec word specification
    * @param scanner source scanner
    * @return widened token span covering the consumed source
    */
   SourceWord consumeImmediateInput (SourceWord token, Spec spec,
      TextScanner scanner) {
      if (spec == null) return token;
      if (spec.consumesNextWord()) {
         SourceWord parsedWord = scanner.nextProgramWord();
         if (parsedWord == null)
            throw programError ("parse.missing-parser-word",
               "Missing word after parser word " + token.text, "",
               token.span);
         return new SourceWord (token.text, SourceSpan.covering (token.span,
            parsedWord.span));
      }
      if (!spec.consumesUntil()) return token;
      scanner.skipWhitespace();
      SourceWord parsed = scanner.parseUntil (spec.parseString);
      if (parsed == null) {
         if ("\n".equals (spec.parseString) && scanner.atEnd())
            return new SourceWord (token.text, SourceSpan.covering (
               token.span, scanner.lastConsumedSpan()));
         throw programError ("parse.missing-scanner-end",
            "Missing closing " + TextScanner.quotedText (spec.parseString) +
            " for scanner word " + token.text, "", token.span);
      }
      return new SourceWord (token.text, SourceSpan.covering (token.span,
         scanner.lastConsumedSpan()));
   } // end of consumeImmediateInput()

   /**
    * Tells whether the current token closes a legacy colon definition.
    * @param token current source word
    * @param spec current dictionary entry, if any
    * @param compile current compile context
    * @return true for a legacy terminator token
    */
   boolean isLegacyDefinitionTerminator (SourceWord token, Spec spec,
      CompileContext compile) {
      if ((compile == null) || (compile.legacyTerminator == null) ||
          (compile.legacyTerminator.length() == 0))
         return false;
      if ((spec != null) && spec.hasControlMode (Spec.CONTROL_END))
         return false;
      return compile.legacyTerminator.equals (canonicalWord (token.text));
   } // end of isLegacyDefinitionTerminator()

   /**
    * Recovers from a failed definition by skipping to its closing word.
    * @param scanner source scanner
    * @param compile failed compile context
    * @param token token that triggered the failure
    * @param spec specification of the failing token, if any
    * @param ss current specification set
    * @return null after abandoning the invalid definition
    */
   CompileContext recoverCompileState (TextScanner scanner,
      CompileContext compile, SourceWord token, Spec spec, SpecSet ss) {
      if ((token != null) && isDefinitionEndToken (token, spec, compile))
         return null;
      int nestedDefinitions = 0;
      if ((token != null) && isDefinitionStarterWord (
          canonicalWord (token.text), ss))
         nestedDefinitions = 1;
      if ((token != null) && (spec != null))
         skipRecoveryPayload (token, spec, scanner, ss);
      if (scanner.atEnd()) return null;
      SourceWord skipped = null;
      while ((skipped = scanner.nextProgramWord()) != null) {
         Spec skippedSpec = (Spec)ss.get (skipped.text);
         if (isDefinitionStarterWord (canonicalWord (skipped.text), ss)) {
            nestedDefinitions++;
            skipRecoveryPayload (skipped, skippedSpec, scanner, ss);
            continue;
         }
         if (isDefinitionEndToken (skipped, skippedSpec, compile)) {
            if (nestedDefinitions > 0) {
               nestedDefinitions--;
               continue;
            }
            return null;
         }
         skipRecoveryPayload (skipped, skippedSpec, scanner, ss);
      }
      return null;
   } // end of recoverCompileState()

   /**
    * Skips any extra source text consumed by one immediate or defining word
    * while recovery is abandoning the rest of the current definition.
    * @param token already scanned head word
    * @param spec resolved specification, if any
    * @param scanner source scanner
    * @param ss current specification set
    */
   void skipRecoveryPayload (SourceWord token, Spec spec, TextScanner scanner,
      SpecSet ss) {
      if ((token == null) || (spec == null)) return;
      try {
         if (spec.definesWord()) {
            nextDefinedName (scanner, token, token.text, ss);
            return;
         }
         if (spec.isImmediate())
            consumeImmediateInput (token, spec, scanner);
      } catch (ProgramException e) {
         if (scanner.atEnd()) return;
      }
   } // end of skipRecoveryPayload()

   /**
    * Tells whether the token closes the current definition for recovery.
    * @param token current token
    * @param spec resolved specification, if any
    * @param compile current compile context
    * @return true when the token ends the abandoned definition
    */
   boolean isDefinitionEndToken (SourceWord token, Spec spec,
      CompileContext compile) {
      if ((spec != null) && spec.hasControlMode (Spec.CONTROL_END))
         return true;
      return isLegacyDefinitionTerminator (token, spec, compile);
   } // end of isDefinitionEndToken()

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
    * Adds one top-level word and drops it again if it breaks the linear part.
    * @param word display text
    * @param span source span
    * @param spec resolved stack effect
    * @param ts type system to use
    * @param ss current specification set
    */
   void addCheckedTopLevelWord (String word, SourceSpan span, Spec spec,
      TypeSystem ts, SpecSet ss) {
      addTopLevelWord (word, span, spec);
      try {
         currentTopLevelEffect (ts, ss);
      } catch (ProgramException e) {
         discardLastTopLevelWord();
         addDiagnostic (e.diagnostic());
      }
   } // end of addCheckedTopLevelWord()

   /**
    * Removes the last collected top-level word after a recovered failure.
    */
   void discardLastTopLevelWord() {
      if (size() > 0) removeLast();
      if (wordSpans.size() > 0) wordSpans.removeLast();
      if (wordSpecs.size() > 0) wordSpecs.removeLast();
   } // end of discardLastTopLevelWord()

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
    * Reads the next definition name directly from the source scanner.
    * @param scanner source scanner
    * @param definingToken defining word token
    * @param definingWord text such as CONSTANT or VARIABLE
    * @param ss current specification set
    * @return parsed name token
    */
   SourceWord nextDefinedName (TextScanner scanner, SourceWord definingToken,
      String definingWord, SpecSet ss) {
      SourceWord result = scanner.nextProgramWord();
      if (result == null)
         throw programError ("parse.missing-word-name",
            "Missing word name after " + definingWord, "",
            definingToken.span);
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
    * Handles top-level CONSTANT in the outer interpreter by consuming one
    * runtime value and defining a new zero-argument word that returns a value
    * of the consumed type.
    * @param scanner source scanner
    * @param constantToken CONSTANT token
    * @param ts type system to use
    * @param ss current specification set
    */
   void defineConstant (TextScanner scanner, SourceWord constantToken,
      Spec definerSpec, TypeSystem ts, SpecSet ss) {
      SourceWord nameToken = nextDefinedName (scanner, constantToken,
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
            " requires one value on the stack", "", definerSpan);
      TypeSymbol top = (TypeSymbol)prefixEffect.rightSide.lastElement();
      TypeSymbol expected = (TypeSymbol)definerSpec.leftSide.firstElement();
      if (ts.relation (top.ftype, expected.ftype) == 0)
         throw programError ("define.constant-type",
            constantToken.text + " " + nameToken.text +
            " expects a value comparable with " + expected.ftype +
            " but the current stack provides " + top.ftype, "",
            definerSpan);
      Spec constSpec = SpecSet.parseSpec ("-- " + top.ftype, ts,
         nameToken.span);
      ss.put (nameToken.text, constSpec);
      Spec consumeSpec = SpecSet.parseSpec (top.ftype + " --", ts,
         definerSpan);
      addTopLevelWord ("", definerSpan,
         consumeSpec.withOrigin (definerSpan,
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
      SourceSpan definerSpan = SourceSpan.covering (variableToken.span,
         nameToken.span);
      if ((definerSpec.leftSide.size() != 0) |
          (definerSpec.rightSide.size() != 1))
         throw programError ("define.variable-shape",
            variableToken.text + " must have defining shape ( -- y )", "",
            definerSpan);
      ss.put (nameToken.text, runtimeSpecClone (definerSpec, ts)
         .withOrigin (nameToken.span, nameToken.text));
   } // end of defineVariable()

   /**
    * Handles top-level VARIABLE in the outer interpreter by defining a new
    * word that returns an aligned data-space address.
    * @param scanner source scanner
    * @param variableToken VARIABLE token
    * @param ts type system to use
    * @param ss current specification set
    */
   void defineVariable (TextScanner scanner, SourceWord variableToken,
      Spec definerSpec, TypeSystem ts, SpecSet ss) {
      SourceWord nameToken = nextDefinedName (scanner, variableToken,
         variableToken.text, ss);
      SourceSpan definerSpan = SourceSpan.covering (variableToken.span,
         nameToken.span);
      if ((definerSpec.leftSide.size() != 0) |
          (definerSpec.rightSide.size() != 1))
         throw programError ("define.variable-shape",
            variableToken.text + " must have defining shape ( -- y )", "",
            definerSpan);
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
    * Guards literal specifications when they come from programmatic setup.
    * Loader-side validation already rejects such shapes for file-based specs.
    * @param kind literal kind name
    * @param spec literal specification
    * @param token literal token as written in the program
    * @param span source span of the token
    * @param context surrounding context for diagnostics
    */
   void validateLiteralRuntimeSpec (String kind, Spec spec, String token,
      SourceSpan span, String context) {
      if ((spec != null) && (spec.leftSide.size() != 0))
         throw programError ("lookup.literal-spec-invalid",
            "Literal specification for " + kind + " cannot consume stack " +
            "input, but " + token + " is used in " + context,
            "", span);
   } // end of validateLiteralRuntimeSpec()

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
      Iterator<Map.Entry<String, Spec>> it = ss.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<String, Spec> entry = (Map.Entry<String, Spec>)it.next();
         Spec spec = (Spec)entry.getValue();
         if ((spec != null) && spec.hasControlMode (Spec.CONTROL_END) &&
             word.equals (canonicalWord ((String)entry.getKey())))
            return true;
         if ((spec != null) && Spec.DEFINE_COLON.equals (spec.defineMode) &&
             Spec.PARSE_DEFINITION.equals (spec.parseMode) &&
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
    * Builds one declared control-structure effect from its captured segments.
    * @param structure structure declaration
    * @param frame captured structure frame
    * @param closingToken closing token
    * @param ts type system to use
    * @param ss current specification set
    * @param wordName current definition name
    * @return resulting structure effect
    */
   Spec buildStructureEffect (ControlStructure structure,
      StructureFrame frame, SourceWord closingToken, TypeSystem ts,
      SpecSet ss, String wordName) {
      SourceSpan structureSpan = SourceSpan.covering (frame.openerToken.span,
         closingToken.span);
      String label = structureLabel (ss, structure.labelRoles (
         frame.seenBoundaries));
      LinkedList<Spec> segmentEffects = new LinkedList<Spec>();
      Iterator<SpecList> it = frame.segmentSeqs.iterator();
      while (it.hasNext()) {
         segmentEffects.add (evaluateSpecList ((SpecList)it.next(), ts, ss,
            "linear part of definition " + wordName));
      }
      return evaluateStructureExpr (structure.meaning, structure,
         segmentEffects,
         label, ts, ss, wordName, structureSpan)
         .withOrigin (structureSpan, label);
   } // end of buildStructureEffect()

   /**
    * Returns the empty stack effect used for missing optional segments.
    * @param ts type system to use
    * @return empty effect
    */
   Spec emptyStructureEffect (TypeSystem ts) {
      return (new Spec (ts)).withOrigin (null, "empty");
   } // end of emptyStructureEffect()

   /**
    * Evaluates one declarative control-meaning expression.
    * @param expr expression to evaluate
    * @param alpha first captured segment
    * @param beta second captured segment or empty effect
    * @param label human-readable structure label
    * @param ts type system to use
    * @param ss current specification set
    * @param wordName current definition name
    * @param structureSpan source span of the full structure
    * @return resulting effect
    */
   Spec evaluateStructureExpr (ControlStructure.EffectExpr expr,
      ControlStructure structure, LinkedList<Spec> segmentEffects,
      String label, TypeSystem ts, SpecSet ss, String wordName,
      SourceSpan structureSpan) {
      if (expr instanceof ControlStructure.EmptyExpr)
         return emptyStructureEffect (ts).withOrigin (structureSpan, label);
      if (expr instanceof ControlStructure.SegmentExpr) {
         String name = ((ControlStructure.SegmentExpr)expr).segmentName;
         int index = structure.segmentIndexOf (name);
         if ((index >= 0) && (index < segmentEffects.size()))
            return ((Spec)((Spec)segmentEffects.get (index)).clone())
               .withOrigin (structureSpan, label);
         if (index >= 0)
            return emptyStructureEffect (ts).withOrigin (structureSpan, label);
         throw new RuntimeException ("Unknown structure segment " + name +
            " in " + label);
      }
      if (expr instanceof ControlStructure.ControlExpr) {
         String role = resolveStructureControlRole (structure,
            ((ControlStructure.ControlExpr)expr).role);
         String word = controlWordName (role, ss);
         return controlRuntimeSpec (role, ts, ss, structureSpan)
            .withOrigin (structureSpan, word);
      }
      if (expr instanceof ControlStructure.SeqExpr) {
         SpecList seq = new SpecList();
         Iterator<ControlStructure.EffectExpr> it =
            ((ControlStructure.SeqExpr)expr).parts.iterator();
         while (it.hasNext()) {
            Spec part = evaluateStructureExpr (
               (ControlStructure.EffectExpr)it.next(), structure,
               segmentEffects, label, ts, ss, wordName, structureSpan);
            seq.add (((Spec)part.clone()).withOrigin (structureSpan, label));
         }
         return evaluateSpecList (seq, ts, ss,
            label + " in definition " + wordName)
            .withOrigin (structureSpan, label);
      }
      if (expr instanceof ControlStructure.GlbExpr) {
         ControlStructure.GlbExpr glbExpr = (ControlStructure.GlbExpr)expr;
         Spec left = evaluateStructureExpr (glbExpr.left, structure,
            segmentEffects, label, ts, ss, wordName, structureSpan);
         Spec right = evaluateStructureExpr (glbExpr.right, structure,
            segmentEffects, label, ts, ss, wordName, structureSpan);
         Spec merged = left.glb (right, ts, ss);
         if (merged == null)
            throw programError ("type.control-glb-clash",
               "Non-comparable alternatives in " + label +
               " of definition " + wordName, "left effect " +
               left.toString().trim() + ", right effect " +
               right.toString().trim() + " cannot be merged",
               structureSpan);
         return merged.withOrigin (structureSpan, label);
      }
      if (expr instanceof ControlStructure.StarExpr) {
         Spec inner = evaluateStructureExpr (
            ((ControlStructure.StarExpr)expr).inner, structure, segmentEffects,
            label, ts, ss, wordName, structureSpan);
         Spec loop = inner.piStar (ts, ss);
         if (loop == null)
            throw programError ("type.control-star-clash",
               "Non-idempotent repeated effect in " + label +
               " of definition " + wordName, "effect " +
               inner.toString().trim(), structureSpan);
         return loop.withOrigin (structureSpan, label);
      }
      throw new RuntimeException ("Unknown control expression in " + label);
   } // end of evaluateStructureExpr()

   /**
    * Resolves OPEN/MID/CLOSE pseudo-roles inside one structure meaning.
    * @param structure structure declaration
    * @param role role text from the meaning expression
    * @return concrete control role
    */
   String resolveStructureControlRole (ControlStructure structure,
      String role) {
      if ("OPEN".equals (role)) return structure.openRole;
      if ("MID".equals (role) && (structure.boundaryCount() == 1))
         return structure.boundaryRoleAt (0);
      if ("CLOSE".equals (role)) return structure.closeRole;
      return role;
   } // end of resolveStructureControlRole()

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
      String preferred = preferredControlWordName (role);
      if ((preferred != null) && hasControlWordName (preferred, role, ss))
         return preferred;
      String best = null;
      Iterator<Map.Entry<String, Spec>> it = ss.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<String, Spec> entry = (Map.Entry<String, Spec>)it.next();
         Spec spec = (Spec)entry.getValue();
         if ((spec != null) && spec.hasControlMode (role)) {
            String word = (String)entry.getKey();
            if ((best == null) || (word.compareTo (best) < 0))
               best = word;
         }
      }
      if (best != null) return best;
      return role;
   } // end of controlWordName()

   /**
    * Returns the preferred surface spelling of one control role.
    * @param role control role
    * @return preferred control word text or null
    */
   String preferredControlWordName (String role) {
      if (Spec.CONTROL_IF.equals (role)) return "IF";
      if (Spec.CONTROL_ELSE.equals (role)) return "ELSE";
      if (Spec.CONTROL_FI.equals (role)) return "THEN";
      if (Spec.CONTROL_BEGIN.equals (role)) return "BEGIN";
      if (Spec.CONTROL_WHILE.equals (role)) return "WHILE";
      if (Spec.CONTROL_REPEAT.equals (role)) return "REPEAT";
      if (Spec.CONTROL_AGAIN.equals (role)) return "AGAIN";
      if (Spec.CONTROL_UNTIL.equals (role)) return "UNTIL";
      if (Spec.CONTROL_DO.equals (role)) return "DO";
      if (Spec.CONTROL_LOOP.equals (role)) return "LOOP";
      if (Spec.CONTROL_INDEX.equals (role)) return "I";
      if (Spec.CONTROL_END.equals (role)) return ";";
      return null;
   } // end of preferredControlWordName()

   /**
    * Tells whether the requested control role has the given surface spelling.
    * @param word candidate surface word
    * @param role control role
    * @param ss current specification set
    * @return true when the word exists for the role
    */
   boolean hasControlWordName (String word, String role, SpecSet ss) {
      Spec spec = (Spec)ss.get (word);
      return (spec != null) && spec.hasControlMode (role);
   } // end of hasControlWordName()

   /**
    * Builds a readable label for one control structure.
    * @param ss current specification set
    * @param roles control roles in structural order
    * @return label such as IF...ELSE...THEN
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
    * Canonicalizes one program word for case-insensitive Forth parsing.
    * @param word original token text
    * @return canonical uppercase form
    */
   static String canonicalWord (String word) {
      return SpecSet.canonicalWord (word);
   } // end of canonicalWord()

   /**
    * Creates the missing-terminator diagnostic for the innermost open frame.
    * @param frame open compile-time frame
    * @param wordName current definition
    * @param ss current specification set
    * @return diagnostic exception
    */
   ProgramException missingTerminatorForFrame (CompileFrame frame,
      String wordName, SpecSet ss) {
      if (frame instanceof StructureFrame) {
         StructureFrame structure = (StructureFrame)frame;
         String [] expected = structure.expectedNextRoles();
         if (expected.length == 1)
            return missingTerminator (expected [0], structure.openRole,
               frame.openerToken.span, wordName, ss);
         if (expected.length > 1)
            return missingTerminator (expected, structure.openRole,
               frame.openerToken.span, wordName, ss);
      }
      return programError ("parse.missing-terminator",
         "Missing end of definition for " + wordName, "",
         frame.openerToken.span);
   } // end of missingTerminatorForFrame()

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
