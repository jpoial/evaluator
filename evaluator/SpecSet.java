
// file: SpecSet.java

package evaluator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Set of specifications hashed using operation names (words).
 * @author Jaanus Poial
 * @version 0.6
 * @since 1.5
 */
public class SpecSet extends Hashtable<String, Spec> {
    
    static final long serialVersionUID = 0xaabbcc;
    static final String INTEGER_LITERAL_KIND = "INTEGER";

   Hashtable<String, Spec> literalSpecs;

   SpecSet() {
      super();
      literalSpecs = new Hashtable<String, Spec>();
   } // end of constructor

   /**
    * Reads specifications from the file using given type system.
    * @param fileName  local file name
    * @param ts  type system used in specifications
    */
   SpecSet (String fileName, TypeSystem ts) {
      this();
      load (fileName, ts);
   } // end of constructor

   /**
    * Adds or replaces one specification in a safe way.
    * @param word Forth word
    * @param spec stack effect for the word
    * @return previous specification or null
    */
   public synchronized Spec put (String word, Spec spec) {
      if (word == null)
         throw new RuntimeException ("Word name must not be null.");
      String key = canonicalWord (word);
      if (key.length() == 0)
         throw new RuntimeException ("Word name must not be empty.");
      if (spec == null)
         throw new RuntimeException ("Specification for " + key +
            " must not be null.");
      return (Spec)super.put (key, (Spec)spec.clone());
   } // end of put()

   /**
    * Defines one specification using stack effect text.
    * @param word Forth word
    * @param specText stack effect text with or without parentheses
    * @param ts type system used for validation
    * @return previous specification or null
    */
   Spec define (String word, String specText, TypeSystem ts) {
      String body = specText.trim();
      if (body.startsWith ("(") && body.endsWith (")")) {
         body = body.substring (1, body.length()-1).trim();
      }
      return put (word, parseSpec (body, ts, "<memory>", 0));
   } // end of define()

   /**
    * Returns the specification associated with the given word name,
    * treating Forth words case-insensitively.
    * @param word lookup key
    * @return specification or null
    */
   public synchronized Spec get (Object word) {
      if (word instanceof String)
         return (Spec)super.get (canonicalWord ((String)word));
      return (Spec)super.get (word);
   } // end of get()

   /**
    * Tells whether the given word name is present, case-insensitively.
    * @param word lookup key
    * @return true if the word is present
    */
   public synchronized boolean containsKey (Object word) {
      if (word instanceof String)
         return super.containsKey (canonicalWord ((String)word));
      return super.containsKey (word);
   } // end of containsKey()

   /**
    * Adds or replaces one literal specification in a safe way.
    * @param kind literal kind name
    * @param spec stack effect for the literal
    * @return previous specification or null
    */
   public synchronized Spec putLiteral (String kind, Spec spec) {
      if (kind == null)
         throw new RuntimeException ("Literal kind must not be null.");
      String key = canonicalWord (kind);
      if (key.length() == 0)
         throw new RuntimeException ("Literal kind must not be empty.");
      if (spec == null)
         throw new RuntimeException ("Specification for literal " + key +
            " must not be null.");
      return (Spec)literalSpecs.put (key, (Spec)spec.clone());
   } // end of putLiteral()

   /**
    * Returns the specification associated with the given literal kind.
    * @param kind literal kind name
    * @return specification or null
    */
   public synchronized Spec getLiteral (String kind) {
      if (kind == null) return null;
      Spec result = (Spec)literalSpecs.get (canonicalWord (kind));
      if (result == null) return null;
      return (Spec)result.clone();
   } // end of getLiteral()

   /**
    * Tells whether the given literal kind is present.
    * @param kind lookup key
    * @return true if the literal kind is present
    */
   public synchronized boolean containsLiteral (String kind) {
      if (kind == null) return false;
      return literalSpecs.containsKey (canonicalWord (kind));
   } // end of containsLiteral()

   /**
    * Loads more specifications from a file into this set.
    * @param fileName local file name
    * @param ts type system used for validation
    */
   void load (String fileName, TypeSystem ts) {
      try {
         TextScanner scanner = TextScanner.fromFile (fileName);
         while (true) {
            scanner.skipIgnorable();
            if (scanner.atEnd()) return;
            SourceWord word = scanner.nextAtom ("(");
            if (word == null)
               throw new RuntimeException ("Missing word name in " +
                  scanner.positionSpan().startText());
            if (word.text.length() == 0)
               throw new RuntimeException ("Missing word name in " +
                  word.span.startText());
            if (!word.quoted && "LITERAL".equals (canonicalWord (word.text))) {
               SourceWord kind = scanner.nextAtom ("(");
               if (kind == null)
                  throw new RuntimeException ("Missing literal kind after " +
                     "LITERAL in " + word.span.startText());
               if (kind.text.length() == 0)
                  throw new RuntimeException ("Missing literal kind after " +
                     "LITERAL in " + kind.span.startText());
               if (containsLiteral (kind.text))
                  throw new RuntimeException ("Duplicate literal " +
                     "specification for " + kind.text + " in " +
                     kind.span.startText());
               SourceWord extra = scanner.nextAtom ("(");
               if (extra != null)
                  throw new RuntimeException ("Unexpected text " +
                     extra.text + " before stack effect in " +
                     extra.span.startText());
               SourceSpan openSpan = scanner.consumeChar ('(');
               if (openSpan == null)
                  throw new RuntimeException ("Malformed literal " +
                     "specification in " + kind.span.startText());
               SourceWord body = scanner.parseUntil (')');
               if (body == null)
                  throw new RuntimeException ("Malformed literal " +
                     "specification in " + openSpan.startText());
               LinkedList<SourceWord> trailing = scanner.nextLineAtoms();
               if ((trailing != null) && (trailing.size() > 0))
                  throw new RuntimeException ("Unexpected trailing text in " +
                     ((SourceWord)trailing.getFirst()).span.startText());
               putLiteral (kind.text, parseSpec (body.text.trim(), ts,
                  body.span));
               continue;
            }
            if (containsKey (word.text))
               throw new RuntimeException ("Duplicate specification for " +
                  word.text + " in " + word.span.startText());
            String parseMode = Spec.PARSE_NONE;
            String parseString = "";
            String defineMode = Spec.DEFINE_NONE;
            String controlMode = Spec.CONTROL_NONE;
            String stateMode = Spec.STATE_ANY;
            while (true) {
               SourceWord option = scanner.nextAtom ("(");
               if (option == null) break;
               String optionKey = canonicalWord (option.text);
               if ("PARSE".equals (optionKey)) {
                  if (parseMode.length() > 0)
                     throw new RuntimeException ("Duplicate PARSE clause in " +
                        option.span.startText());
                  SourceWord modeToken = scanner.nextAtom ("(");
                  if (modeToken == null)
                     throw new RuntimeException ("Missing parser mode after " +
                        "PARSE in " + option.span.startText());
                  parseMode = canonicalParseMode (modeToken.text);
                  if (parseMode == null)
                     throw new RuntimeException ("Unknown parser mode " +
                        modeToken.text + " in " + modeToken.span.startText());
                  if (parseModeNeedsArgument (parseMode)) {
                     SourceWord delimiter = scanner.nextAtom ("(");
                     if (delimiter == null)
                        throw new RuntimeException ("Missing parser " +
                           "delimiter after PARSE " + modeToken.text + " in " +
                           modeToken.span.startText());
                     parseString = resolveParseString (delimiter, ts);
                  }
                  continue;
               }
               if ("DEFINE".equals (optionKey)) {
                  if (defineMode.length() > 0)
                     throw new RuntimeException ("Duplicate DEFINE clause in " +
                        option.span.startText());
                  SourceWord modeToken = scanner.nextAtom ("(");
                  if (modeToken == null)
                     throw new RuntimeException ("Missing defining mode " +
                        "after DEFINE in " + option.span.startText());
                  defineMode = canonicalDefineMode (modeToken.text);
                  if (defineMode == null)
                     throw new RuntimeException ("Unknown defining mode " +
                        modeToken.text + " in " + modeToken.span.startText());
                  continue;
               }
               if ("CONTROL".equals (optionKey)) {
                  if (controlMode.length() > 0)
                     throw new RuntimeException ("Duplicate CONTROL clause in " +
                        option.span.startText());
                  SourceWord modeToken = scanner.nextAtom ("(");
                  if (modeToken == null)
                     throw new RuntimeException ("Missing control mode after " +
                        "CONTROL in " + option.span.startText());
                  controlMode = canonicalControlMode (modeToken.text);
                  if (controlMode == null)
                     throw new RuntimeException ("Unknown control mode " +
                        modeToken.text + " in " + modeToken.span.startText());
                  continue;
               }
               if ("STATE".equals (optionKey) | "CONTEXT".equals (optionKey)) {
                  if (stateMode.length() > 0)
                     throw new RuntimeException ("Duplicate STATE clause in " +
                        option.span.startText());
                  SourceWord modeToken = scanner.nextAtom ("(");
                  if (modeToken == null)
                     throw new RuntimeException ("Missing state mode after " +
                        option.text + " in " + option.span.startText());
                  stateMode = canonicalStateMode (modeToken.text);
                  if (stateMode == null)
                     throw new RuntimeException ("Unknown state mode " +
                        modeToken.text + " in " + modeToken.span.startText());
                  continue;
               }
               if ("SCAN".equals (optionKey)) {
                  if (parseMode.length() > 0)
                     throw new RuntimeException ("Duplicate scanner clause in " +
                        option.span.startText());
                  SourceWord delimiter = scanner.nextAtom ("(");
                  if (delimiter == null)
                     throw new RuntimeException ("Missing scanner delimiter " +
                        "after SCAN in " + option.span.startText());
                  parseMode = Spec.PARSE_UNTIL;
                  parseString = resolveParseString (delimiter, ts);
                  continue;
               }
               if (looksLikeScannerDelimiter (option, ts)) {
                  if (parseMode.length() > 0)
                     throw new RuntimeException ("Duplicate scanner clause in " +
                        option.span.startText());
                  parseMode = Spec.PARSE_UNTIL;
                  parseString = resolveParseString (option, ts);
                  continue;
               }
               throw new RuntimeException ("Unexpected text " +
                  option.text + " before stack effect in " +
                  option.span.startText());
            }
            SourceSpan openSpan = scanner.consumeChar ('(');
            if (openSpan == null)
               throw new RuntimeException ("Malformed specification in " +
                  word.span.startText());
            SourceWord body = scanner.parseUntil (')');
            if (body == null)
               throw new RuntimeException ("Malformed specification in " +
                  openSpan.startText());
            LinkedList<SourceWord> trailing = scanner.nextLineAtoms();
            if ((trailing != null) && (trailing.size() > 0))
               throw new RuntimeException ("Unexpected trailing text in " +
                  ((SourceWord)trailing.getFirst()).span.startText());
            validateParserMetadata (word.text, parseMode, parseString,
               defineMode, controlMode, stateMode, body.span);
            put (word.text, parseSpec (body.text.trim(), ts, body.span)
               .withParseMode (parseMode)
               .withParseString (parseString)
               .withDefineMode (defineMode)
               .withControlMode (controlMode)
               .withStateMode (stateMode));
         }
      } catch (IOException e) {
         throw new RuntimeException ("Unable to read specification set from "
            + fileName, e);
      }
   } // end of load()

   /**
    * Saves this set to a text file in a stable order.
    * @param fileName local file name
    */
   void save (String fileName) {
      BufferedWriter writer = null;
      String nl = System.getProperty ("line.separator");
      try {
         writer = new BufferedWriter (new FileWriter (fileName));
         Iterator<String> literalIt = sortedLiteralKinds().iterator();
         while (literalIt.hasNext()) {
            String kind = (String)literalIt.next();
            Spec spec = getLiteral (kind);
            writer.write ("LITERAL ");
            writer.write (formatWordForFile (kind));
            writer.write (" " + spec.toString().trim());
            writer.write (nl);
         }
         Iterator<String> it = sortedWords().iterator();
         while (it.hasNext()) {
            String word = (String)it.next();
            Spec spec = (Spec)get (word);
            writer.write (formatWordForFile (word));
            appendSpecMetadata (writer, spec);
            writer.write (" " + spec.toString().trim());
            writer.write (nl);
         }
      } catch (IOException e) {
         throw new RuntimeException ("Unable to save specification set to " +
            fileName, e);
      } finally {
         if (writer != null) {
            try {
               writer.close();
            } catch (IOException e) {
               // ignore close failure in demo code
            }
         }
      }
   } // end of save()

   /**
    * Parses one stack effect body from text between parentheses.
    * @param body effect text
    * @param ts typesystem to validate against
    * @param fileName source file name for diagnostics
    * @param lineNo source line number for diagnostics
    * @return parsed specification
    */
   static Spec parseSpec (String body, TypeSystem ts, String fileName,
      int lineNo) {
      return parseSpec (body, ts, new SourceSpan (fileName, lineNo, 1,
         lineNo, 1));
   } // end of parseSpec()

   /**
    * Parses one stack effect body from text between parentheses.
    * @param body effect text
    * @param ts typesystem to validate against
    * @param span source span for diagnostics
    * @return parsed specification
    */
   static Spec parseSpec (String body, TypeSystem ts, SourceSpan span) {
      int arrowPos = body.indexOf ("--");
      if (arrowPos < 0)
         throw new RuntimeException ("Missing -- in " +
            sourceLocationText (span));
      Tvector left = parseTypeList (body.substring (0, arrowPos).trim(),
         ts, span);
      Tvector right = parseTypeList (body.substring (arrowPos + 2).trim(),
         ts, span);
      Spec result = new Spec (left, right, ts, "", 0);
      result.maxPos();
      return result;
   } // end of parseSpec()

   /**
    * Parses one stack-state side of a specification.
    * @param text side of the specification
    * @param ts typesystem to validate against
    * @param fileName source file name for diagnostics
    * @param lineNo source line number for diagnostics
    * @return parsed list of type symbols
    */
   static Tvector parseTypeList (String text, TypeSystem ts, String fileName,
      int lineNo) {
      return parseTypeList (text, ts, new SourceSpan (fileName, lineNo, 1,
         lineNo, 1));
   } // end of parseTypeList()

   /**
    * Parses one stack-state side of a specification.
    * @param text side of the specification
    * @param ts typesystem to validate against
    * @param span source span for diagnostics
    * @return parsed list of type symbols
    */
   static Tvector parseTypeList (String text, TypeSystem ts,
      SourceSpan span) {
      Tvector result = new Tvector();
      if (text.length() == 0) return result;
      String[] tokens = text.split ("\\s+");
      for (int i = 0; i < tokens.length; i++) {
         result.add (parseTypeSymbol (tokens [i], ts, span));
      }
      return result;
   } // end of parseTypeList()

   /**
    * Parses one type symbol of the form type or type[index].
    * @param text symbol text
    * @param ts typesystem to validate against
    * @param fileName source file name for diagnostics
    * @param lineNo source line number for diagnostics
    * @return parsed symbol
    */
   static TypeSymbol parseTypeSymbol (String text, TypeSystem ts,
      String fileName, int lineNo) {
      return parseTypeSymbol (text, ts, new SourceSpan (fileName, lineNo, 1,
         lineNo, 1));
   } // end of parseTypeSymbol()

   /**
    * Parses one type symbol of the form type or type[index].
    * @param text symbol text
    * @param ts typesystem to validate against
    * @param span source span for diagnostics
    * @return parsed symbol
    */
   static TypeSymbol parseTypeSymbol (String text, TypeSystem ts,
      SourceSpan span) {
      String typeName = text;
      int position = 0;
      int p1 = text.indexOf ('[');
      if (p1 >= 0) {
         int p2 = text.indexOf (']', p1 + 1);
         if ((p2 != text.length()-1) | (p2 <= p1 + 1))
            throw new RuntimeException ("Malformed type symbol " + text +
               " in " + sourceLocationText (span));
         typeName = text.substring (0, p1);
         try {
            position = Integer.parseInt (text.substring (p1 + 1, p2));
         } catch (NumberFormatException e) {
            throw new RuntimeException ("Malformed wildcard index in " + text +
               " in " + sourceLocationText (span), e);
         }
         if (position < 0)
            throw new RuntimeException ("Negative wildcard index in " + text +
               " in " + sourceLocationText (span));
      }
      if (!ts.containsType (typeName))
         throw new RuntimeException ("Unknown type " + typeName + " in " +
            sourceLocationText (span));
      return new TypeSymbol (typeName, position, position > 0);
   } // end of parseTypeSymbol()

   /**
    * Renders a short source location for runtime parse errors.
    * @param span source span
    * @return location text
    */
   static String sourceLocationText (SourceSpan span) {
      if (span == null) return "<memory>";
      if (span.hasLocation()) return span.startText();
      return span.sourceName;
   } // end of sourceLocationText()

   /**
    * Resolves one scanner delimiter token from a specification file.
    * @param token delimiter token or named scanner
    * @param ts current type system
    * @return delimiter string
    */
   static String resolveParseString (SourceWord token, TypeSystem ts) {
      if (token == null)
         throw new RuntimeException ("Missing scanner delimiter.");
      if (token.quoted) return token.text;
      if ((ts != null) && ts.containsScanner (token.text))
         return ts.scannerDelimiter (token.text);
      return token.text;
   } // end of resolveParseString()

   /**
    * Tells whether the token is a decimal integer literal.
    * @param text source token
    * @return true for decimal integers such as 0, 123, -7, or +42
    */
   static boolean isDecimalIntegerLiteral (String text) {
      if ((text == null) | (text.length() == 0)) return false;
      int start = 0;
      char first = text.charAt (0);
      if ((first == '+') | (first == '-')) {
         if (text.length() == 1) return false;
         start = 1;
      }
      for (int i = start; i < text.length(); i++) {
         if (!Character.isDigit (text.charAt (i))) return false;
      }
      return true;
   } // end of isDecimalIntegerLiteral()

   /**
    * Tells whether one optional token can denote a scanner delimiter.
    * @param token optional token between word and stack effect
    * @param ts current type system
    * @return true if token denotes a scanner delimiter
    */
   static boolean looksLikeScannerDelimiter (SourceWord token, TypeSystem ts) {
      if (token == null) return false;
      if (token.quoted) return true;
      return (ts != null) && ts.containsScanner (token.text);
   } // end of looksLikeScannerDelimiter()

   /**
    * Canonicalizes one parser mode keyword.
    * @param mode mode text from the spec file
    * @return canonical parser mode or null when unknown
    */
   static String canonicalParseMode (String mode) {
      String key = canonicalWord (mode);
      if ("UNTIL".equals (key)) return Spec.PARSE_UNTIL;
      if ("WORD".equals (key)) return Spec.PARSE_WORD;
      if ("DEFINITION".equals (key)) return Spec.PARSE_DEFINITION;
      return null;
   } // end of canonicalParseMode()

   /**
    * Canonicalizes one defining mode keyword.
    * @param mode mode text from the spec file
    * @return canonical define mode or null when unknown
    */
   static String canonicalDefineMode (String mode) {
      String key = canonicalWord (mode);
      if ("COLON".equals (key)) return Spec.DEFINE_COLON;
      if ("CONSTANT".equals (key)) return Spec.DEFINE_CONSTANT;
      if ("VARIABLE".equals (key)) return Spec.DEFINE_VARIABLE;
      return null;
   } // end of canonicalDefineMode()

   /**
    * Canonicalizes one control mode keyword.
    * @param mode mode text from the spec file
    * @return canonical control mode or null when unknown
    */
   static String canonicalControlMode (String mode) {
      String key = canonicalWord (mode);
      if (Spec.CONTROL_IF.equals (key)) return Spec.CONTROL_IF;
      if (Spec.CONTROL_ELSE.equals (key)) return Spec.CONTROL_ELSE;
      if (Spec.CONTROL_FI.equals (key)) return Spec.CONTROL_FI;
      if (Spec.CONTROL_BEGIN.equals (key)) return Spec.CONTROL_BEGIN;
      if (Spec.CONTROL_WHILE.equals (key)) return Spec.CONTROL_WHILE;
      if (Spec.CONTROL_REPEAT.equals (key)) return Spec.CONTROL_REPEAT;
      if (Spec.CONTROL_AGAIN.equals (key)) return Spec.CONTROL_AGAIN;
      if (Spec.CONTROL_UNTIL.equals (key)) return Spec.CONTROL_UNTIL;
      if (Spec.CONTROL_DO.equals (key)) return Spec.CONTROL_DO;
      if (Spec.CONTROL_LOOP.equals (key)) return Spec.CONTROL_LOOP;
      if (Spec.CONTROL_INDEX.equals (key)) return Spec.CONTROL_INDEX;
      return null;
   } // end of canonicalControlMode()

   /**
    * Canonicalizes one usage-context keyword.
    * @param mode mode text from the spec file
    * @return canonical context mode or null when unknown
    */
   static String canonicalStateMode (String mode) {
      String key = canonicalWord (mode);
      if (Spec.STATE_INTERPRET.equals (key)) return Spec.STATE_INTERPRET;
      if (Spec.STATE_COMPILE.equals (key)) return Spec.STATE_COMPILE;
      if ("OUTER".equals (key)) return Spec.STATE_INTERPRET;
      if ("DEFINITION".equals (key)) return Spec.STATE_COMPILE;
      return null;
   } // end of canonicalStateMode()

   /**
    * Tells whether the given parser mode needs a delimiter argument.
    * @param mode parser mode
    * @return true when the mode requires one extra argument
    */
   static boolean parseModeNeedsArgument (String mode) {
      return Spec.PARSE_UNTIL.equals (mode) ||
         Spec.PARSE_DEFINITION.equals (mode);
   } // end of parseModeNeedsArgument()

   /**
    * Validates parser-word metadata combinations.
    * @param word word being defined
    * @param parseMode parser mode
    * @param parseString parser delimiter
    * @param defineMode defining mode
    * @param span source span for diagnostics
    */
   static void validateParserMetadata (String word, String parseMode,
      String parseString, String defineMode, String controlMode,
      String stateMode,
      SourceSpan span) {
      if (Spec.PARSE_UNTIL.equals (parseMode) &&
          ((parseString == null) | (parseString.length() == 0)))
         throw new RuntimeException ("Missing parser delimiter for " + word +
            " in " + sourceLocationText (span));
      if (Spec.PARSE_DEFINITION.equals (parseMode) &&
          ((parseString == null) | (parseString.length() == 0)))
         throw new RuntimeException ("Missing definition terminator for " +
            word + " in " + sourceLocationText (span));
      if (Spec.DEFINE_COLON.equals (defineMode) &&
          !Spec.PARSE_DEFINITION.equals (parseMode))
         throw new RuntimeException ("DEFINE COLON requires PARSE " +
            "DEFINITION for " + word + " in " + sourceLocationText (span));
      if ((Spec.DEFINE_CONSTANT.equals (defineMode) |
           Spec.DEFINE_VARIABLE.equals (defineMode)) &&
          !Spec.PARSE_WORD.equals (parseMode))
         throw new RuntimeException ("DEFINE " + defineMode + " requires " +
            "PARSE WORD for " + word + " in " + sourceLocationText (span));
      if (Spec.PARSE_DEFINITION.equals (parseMode) &&
          !Spec.DEFINE_COLON.equals (defineMode))
         throw new RuntimeException ("PARSE DEFINITION currently requires " +
            "DEFINE COLON for " + word + " in " + sourceLocationText (span));
      if (((controlMode != null) && (controlMode.length() > 0)) &&
          (((defineMode != null) && (defineMode.length() > 0)) ||
           ((parseMode != null) && (parseMode.length() > 0))))
         throw new RuntimeException ("CONTROL currently cannot be combined " +
            "with PARSE or DEFINE for " + word + " in " +
            sourceLocationText (span));
      if (((stateMode != null) && (stateMode.length() > 0)) &&
          Spec.DEFINE_COLON.equals (defineMode))
         throw new RuntimeException ("STATE is not supported for " + word +
            " together with DEFINE COLON in " + sourceLocationText (span));
   } // end of validateParserMetadata()

   /**
    * Writes parser metadata of one specification in a stable textual form.
    * @param writer destination
    * @param spec specification to render
    * @throws IOException when the destination write fails
    */
   static void appendSpecMetadata (BufferedWriter writer, Spec spec)
      throws IOException {
      if (spec == null) return;
      if ((spec.parseMode != null) && (spec.parseMode.length() > 0)) {
         writer.write (" PARSE ");
         writer.write (spec.parseMode);
         if (parseModeNeedsArgument (spec.parseMode)) {
            writer.write (" ");
            writer.write (TextScanner.quotedText (spec.parseString));
         }
      }
      if ((spec.defineMode != null) && (spec.defineMode.length() > 0)) {
         writer.write (" DEFINE ");
         writer.write (spec.defineMode);
      }
      if ((spec.controlMode != null) && (spec.controlMode.length() > 0)) {
         writer.write (" CONTROL ");
         writer.write (spec.controlMode);
      }
      if ((spec.stateMode != null) && (spec.stateMode.length() > 0)) {
         writer.write (" STATE ");
         writer.write (spec.stateMode);
      }
   } // end of appendSpecMetadata()

   /**
    * Formats a word name for saving or display in spec files.
    * @param word original word name
    * @return word text or quoted word text when required
    */
   static String formatWordForFile (String word) {
      if (needsQuotedAtom (word)) return TextScanner.quotedText (word);
      return word;
   } // end of formatWordForFile()

   /**
    * Tells whether one atom needs quoting in spec/type files.
    * @param text atom text
    * @return true if quoting is needed
    */
   static boolean needsQuotedAtom (String text) {
      if (text == null) return true;
      if (text.length() == 0) return true;
      for (int i = 0; i < text.length(); i++) {
         char current = text.charAt (i);
         if (Character.isWhitespace (current)) return true;
         if ((current == '(') | (current == '"') | (current == '#'))
            return true;
      }
      return false;
   } // end of needsQuotedAtom()

   /**
    * Converts set to string.
    */
   public String toString() {
      StringBuffer result = new StringBuffer ("");
      String nl = System.getProperty ("line.separator");
      Iterator<String> literalKinds = sortedLiteralKinds().iterator();
      while (literalKinds.hasNext()) {
         String kind = (String)literalKinds.next();
         Spec spec = getLiteral (kind);
         result.append (nl + "LITERAL " + formatWordForFile (kind));
         result.append ("\t" + spec.toString());
      }
      Iterator<String> words = sortedWords().iterator();
      while (words.hasNext()) {
         String word = (String)words.next();
         Spec spec = (Spec)get (word);
         result.append (nl + formatWordForFile (word));
         if ((spec.parseMode != null) && (spec.parseMode.length() > 0)) {
            result.append (" PARSE " + spec.parseMode);
            if (parseModeNeedsArgument (spec.parseMode))
               result.append (" " + TextScanner.quotedText (spec.parseString));
         }
         if ((spec.defineMode != null) && (spec.defineMode.length() > 0))
            result.append (" DEFINE " + spec.defineMode);
         if ((spec.controlMode != null) && (spec.controlMode.length() > 0))
            result.append (" CONTROL " + spec.controlMode);
         if ((spec.stateMode != null) && (spec.stateMode.length() > 0))
            result.append (" STATE " + spec.stateMode);
         result.append ("\t" + spec.toString());
      }
      result.append (nl);
      return result.toString();
   } // end of toString()

   /**
    * Returns word names in stable sorted order.
    * @return sorted set of keys
    */
   TreeSet<String> sortedWords() {
      return new TreeSet<String> (keySet());
   } // end of sortedWords()

   /**
    * Returns literal kind names in stable sorted order.
    * @return sorted set of literal keys
    */
   TreeSet<String> sortedLiteralKinds() {
      return new TreeSet<String> (literalSpecs.keySet());
   } // end of sortedLiteralKinds()

   /**
    * Canonicalizes a Forth word name for case-insensitive storage.
    * @param word original word name
    * @return trimmed uppercase word name
    */
   static String canonicalWord (String word) {
      if (word == null) return null;
      return word.trim().toUpperCase (Locale.ROOT);
   } // end of canonicalWord()
	
} // end of SpecSet

// end of file
