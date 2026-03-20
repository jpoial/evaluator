
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
    static final String DOUBLE_LITERAL_KIND = "DOUBLE";

   Hashtable<String, Spec> literalSpecs;
   LinkedList<ControlStructure> controlStructures;

   SpecSet() {
      super();
      literalSpecs = new Hashtable<String, Spec>();
      controlStructures = new LinkedList<ControlStructure>();
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
    * Adds one declarative control structure.
    * @param structure structure to register
    */
   void addControlStructure (ControlStructure structure) {
      if (structure == null)
         throw new RuntimeException ("Control structure must not be null.");
      controlStructures.add (structure);
   } // end of addControlStructure()

   /**
    * Returns the structures that start with the given control role.
    * @param role opener role
    * @return matching structures
    */
   LinkedList<ControlStructure> structuresForOpenRole (String role) {
      LinkedList<ControlStructure> result =
         new LinkedList<ControlStructure>();
      String key = canonicalControlMode (role);
      Iterator<ControlStructure> it = controlStructures.iterator();
      while (it.hasNext()) {
         ControlStructure structure = (ControlStructure)it.next();
         if ((structure != null) && key.equals (structure.openRole))
            result.add (structure);
      }
      return result;
   } // end of structuresForOpenRole()

   /**
    * Tells whether the role opens one or more declared structures.
    * @param role control role
    * @return true when the role is an opener
    */
   boolean hasOpenControlRole (String role) {
      return structuresForOpenRole (role).size() > 0;
   } // end of hasOpenControlRole()

   /**
    * Loads more specifications from a file into this set.
    * @param fileName local file name
    * @param ts type system used for validation
    */
   void load (String fileName, TypeSystem ts) {
      try {
         TextScanner scanner = TextScanner.fromFile (fileName);
         LinkedList<SourceWord> pendingLine = null;
         while (true) {
            LinkedList<SourceWord> line = pendingLine;
            if (line == null) line = scanner.nextLineAtoms();
            pendingLine = null;
            if (line == null) break;
            if (line.size() == 0) continue;
            pendingLine = loadTopLevelLine (scanner, line, ts);
         }
         installBuiltinControlStructures();
         validateControlStructures();
         validateDeclaredControlWords();
      } catch (IOException e) {
         throw new RuntimeException ("Unable to read specification set from "
            + fileName, e);
      }
   } // end of load()

   /**
    * Parses one top-level specification line and any nested structure block.
    * @param scanner source scanner for multiline structures
    * @param line current logical line atoms
    * @param ts type system used for validation
    * @return one buffered next top-level line or null
    */
   LinkedList<SourceWord> loadTopLevelLine (TextScanner scanner,
      LinkedList<SourceWord> line, TypeSystem ts) {
      if (line == null || line.size() == 0) return null;
      SourceWord word = (SourceWord)line.removeFirst();
      if (word == null || word.text.length() == 0)
         throw new RuntimeException ("Missing word name in " +
            (word == null ? scanner.positionSpan().startText() :
            word.span.startText()));
      String wordKey = canonicalWord (word.text);
      if ("STRUCTURE".equals (wordKey)) {
         addControlStructure (parseControlStructure (scanner, word, line));
         return null;
      }
      if (isTopLevelSyntaxDirective (word)) {
         StructureBlock block = parseAnonymousControlStructure (scanner, word,
            line);
         addControlStructure (block.structure);
         return block.pendingLine;
      }
      if (!word.quoted && "LITERAL".equals (wordKey)) {
         parseLiteralLine (word, line, ts);
         return null;
      }
      parseWordSpecLine (word, line, ts);
      return null;
   } // end of loadTopLevelLine()

   /**
    * Parses one literal specification from a top-level line.
    * @param head LITERAL token
    * @param line remaining line atoms
    * @param ts type system used for validation
    */
   void parseLiteralLine (SourceWord head, LinkedList<SourceWord> line,
      TypeSystem ts) {
      if (line.size() == 0)
         throw new RuntimeException ("Missing literal kind after " +
            "LITERAL in " + head.span.startText());
      SourceWord kind = (SourceWord)line.removeFirst();
      if (kind.text.length() == 0)
         throw new RuntimeException ("Missing literal kind after " +
            "LITERAL in " + kind.span.startText());
      if (containsLiteral (kind.text))
         throw new RuntimeException ("Duplicate literal " +
            "specification for " + kind.text + " in " +
            kind.span.startText());
      LineSpecBody body = consumeLineSpecBody (line, kind.span,
         "Malformed literal specification in ");
      putLiteral (kind.text, parseSpec (body.text.trim(), ts, body.span));
   } // end of parseLiteralLine()

   /**
    * Parses one ordinary word specification from a top-level line.
    * @param word word token
    * @param line remaining line atoms
    * @param ts type system used for validation
    */
   void parseWordSpecLine (SourceWord word, LinkedList<SourceWord> line,
      TypeSystem ts) {
      if (containsKey (word.text))
         throw new RuntimeException ("Duplicate specification for " +
            word.text + " in " + word.span.startText());
      String parseMode = Spec.PARSE_NONE;
      String parseString = "";
      String defineMode = Spec.DEFINE_NONE;
      String controlMode = Spec.CONTROL_NONE;
      String stateMode = Spec.STATE_ANY;
      boolean immediate = false;
      boolean immediateSeen = false;
      while (line.size() > 0) {
         SourceWord option = (SourceWord)line.removeFirst();
         if (isUnquotedToken (option, "(")) {
            line.addFirst (option);
            break;
         }
         String optionKey = canonicalWord (option.text);
         if ("PARSE".equals (optionKey)) {
            if (parseMode.length() > 0)
               throw new RuntimeException ("Duplicate PARSE clause in " +
                  option.span.startText());
            SourceWord modeToken = expectLineToken (line,
               "Missing parser mode after PARSE in " +
               option.span.startText());
            parseMode = canonicalParseMode (modeToken.text);
            if (parseMode == null)
               throw new RuntimeException ("Unknown parser mode " +
                  modeToken.text + " in " + modeToken.span.startText());
            if (parseModeNeedsArgument (parseMode)) {
               SourceWord delimiter = expectLineToken (line,
                  "Missing parser delimiter after PARSE " + modeToken.text +
                  " in " + modeToken.span.startText());
               parseString = resolveParseString (delimiter, ts);
            }
            continue;
         }
         if ("DEFINE".equals (optionKey)) {
            if (defineMode.length() > 0)
               throw new RuntimeException ("Duplicate DEFINE clause in " +
                  option.span.startText());
            SourceWord modeToken = expectLineToken (line,
               "Missing defining mode after DEFINE in " +
               option.span.startText());
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
            SourceWord modeToken = expectLineToken (line,
               "Missing control mode after CONTROL in " +
               option.span.startText());
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
            SourceWord modeToken = expectLineToken (line,
               "Missing state mode after " + option.text + " in " +
               option.span.startText());
            stateMode = canonicalStateMode (modeToken.text);
            if (stateMode == null)
               throw new RuntimeException ("Unknown state mode " +
                  modeToken.text + " in " + modeToken.span.startText());
            continue;
         }
         if ("IMMEDIATE".equals (optionKey)) {
            if (immediateSeen)
               throw new RuntimeException ("Duplicate IMMEDIATE clause " +
                  "in " + option.span.startText());
            immediate = true;
            immediateSeen = true;
            continue;
         }
         if ("SCAN".equals (optionKey)) {
            if (parseMode.length() > 0)
               throw new RuntimeException ("Duplicate scanner clause in " +
                  option.span.startText());
            SourceWord delimiter = expectLineToken (line,
               "Missing scanner delimiter after SCAN in " +
               option.span.startText());
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
      LineSpecBody body = consumeLineSpecBody (line, word.span,
         "Malformed specification in ");
      if (!immediateSeen &&
          impliedImmediate (parseMode, defineMode, controlMode))
         immediate = true;
      validateParserMetadata (word.text, parseMode, parseString,
         defineMode, controlMode, immediate, stateMode, body.span);
      put (word.text, parseSpec (body.text.trim(), ts, body.span)
         .withParseMode (parseMode)
         .withParseString (parseString)
         .withDefineMode (defineMode)
         .withControlMode (controlMode)
         .withImmediate (immediate)
         .withStateMode (stateMode));
   } // end of parseWordSpecLine()

   /**
    * Returns one required next token from a parsed top-level line.
    * @param line remaining line atoms
    * @param message diagnostic text when missing
    * @return next token
    */
   static SourceWord expectLineToken (LinkedList<SourceWord> line,
      String message) {
      if (line.size() == 0) throw new RuntimeException (message);
      return (SourceWord)line.removeFirst();
   } // end of expectLineToken()

   /**
    * Reads the stack-effect body delimited by unquoted '(' and ')' tokens.
    * @param line remaining line atoms
    * @param span fallback span for diagnostics
    * @param messagePrefix diagnostic prefix
    * @return parsed body text with source span
    */
   static LineSpecBody consumeLineSpecBody (LinkedList<SourceWord> line,
      SourceSpan span, String messagePrefix) {
      if (line.size() == 0 || !isUnquotedToken ((SourceWord)line.getFirst(), "("))
         throw new RuntimeException (messagePrefix + span.startText());
      SourceWord open = (SourceWord)line.removeFirst();
      LinkedList<SourceWord> bodyTokens = new LinkedList<SourceWord>();
      SourceWord close = null;
      while (line.size() > 0) {
         SourceWord current = (SourceWord)line.removeFirst();
         if (isUnquotedToken (current, ")")) {
            close = current;
            break;
         }
         bodyTokens.add (current);
      }
      if (close == null)
         throw new RuntimeException (messagePrefix + open.span.startText());
      if (line.size() > 0)
         throw new RuntimeException ("Unexpected trailing text in " +
            ((SourceWord)line.getFirst()).span.startText());
      LineSpecBody result = new LineSpecBody();
      result.text = joinAtomTexts (bodyTokens).trim();
      if (bodyTokens.size() > 0)
         result.span = SourceSpan.covering (
            ((SourceWord)bodyTokens.getFirst()).span,
            ((SourceWord)bodyTokens.getLast()).span);
      else
         result.span = SourceSpan.covering (open.span, close.span);
      return result;
   } // end of consumeLineSpecBody()

   /**
    * Tells whether a line token is one exact unquoted delimiter.
    * @param token token to test
    * @param text required text
    * @return true for matching unquoted tokens
    */
   static boolean isUnquotedToken (SourceWord token, String text) {
      if (token == null || token.quoted) return false;
      return text.equals (token.text);
   } // end of isUnquotedToken()

   /**
    * Tells whether one top-level token starts the wrapper-free SYNTAX block.
    * @param token top-level token
    * @return true for SYNTAX:
    */
   static boolean isTopLevelSyntaxDirective (SourceWord token) {
      if (token == null || token.quoted) return false;
      if (token.text == null || !token.text.endsWith (":")) return false;
      return "SYNTAX".equals (canonicalDirectiveKey (token.text));
   } // end of isTopLevelSyntaxDirective()

   /**
    * One parsed stack-effect body from a top-level line.
    */
   static class LineSpecBody {
      String text = "";
      SourceSpan span;
   } // end of LineSpecBody

   /**
    * Saves this set to a text file in a stable order.
    * @param fileName local file name
    */
   void save (String fileName) {
      BufferedWriter writer = null;
      String nl = System.getProperty ("line.separator");
      try {
         writer = new BufferedWriter (new FileWriter (fileName));
         boolean wroteStructure = false;
         Iterator<ControlStructure> structures = controlStructures.iterator();
         while (structures.hasNext()) {
            ControlStructure structure = (ControlStructure)structures.next();
            if ((structure == null) || structure.builtin) continue;
            appendControlStructure (writer, structure);
            writer.write (nl);
            wroteStructure = true;
         }
         if (wroteStructure) writer.write (nl);
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
    * Tells whether the token is a decimal double-integer literal.
    * The current evaluator follows the standard trailing-dot form, but only
    * for decimal integer tokens such as 1234. or -7.
    * @param text source token
    * @return true for decimal doubles with a trailing '.'
    */
   static boolean isDecimalDoubleLiteral (String text) {
      if ((text == null) || (text.length() < 2)) return false;
      if (text.charAt (text.length() - 1) != '.') return false;
      return isDecimalIntegerLiteral (text.substring (0, text.length() - 1));
   } // end of isDecimalDoubleLiteral()

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
    * Parses one control-structure declaration block.
    * @param scanner spec-file scanner positioned after STRUCTURE
    * @param head STRUCTURE token for diagnostics
    * @return parsed structure declaration
    */
   ControlStructure parseControlStructure (TextScanner scanner,
      SourceWord head) {
      return parseControlStructure (scanner, head, null);
   } // end of parseControlStructure()

   /**
    * Parses one wrapped control-structure declaration block.
    * @param scanner spec-file scanner positioned after the head line
    * @param head STRUCTURE token for diagnostics
    * @param headRemainder remaining tokens from the STRUCTURE line
    * @return parsed structure declaration
    */
   ControlStructure parseControlStructure (TextScanner scanner,
      SourceWord head, LinkedList<SourceWord> headRemainder) {
      SourceWord nameToken = null;
      if (headRemainder == null) {
         nameToken = scanner.nextAtom();
         if (nameToken == null)
            throw new RuntimeException ("Missing structure name after " +
               head.text + " in " + head.span.startText());
         LinkedList<SourceWord> trailing = scanner.nextLineAtoms();
         if ((trailing != null) && (trailing.size() > 0))
            throw new RuntimeException ("Unexpected trailing text in " +
               ((SourceWord)trailing.getFirst()).span.startText());
      } else {
         if (headRemainder.size() == 0)
            throw new RuntimeException ("Missing structure name after " +
               head.text + " in " + head.span.startText());
         nameToken = (SourceWord)headRemainder.removeFirst();
         if (headRemainder.size() > 0)
            throw new RuntimeException ("Unexpected trailing text in " +
               ((SourceWord)headRemainder.getFirst()).span.startText());
      }
      ControlStructure structure = new ControlStructure (nameToken.text)
         .withSourceSpan (SourceSpan.covering (head.span, nameToken.span));
      LinkedList<SourceWord> pendingLine = null;
      while (true) {
         LinkedList<SourceWord> line = pendingLine;
         if (line == null) line = scanner.nextLineAtoms();
         pendingLine = null;
         if (line == null)
            throw new RuntimeException ("Missing ENDSTRUCTURE for " +
               nameToken.text + " in " + nameToken.span.startText());
         if (line.size() == 0) continue;
         SourceWord directive = (SourceWord)line.removeFirst();
         String directiveKey = canonicalDirectiveKey (directive.text);
         if ("ENDSTRUCTURE".equals (directiveKey)) {
            if (line.size() > 0)
               throw new RuntimeException ("Unexpected trailing text in " +
                  ((SourceWord)line.getFirst()).span.startText());
            validateControlStructure (structure, nameToken.span);
            return structure;
         }
         if ("SYNTAX".equals (directiveKey)) {
            if ((structure.syntaxText != null) &&
                (structure.syntaxText.length() > 0))
               throw new RuntimeException ("Duplicate SYNTAX clause in " +
                  directive.span.startText());
            if (((structure.openRole != null) &&
                 (structure.openRole.length() > 0)) ||
                ((structure.closeRole != null) &&
                 (structure.closeRole.length() > 0)) ||
                structure.hasMidRole())
               throw new RuntimeException ("SYNTAX cannot be combined with " +
                  "OPEN, MID, or CLOSE in " + directive.span.startText());
            DirectiveBody body = collectStructureDirectiveBody (scanner, line);
            pendingLine = body.pendingLine;
            parseControlSyntax (structure, body.text, directive.span);
            continue;
         }
         if ("OPEN".equals (directiveKey)) {
            if ((structure.syntaxText != null) &&
                (structure.syntaxText.length() > 0))
               throw new RuntimeException ("OPEN cannot be combined with " +
                  "SYNTAX in " + directive.span.startText());
            if (structure.openRole != null && structure.openRole.length() > 0)
               throw new RuntimeException ("Duplicate OPEN clause in " +
                  directive.span.startText());
            structure.withOpenRole (expectSingleRole (line, "OPEN",
               directive.span));
            continue;
         }
         if ("MID".equals (directiveKey) || "MIDDLE".equals (directiveKey)) {
            if ((structure.syntaxText != null) &&
                (structure.syntaxText.length() > 0))
               throw new RuntimeException ("MID cannot be combined with " +
                  "SYNTAX in " + directive.span.startText());
            if (structure.hasMidRole())
               throw new RuntimeException ("Duplicate MID clause in " +
                  directive.span.startText());
            if (line.size() == 0)
               throw new RuntimeException ("Missing control role after " +
                  directive.text + " in " + directive.span.startText());
            SourceWord role = (SourceWord)line.removeFirst();
            boolean optional = false;
            if (line.size() > 0) {
               SourceWord flag = (SourceWord)line.removeFirst();
               if (!"OPTIONAL".equals (canonicalWord (flag.text)))
                  throw new RuntimeException ("Unexpected text " + flag.text +
                     " in " + flag.span.startText());
               optional = true;
            }
            if (line.size() > 0)
               throw new RuntimeException ("Unexpected trailing text in " +
                  ((SourceWord)line.getFirst()).span.startText());
            structure.withMidRole (canonicalControlMode (role.text), optional);
            continue;
         }
         if ("CLOSE".equals (directiveKey)) {
            if ((structure.syntaxText != null) &&
                (structure.syntaxText.length() > 0))
               throw new RuntimeException ("CLOSE cannot be combined with " +
                  "SYNTAX in " + directive.span.startText());
            if (structure.closeRole != null &&
                structure.closeRole.length() > 0)
               throw new RuntimeException ("Duplicate CLOSE clause in " +
                  directive.span.startText());
            structure.withCloseRole (expectSingleRole (line, "CLOSE",
               directive.span));
            continue;
         }
         if ("COMPILATION".equals (directiveKey)) {
            if ((structure.compilationText != null) &&
                (structure.compilationText.length() > 0))
               throw new RuntimeException (
                  "Duplicate COMPILATION clause in " +
                  directive.span.startText());
            DirectiveBody body = collectStructureDirectiveBody (scanner, line);
            pendingLine = body.pendingLine;
            structure.withCompilationText (body.text);
            continue;
         }
         if ("RUN-TIME".equals (directiveKey) ||
             "RUNTIME".equals (directiveKey)) {
            if ((structure.runtimeText != null) &&
                (structure.runtimeText.length() > 0))
               throw new RuntimeException ("Duplicate RUN-TIME clause in " +
                  directive.span.startText());
            DirectiveBody body = collectStructureDirectiveBody (scanner, line);
            pendingLine = body.pendingLine;
            structure.withRuntimeText (body.text);
            continue;
         }
         if ("MEANING".equals (directiveKey) ||
             "EFFECT".equals (directiveKey) ||
             "SEMANTICS".equals (directiveKey)) {
            if ((structure.meaningText != null) &&
                (structure.meaningText.length() > 0))
               throw new RuntimeException ("Duplicate EFFECT clause in " +
                  directive.span.startText());
            DirectiveBody body = collectStructureDirectiveBody (scanner, line);
            pendingLine = body.pendingLine;
            String meaningText = body.text;
            structure.withMeaning (parseControlMeaning (meaningText,
               directive.span), meaningText);
            continue;
         }
         throw new RuntimeException ("Unknown structure directive " +
            directive.text + " in " + directive.span.startText());
      }
   } // end of parseControlStructure()

   /**
    * Parses one wrapper-free top-level SYNTAX block.
    * @param scanner spec-file scanner positioned after the SYNTAX line
    * @param head SYNTAX: token for diagnostics
    * @param headRemainder inline syntax text from the head line
    * @return parsed structure and one buffered next top-level line
    */
   StructureBlock parseAnonymousControlStructure (TextScanner scanner,
      SourceWord head, LinkedList<SourceWord> headRemainder) {
      StructureBlock result = new StructureBlock();
      ControlStructure structure = new ControlStructure ("")
         .withSourceSpan (head.span);
      StringBuffer syntax = new StringBuffer ("");
      boolean sawNestedDirective = false;
      appendJoinedLine (syntax, headRemainder);
      LinkedList<SourceWord> pendingLine = null;
      while (true) {
         LinkedList<SourceWord> line = pendingLine;
         if (line == null) line = scanner.nextLineAtoms();
         pendingLine = null;
         if (line == null) break;
         if (line.size() == 0) {
            if (!sawNestedDirective && (syntax.length() > 0))
               syntax.append (System.getProperty ("line.separator"));
            continue;
         }
         SourceWord first = (SourceWord)line.getFirst();
         if ((first.span != null) &&
             (first.span.startColumn <= head.span.startColumn)) {
            result.pendingLine = line;
            break;
         }
         if (isNestedStructureDirective (first)) {
            sawNestedDirective = true;
            SourceWord directive = (SourceWord)line.removeFirst();
            String directiveKey = canonicalDirectiveKey (directive.text);
            DirectiveBody body = collectIndentedDirectiveBody (scanner, line,
               directive.span.startColumn);
            pendingLine = body.pendingLine;
            if ("COMPILATION".equals (directiveKey)) {
               if ((structure.compilationText != null) &&
                   (structure.compilationText.length() > 0))
                  throw new RuntimeException ("Duplicate COMPILATION clause in "
                     + directive.span.startText());
               structure.withCompilationText (body.text);
               continue;
            }
            if ("RUN-TIME".equals (directiveKey) ||
                "RUNTIME".equals (directiveKey)) {
               if ((structure.runtimeText != null) &&
                   (structure.runtimeText.length() > 0))
                  throw new RuntimeException ("Duplicate RUN-TIME clause in " +
                     directive.span.startText());
               structure.withRuntimeText (body.text);
               continue;
            }
            if ("MEANING".equals (directiveKey) ||
                "EFFECT".equals (directiveKey) ||
                "SEMANTICS".equals (directiveKey)) {
               if ((structure.meaningText != null) &&
                   (structure.meaningText.length() > 0))
                  throw new RuntimeException ("Duplicate EFFECT clause in " +
                     directive.span.startText());
               structure.withMeaning (parseControlMeaning (body.text,
                  directive.span), body.text);
               continue;
            }
            throw new RuntimeException ("Unknown nested structure directive " +
               directive.text + " in " + directive.span.startText());
         }
         if (sawNestedDirective)
            throw new RuntimeException ("Unexpected syntax text after nested " +
               "directive in " + first.span.startText());
         appendJoinedLine (syntax, line);
      }
      parseControlSyntax (structure, syntax.toString().trim(), head.span);
      structure.name = generatedStructureName (structure,
         controlStructures.size() + 1);
      validateControlStructure (structure, head.span);
      result.structure = structure;
      return result;
   } // end of parseAnonymousControlStructure()

   /**
    * Tells whether one nested token starts a lower-level structure directive.
    * Wrapper-free structure blocks use the colon to keep ordinary Forth words
    * like EFFECT or SYNTAX usable elsewhere.
    * @param token candidate nested token
    * @return true for recognized nested directives with trailing ':'
    */
   static boolean isNestedStructureDirective (SourceWord token) {
      if (token == null || token.quoted || token.text == null) return false;
      if (!token.text.endsWith (":")) return false;
      String key = canonicalDirectiveKey (token.text);
      return "COMPILATION".equals (key) || "RUN-TIME".equals (key) ||
         "RUNTIME".equals (key) || "MEANING".equals (key) ||
         "EFFECT".equals (key) || "SEMANTICS".equals (key);
   } // end of isNestedStructureDirective()

   /**
    * Appends one already tokenized source line back into user-facing text.
    * @param buffer destination text
    * @param line line atoms to append
    */
   static void appendJoinedLine (StringBuffer buffer,
      LinkedList<SourceWord> line) {
      if (buffer == null || line == null || line.size() == 0) return;
      if (buffer.length() > 0)
         buffer.append (System.getProperty ("line.separator"));
      buffer.append (joinAtomTexts (line).trim());
   } // end of appendJoinedLine()

   /**
    * Generates one internal structure name for wrapper-free syntax blocks.
    * @param structure parsed structure
    * @param ordinal fallback ordinal
    * @return internal diagnostic name
    */
   String generatedStructureName (ControlStructure structure, int ordinal) {
      StringBuffer base = new StringBuffer ("");
      if ((structure != null) && (structure.openRole != null) &&
          (structure.openRole.length() > 0))
         base.append (structure.openRole);
      if (structure != null) {
         for (int i = 0; i < structure.boundaryCount(); i++) {
            if (base.length() > 0) base.append ("_");
            base.append (structure.boundaryRoleAt (i));
         }
         if ((structure.closeRole != null) && (structure.closeRole.length() > 0)) {
            if (base.length() > 0) base.append ("_");
            base.append (structure.closeRole);
         }
      }
      String name = base.length() > 0 ? base.toString() :
         "SYNTAX_BLOCK_" + ordinal;
      if (!hasGeneratedStructureName (name)) return name;
      return name + "_" + ordinal;
   } // end of generatedStructureName()

   /**
    * Tells whether an internal structure name is already in use.
    * @param name candidate name
    * @return true when the name is already present
    */
   boolean hasGeneratedStructureName (String name) {
      if (name == null) return false;
      Iterator<ControlStructure> it = controlStructures.iterator();
      while (it.hasNext()) {
         ControlStructure structure = (ControlStructure)it.next();
         if ((structure != null) && name.equals (structure.name)) return true;
      }
      return false;
   } // end of hasGeneratedStructureName()

   /**
    * One parsed wrapper-free structure block.
    */
   static class StructureBlock {
      ControlStructure structure;
      LinkedList<SourceWord> pendingLine = null;
   } // end of StructureBlock

   /**
    * Canonicalizes one structure-directive keyword, accepting a trailing ':'.
    * @param text directive text
    * @return canonical uppercase keyword without trailing ':'
    */
   static String canonicalDirectiveKey (String text) {
      if (text == null) return null;
      String key = canonicalWord (text);
      if ((key != null) && key.endsWith (":"))
         key = key.substring (0, key.length() - 1);
      return key;
   } // end of canonicalDirectiveKey()

   /**
    * Reads one required role operand from a structure directive line.
    * @param line remaining line atoms
    * @param directive directive name
    * @param span directive span
    * @return canonical role name
    */
   static String expectSingleRole (LinkedList<SourceWord> line,
      String directive, SourceSpan span) {
      if (line.size() == 0)
         throw new RuntimeException ("Missing control role after " +
            directive + " in " + span.startText());
      SourceWord role = (SourceWord)line.removeFirst();
      if (line.size() > 0)
         throw new RuntimeException ("Unexpected trailing text in " +
            ((SourceWord)line.getFirst()).span.startText());
      return canonicalControlMode (role.text);
   } // end of expectSingleRole()

   /**
    * Joins one directive line back into user-facing text.
    * @param line line atoms
    * @return joined text
    */
   static String joinAtomTexts (LinkedList<SourceWord> line) {
      StringBuffer text = new StringBuffer ("");
      if (line == null) return "";
      Iterator<SourceWord> it = line.iterator();
      while (it.hasNext()) {
         if (text.length() > 0) text.append (" ");
         text.append (((SourceWord)it.next()).text);
      }
      return text.toString();
   } // end of joinAtomTexts()

   /**
    * Collects one possibly-indented directive body inside STRUCTURE.
    * Continuation lines must be indented so the next flush-left directive
    * remains unambiguous.
    * @param scanner structure scanner
    * @param firstLine atoms remaining on the directive line
    * @return collected body text and one buffered next directive line
    */
   static DirectiveBody collectStructureDirectiveBody (TextScanner scanner,
      LinkedList<SourceWord> firstLine) {
      DirectiveBody result = new DirectiveBody();
      String firstText = joinAtomTexts (firstLine).trim();
      if (firstText.length() > 0)
         result.buffer.append (firstText);
      while (true) {
         LinkedList<SourceWord> nextLine = scanner.nextLineAtoms();
         if (nextLine == null) break;
         if (nextLine.size() == 0) {
            if (result.buffer.length() > 0)
               result.buffer.append (System.getProperty ("line.separator"));
            continue;
         }
         SourceWord first = (SourceWord)nextLine.getFirst();
         if ((first != null) && (first.span != null) &&
             (first.span.startColumn > 1)) {
            if (result.buffer.length() > 0)
               result.buffer.append (System.getProperty ("line.separator"));
            result.buffer.append (joinAtomTexts (nextLine).trim());
            continue;
         }
         result.pendingLine = nextLine;
         break;
      }
      result.text = result.buffer.toString().trim();
      return result;
   } // end of collectStructureDirectiveBody()

   /**
    * Collects one directive body whose continuation lines must stay more
    * indented than the directive itself.
    * @param scanner structure scanner
    * @param firstLine atoms remaining on the directive line
    * @param parentColumn starting column of the directive head
    * @return collected body text and one buffered next line
    */
   static DirectiveBody collectIndentedDirectiveBody (TextScanner scanner,
      LinkedList<SourceWord> firstLine, int parentColumn) {
      DirectiveBody result = new DirectiveBody();
      String firstText = joinAtomTexts (firstLine).trim();
      if (firstText.length() > 0)
         result.buffer.append (firstText);
      while (true) {
         LinkedList<SourceWord> nextLine = scanner.nextLineAtoms();
         if (nextLine == null) break;
         if (nextLine.size() == 0) {
            if (result.buffer.length() > 0)
               result.buffer.append (System.getProperty ("line.separator"));
            continue;
         }
         SourceWord first = (SourceWord)nextLine.getFirst();
         if ((first != null) && (first.span != null) &&
             (first.span.startColumn > parentColumn)) {
            if (result.buffer.length() > 0)
               result.buffer.append (System.getProperty ("line.separator"));
            result.buffer.append (joinAtomTexts (nextLine).trim());
            continue;
         }
         result.pendingLine = nextLine;
         break;
      }
      result.text = result.buffer.toString().trim();
      return result;
   } // end of collectIndentedDirectiveBody()

   /**
    * Small parse helper for one multiline directive body.
    */
   static class DirectiveBody {
      StringBuffer buffer = new StringBuffer ("");
      String text = "";
      LinkedList<SourceWord> pendingLine = null;
   } // end of DirectiveBody

   /**
    * Parses one structured SYNTAX line with metasymbol placeholders.
    * @param structure structure to populate
    * @param text syntax text
    * @param span source span for diagnostics
    */
   static void parseControlSyntax (ControlStructure structure, String text,
      SourceSpan span) {
      if (text == null || text.trim().length() == 0)
         throw new RuntimeException ("Missing structure syntax in " +
            sourceLocationText (span));
      LinkedList<String> tokens = tokenizeControlSyntax (text, span);
      if (tokens.size() < 3)
         throw new RuntimeException ("Too short SYNTAX clause in " +
            sourceLocationText (span));
      String openWord = requireSyntaxWord ((String)tokens.removeFirst(),
         "opening control word", span);
      LinkedList<String> segmentNames = new LinkedList<String>();
      segmentNames.add (ControlStructure.canonicalSegmentName (
         requireSyntaxSegment ((String)tokens.removeFirst(),
         "first segment", span), ControlStructure.SEGMENT_BODY));
      LinkedList<String> boundaryRoles = new LinkedList<String>();
      LinkedList<Boolean> boundaryOptional = new LinkedList<Boolean>();
      while (tokens.size() > 1) {
         boolean optional = false;
         if ("[".equals ((String)tokens.getFirst())) {
            if (tokens.size() < 5)
               throw new RuntimeException ("Malformed optional syntax group in "
                  + sourceLocationText (span));
            optional = true;
            tokens.removeFirst();
         }
         boundaryRoles.add (canonicalControlMode (requireSyntaxWord (
            (String)tokens.removeFirst(), "boundary control word", span)));
         segmentNames.add (ControlStructure.canonicalSegmentName (
            requireSyntaxSegment ((String)tokens.removeFirst(),
            "captured segment", span),
            ControlStructure.fallbackSegmentName (segmentNames.size())));
         boundaryOptional.add (Boolean.valueOf (optional));
         if (optional) {
            if (tokens.size() == 0 || !"]".equals ((String)tokens.removeFirst()))
               throw new RuntimeException ("Missing ] in " +
                  sourceLocationText (span));
            if (tokens.size() > 1)
               throw new RuntimeException ("Optional syntax group must be the "
                  + "last boundary before the closer in " +
                  sourceLocationText (span));
         }
      }
      String closeWord = requireSyntaxWord ((String)tokens.removeFirst(),
         "closing control word", span);
      structure.withSyntaxText (text.trim()).setPattern (
         canonicalControlMode (openWord), boundaryRoles, boundaryOptional,
         segmentNames, canonicalControlMode (closeWord));
   } // end of parseControlSyntax()

   /**
    * Tokenizes one structure SYNTAX line.
    * @param text syntax text
    * @param span source span for diagnostics
    * @return syntax tokens
    */
   static LinkedList<String> tokenizeControlSyntax (String text,
      SourceSpan span) {
      LinkedList<String> result = new LinkedList<String>();
      int i = 0;
      while (i < text.length()) {
         char current = text.charAt (i);
         if (Character.isWhitespace (current)) {
            i++;
            continue;
         }
         if ((current == '[') || (current == ']')) {
            result.add (String.valueOf (current));
            i++;
            continue;
         }
         if (current == '<') {
            int end = text.indexOf ('>', i + 1);
            if (end < 0)
               throw new RuntimeException ("Unclosed metasymbol in " +
                  sourceLocationText (span));
            result.add (text.substring (i, end + 1));
            i = end + 1;
            continue;
         }
         int start = i;
         while (i < text.length()) {
            current = text.charAt (i);
            if (Character.isWhitespace (current) || (current == '[') ||
                (current == ']') || (current == '<'))
               break;
            i++;
         }
         result.add (text.substring (start, i));
      }
      return result;
   } // end of tokenizeControlSyntax()

   /**
    * Reads one literal control-word token from SYNTAX.
    * @param token syntax token
    * @param label diagnostic label
    * @param span source span for diagnostics
    * @return word token text
    */
   static String requireSyntaxWord (String token, String label,
      SourceSpan span) {
      if (token == null || token.length() == 0 || "[".equals (token) ||
          "]".equals (token) ||
          ((token.startsWith ("<")) && (token.endsWith (">"))))
         throw new RuntimeException ("Missing " + label + " in " +
            sourceLocationText (span));
      return token;
   } // end of requireSyntaxWord()

   /**
    * Reads one metasymbol segment token from SYNTAX.
    * @param token syntax token
    * @param label diagnostic label
    * @param span source span for diagnostics
    * @return raw segment name
    */
   static String requireSyntaxSegment (String token, String label,
      SourceSpan span) {
      if (token == null || token.length() < 3 ||
          !token.startsWith ("<") || !token.endsWith (">"))
         throw new RuntimeException ("Missing " + label + " in " +
            sourceLocationText (span));
      String result = token.substring (1, token.length() - 1).trim();
      if (result.length() == 0)
         throw new RuntimeException ("Empty metasymbol in " +
            sourceLocationText (span));
      return result;
   } // end of requireSyntaxSegment()

   /**
    * Canonicalizes one metasymbol token such as &lt;branch&gt; to BRANCH.
    * @param token raw token text
    * @return canonical name without angle brackets, or null when not a metasymbol
    */
   static String canonicalMetasymbol (String token) {
      if (token == null) return null;
      String trimmed = token.trim();
      if (trimmed.length() < 3) return null;
      if (!trimmed.startsWith ("<") || !trimmed.endsWith (">")) return null;
      return ControlStructure.canonicalSegmentName (
         trimmed.substring (1, trimmed.length() - 1), "");
   } // end of canonicalMetasymbol()

   /**
    * Parses one control-structure effect expression.
    * @param text expression text
    * @param span source span for diagnostics
    * @return parsed effect expression
    */
   static ControlStructure.EffectExpr parseControlMeaning (String text,
      SourceSpan span) {
      if (text == null || text.trim().length() == 0)
         throw new RuntimeException ("Missing structure effect in " +
            sourceLocationText (span));
      if (!looksLikeAlgebraicControlEffect (text))
         return parseStructuredControlEffect (text, span);
      TextScanner scanner = new TextScanner (span.sourceName, text);
      ControlStructure.EffectExpr result = parseControlExpr (scanner, span);
      scanner.skipIgnorable();
      if (!scanner.atEnd()) {
         SourceWord extra = scanner.nextAtom();
         throw new RuntimeException ("Unexpected text " + extra.text + " in " +
            sourceLocationText (span));
      }
      return result;
   } // end of parseControlMeaning()

   /**
    * Tells whether the effect text uses the older algebraic notation.
    * @param text raw effect text
    * @return true for SEQUENCE(...), GLB(...), and similar forms
    */
   static boolean looksLikeAlgebraicControlEffect (String text) {
      if (text == null) return false;
      return (text.indexOf ('(') >= 0) || (text.indexOf (')') >= 0) ||
         (text.indexOf (',') >= 0);
   } // end of looksLikeAlgebraicControlEffect()

   /**
    * Parses the newer structured effect notation used in bundled specs.
    * @param text raw effect text
    * @param span source span for diagnostics
    * @return parsed effect tree
    */
   static ControlStructure.EffectExpr parseStructuredControlEffect (
      String text, SourceSpan span) {
      LinkedList<ControlStructure.EffectExpr> lines =
         new LinkedList<ControlStructure.EffectExpr>();
      String normalized = text == null ? "" : text.replace ("\r", "");
      String [] rawLines = normalized.split ("\n", -1);
      for (int i = 0; i < rawLines.length; i++) {
         String line = rawLines [i].trim();
         if (line.length() == 0) continue;
         lines.add (parseStructuredControlEffectLine (line, span));
      }
      if (lines.size() == 0)
         throw new RuntimeException ("Missing structure effect in " +
            sourceLocationText (span));
      return sequenceEffect (lines);
   } // end of parseStructuredControlEffect()

   /**
    * Parses one line of the structured effect notation.
    * @param line trimmed line text
    * @param span source span for diagnostics
    * @return parsed effect expression
    */
   static ControlStructure.EffectExpr parseStructuredControlEffectLine (
      String line, SourceSpan span) {
      LinkedList<SourceWord> tokens = tokenizeStructuredControlEffectLine (
         line, span);
      if (tokens.size() == 0)
         return new ControlStructure.EmptyExpr();
      String head = canonicalWord (((SourceWord)tokens.getFirst()).text);
      if ("EITHER".equals (head)) {
         tokens.removeFirst();
         if (tokens.size() < 2)
            throw new RuntimeException ("EITHER requires at least two " +
               "alternatives in " + sourceLocationText (span));
         ControlStructure.EffectExpr result = parseStructuredControlAtom (
            (SourceWord)tokens.removeFirst(), span);
         while (tokens.size() > 0) {
            result = new ControlStructure.GlbExpr (result,
               parseStructuredControlAtom (
                  (SourceWord)tokens.removeFirst(), span));
         }
         return result;
      }
      if ("REPEAT".equals (head)) {
         tokens.removeFirst();
         if (tokens.size() == 0)
            throw new RuntimeException ("REPEAT requires a repeated effect in "
               + sourceLocationText (span));
         LinkedList<ControlStructure.EffectExpr> repeated =
            new LinkedList<ControlStructure.EffectExpr>();
         while (tokens.size() > 0) {
            repeated.add (parseStructuredControlAtom (
               (SourceWord)tokens.removeFirst(), span));
         }
         return new ControlStructure.StarExpr (sequenceEffect (repeated));
      }
      LinkedList<ControlStructure.EffectExpr> parts =
         new LinkedList<ControlStructure.EffectExpr>();
      while (tokens.size() > 0) {
         parts.add (parseStructuredControlAtom (
            (SourceWord)tokens.removeFirst(), span));
      }
      return sequenceEffect (parts);
   } // end of parseStructuredControlEffectLine()

   /**
    * Tokenizes one structured-effect line, preserving &lt;metasymbol names&gt;.
    * @param line raw line text
    * @param span source span for diagnostics
    * @return line tokens
    */
   static LinkedList<SourceWord> tokenizeStructuredControlEffectLine (
      String line, SourceSpan span) {
      LinkedList<SourceWord> result = new LinkedList<SourceWord>();
      TextScanner scanner = new TextScanner (span.sourceName, line);
      SourceWord token = null;
      while ((token = nextControlMeaningAtom (scanner, "", span)) != null)
         result.add (token);
      return result;
   } // end of tokenizeStructuredControlEffectLine()

   /**
    * Parses one structured-effect atom.
    * @param token atom token
    * @param span source span for diagnostics
    * @return parsed atom expression
    */
   static ControlStructure.EffectExpr parseStructuredControlAtom (
      SourceWord token, SourceSpan span) {
      if (token == null)
         throw new RuntimeException ("Missing effect atom in " +
            sourceLocationText (span));
      String metasymbol = canonicalMetasymbol (token.text);
      if ((metasymbol != null) && (metasymbol.length() > 0)) {
         if ("OPEN".equals (metasymbol) || "MID".equals (metasymbol) ||
             "CLOSE".equals (metasymbol))
            return new ControlStructure.ControlExpr (metasymbol);
         return new ControlStructure.SegmentExpr (metasymbol);
      }
      String key = canonicalWord (token.text);
      if (ControlStructure.SEGMENT_EMPTY.equals (key) ||
          "EPSILON".equals (key))
         return new ControlStructure.EmptyExpr();
      return new ControlStructure.ControlExpr (canonicalControlMode (
         token.text));
   } // end of parseStructuredControlAtom()

   /**
    * Collapses a list of effect expressions into one sequence.
    * @param parts ordered effect parts
    * @return single expression
    */
   static ControlStructure.EffectExpr sequenceEffect (
      LinkedList<ControlStructure.EffectExpr> parts) {
      if ((parts == null) || (parts.size() == 0))
         return new ControlStructure.EmptyExpr();
      if (parts.size() == 1)
         return (ControlStructure.EffectExpr)parts.getFirst();
      ControlStructure.SeqExpr result = new ControlStructure.SeqExpr();
      result.parts.addAll (parts);
      return result;
   } // end of sequenceEffect()

   /**
    * Parses one recursive control-effect expression.
    * @param scanner expression scanner
    * @param span outer source span for diagnostics
    * @return parsed expression
    */
   static ControlStructure.EffectExpr parseControlExpr (TextScanner scanner,
      SourceSpan span) {
      SourceWord head = nextControlMeaningAtom (scanner, "(),", span);
      if (head == null)
         throw new RuntimeException ("Unexpected end of structure effect in "
            + sourceLocationText (span));
      String key = canonicalWord (head.text);
      String metasymbol = canonicalMetasymbol (head.text);
      if (scanner.consumeChar ('(') == null) {
         if (ControlStructure.SEGMENT_EMPTY.equals (key) ||
             "EPSILON".equals (key))
            return new ControlStructure.EmptyExpr();
         if ((metasymbol != null) && (metasymbol.length() > 0))
            return new ControlStructure.SegmentExpr (metasymbol);
         return new ControlStructure.SegmentExpr (key);
      }
      if ("CONTROL".equals (key) || "WORD".equals (key) ||
          "TOKEN".equals (key)) {
         SourceWord roleToken = nextControlMeaningAtom (scanner, "(),", span);
         if (roleToken == null)
            throw new RuntimeException ("Missing control role in " +
               head.span.startText());
         if (scanner.consumeChar (')') == null)
            throw new RuntimeException ("Missing ) after CONTROL in " +
               head.span.startText());
         String role = canonicalMetasymbol (roleToken.text);
         if (role == null) role = canonicalControlMode (roleToken.text);
         return new ControlStructure.ControlExpr (role);
      }
      LinkedList<ControlStructure.EffectExpr> args =
         new LinkedList<ControlStructure.EffectExpr>();
      if (scanner.consumeChar (')') == null) {
         while (true) {
            args.add (parseControlExpr (scanner, span));
            if (scanner.consumeChar (',') != null) continue;
            if (scanner.consumeChar (')') != null) break;
            throw new RuntimeException ("Missing , or ) in " +
               head.span.startText());
         }
      }
      if ("SEQ".equals (key) || "CHAIN".equals (key) ||
          "SEQUENCE".equals (key) ||
          "THEN".equals (key)) {
         if (args.size() == 0)
            throw new RuntimeException (
               "SEQUENCE requires at least one argument in " +
               head.span.startText());
         ControlStructure.SeqExpr result = new ControlStructure.SeqExpr();
         result.parts.addAll (args);
         return result;
      }
      if ("GLB".equals (key)) {
         if (args.size() != 2)
            throw new RuntimeException ("GLB requires two arguments in " +
               head.span.startText());
         return new ControlStructure.GlbExpr (
            (ControlStructure.EffectExpr)args.get (0),
            (ControlStructure.EffectExpr)args.get (1));
      }
      if ("STAR".equals (key) || "PISTAR".equals (key) ||
          "IDEMPOTENT".equals (key)) {
         if (args.size() != 1)
            throw new RuntimeException ("STAR requires one argument in " +
               head.span.startText());
         return new ControlStructure.StarExpr (
            (ControlStructure.EffectExpr)args.getFirst());
      }
      throw new RuntimeException ("Unknown control-effect operator " +
         head.text + " in " + head.span.startText());
   } // end of parseControlExpr()

   /**
    * Reads one token from a control-effect expression, preserving
    * &lt;metasymbol names with spaces&gt; as one atom.
    * @param scanner expression scanner
    * @param stopChars extra delimiter characters
    * @param span outer source span for diagnostics
    * @return next atom or null at end
    */
   static SourceWord nextControlMeaningAtom (TextScanner scanner,
      String stopChars, SourceSpan span) {
      scanner.skipIgnorable();
      if (scanner.atEnd()) return null;
      SourceSpan open = scanner.consumeChar ('<');
      if (open == null) return scanner.nextAtom (stopChars);
      SourceWord body = scanner.parseUntil ('>');
      if (body == null)
         throw new RuntimeException ("Unclosed metasymbol in " +
            sourceLocationText (span));
      return new SourceWord ("<" + body.text + ">",
         SourceSpan.covering (open, body.span));
   } // end of nextControlMeaningAtom()

   /**
    * Validates one parsed control structure for internal consistency.
    * @param structure structure to validate
    * @param span source span for diagnostics
    */
   static void validateControlStructure (ControlStructure structure,
      SourceSpan span) {
      if (structure == null)
         throw new RuntimeException ("Missing control structure in " +
            sourceLocationText (span));
      if (structure.name == null || structure.name.length() == 0)
         throw new RuntimeException ("Missing structure name in " +
            sourceLocationText (span));
      structure.ensurePatternFromLegacy();
      if (structure.openRole == null || structure.openRole.length() == 0)
         throw new RuntimeException ("Missing OPEN clause for structure " +
            structure.name + " in " + sourceLocationText (span));
      if (structure.closeRole == null || structure.closeRole.length() == 0)
         throw new RuntimeException ("Missing CLOSE clause for structure " +
            structure.name + " in " + sourceLocationText (span));
      if (structure.segmentCount() == 0)
         throw new RuntimeException ("Missing captured segment for structure " +
            structure.name + " in " + sourceLocationText (span));
      if (structure.meaning == null)
         throw new RuntimeException ("Missing EFFECT clause for structure " +
            structure.name + " in " + sourceLocationText (span));
      if (Spec.CONTROL_END.equals (structure.openRole) ||
          Spec.CONTROL_END.equals (structure.closeRole))
         throw new RuntimeException ("CONTROL END is reserved and cannot be " +
            "used inside STRUCTURE " + structure.name + " in " +
            sourceLocationText (span));
      if (Spec.CONTROL_INDEX.equals (structure.openRole) ||
          Spec.CONTROL_INDEX.equals (structure.closeRole))
         throw new RuntimeException ("CONTROL INDEX is reserved and cannot " +
            "be used inside STRUCTURE " + structure.name + " in " +
            sourceLocationText (span));
      for (int i = 0; i < structure.boundaryCount(); i++) {
         if (Spec.CONTROL_END.equals (structure.boundaryRoleAt (i)))
            throw new RuntimeException ("CONTROL END is reserved and cannot " +
               "be used inside STRUCTURE " + structure.name + " in " +
               sourceLocationText (span));
         if (Spec.CONTROL_INDEX.equals (structure.boundaryRoleAt (i)))
            throw new RuntimeException ("CONTROL INDEX is reserved and cannot "
               + "be used inside STRUCTURE " + structure.name + " in " +
               sourceLocationText (span));
      }
      validateControlMeaning (structure.meaning, structure, span);
   } // end of validateControlStructure()

   /**
    * Validates one parsed control-effect expression against the structure.
    * @param expr expression tree
    * @param structure declared structure
    * @param span source span for diagnostics
    */
   static void validateControlMeaning (ControlStructure.EffectExpr expr,
      ControlStructure structure, SourceSpan span) {
      if (expr == null) return;
      if (expr instanceof ControlStructure.EmptyExpr) return;
      if (expr instanceof ControlStructure.SegmentExpr) {
         String name = ((ControlStructure.SegmentExpr)expr).segmentName;
         if (structure.segmentIndexOf (name) >= 0)
            return;
         throw new RuntimeException ("Unknown segment " + name +
            " in structure " + structure.name + " at " +
            sourceLocationText (span));
      }
      if (expr instanceof ControlStructure.ControlExpr) {
         String role = ((ControlStructure.ControlExpr)expr).role;
         if ("OPEN".equals (role) || "CLOSE".equals (role)) return;
         if ("MID".equals (role) && (structure.boundaryCount() == 1)) return;
         if (structure.usesRole (role)) return;
         throw new RuntimeException ("Unknown control role " + role +
            " in structure " + structure.name + " at " +
            sourceLocationText (span));
      }
      if (expr instanceof ControlStructure.SeqExpr) {
         Iterator<ControlStructure.EffectExpr> it =
            ((ControlStructure.SeqExpr)expr).parts.iterator();
         while (it.hasNext())
            validateControlMeaning ((ControlStructure.EffectExpr)it.next(),
               structure, span);
         return;
      }
      if (expr instanceof ControlStructure.GlbExpr) {
         validateControlMeaning (((ControlStructure.GlbExpr)expr).left,
            structure, span);
         validateControlMeaning (((ControlStructure.GlbExpr)expr).right,
            structure, span);
         return;
      }
      if (expr instanceof ControlStructure.StarExpr) {
         validateControlMeaning (((ControlStructure.StarExpr)expr).inner,
            structure, span);
      }
   } // end of validateControlMeaning()

   /**
    * Adds the built-in control structures required for legacy spec files.
    */
   void installBuiltinControlStructures() {
      installBuiltinControlStructure ("BUILTIN_IF",
         Spec.CONTROL_IF, Spec.CONTROL_ELSE, true, Spec.CONTROL_FI,
         "SEQUENCE(WORD(IF), GLB(ALPHA, BETA))");
      installBuiltinControlStructure ("BUILTIN_WHILE",
         Spec.CONTROL_BEGIN, Spec.CONTROL_WHILE, false,
         Spec.CONTROL_REPEAT,
         "SEQUENCE(STAR(SEQUENCE(ALPHA, WORD(WHILE))), STAR(BETA))");
      installBuiltinControlStructure ("BUILTIN_AGAIN",
         Spec.CONTROL_BEGIN, "", false, Spec.CONTROL_AGAIN,
         "STAR(BODY)");
      installBuiltinControlStructure ("BUILTIN_UNTIL",
         Spec.CONTROL_BEGIN, "", false, Spec.CONTROL_UNTIL,
         "STAR(SEQUENCE(BODY, WORD(UNTIL)))");
      installBuiltinControlStructure ("BUILTIN_DO",
         Spec.CONTROL_DO, "", false, Spec.CONTROL_LOOP,
         "SEQUENCE(WORD(DO), STAR(BODY))");
   } // end of installBuiltinControlStructures()

   /**
    * Adds one built-in structure unless the same role signature already
    * exists in the current specification set.
    * @param name structure name
    * @param openRole opener role
    * @param midRole middle role, or empty
    * @param midOptional true when the middle role is optional
    * @param closeRole closer role
    * @param meaningText declarative meaning expression
    */
   void installBuiltinControlStructure (String name, String openRole,
      String midRole, boolean midOptional, String closeRole,
      String meaningText) {
      ControlStructure structure = new ControlStructure (name)
         .withOpenRole (openRole)
         .withMidRole (midRole, midOptional)
         .withCloseRole (closeRole)
         .withLegacySegments (midRole.length() > 0 ? "alpha" : "body",
            midRole.length() > 0 ? "beta" : "")
         .withMeaning (parseControlMeaning (meaningText, new SourceSpan (
            "<built-in>", 1, 1, 1, 1)), meaningText)
         .withSourceSpan (new SourceSpan ("<built-in>", 1, 1, 1, 1))
         .withBuiltin (true);
      if (Spec.CONTROL_IF.equals (openRole))
         structure.withSyntaxText ("IF <then branch> [ELSE <else branch>] FI")
            .withSegmentNames ("then branch", "else branch");
      else if (Spec.CONTROL_BEGIN.equals (openRole) &&
               Spec.CONTROL_WHILE.equals (midRole))
         structure.withSyntaxText (
            "BEGIN <loop prefix> WHILE <loop body> REPEAT")
            .withSegmentNames ("loop prefix", "loop body");
      else if (Spec.CONTROL_BEGIN.equals (openRole) &&
               Spec.CONTROL_AGAIN.equals (closeRole))
         structure.withSyntaxText ("BEGIN <loop body> AGAIN")
            .withSegmentNames ("loop body", "");
      else if (Spec.CONTROL_BEGIN.equals (openRole) &&
               Spec.CONTROL_UNTIL.equals (closeRole))
         structure.withSyntaxText ("BEGIN <loop body> UNTIL")
            .withSegmentNames ("loop body", "");
      else if (Spec.CONTROL_DO.equals (openRole))
         structure.withSyntaxText ("DO <loop body> LOOP")
            .withSegmentNames ("loop body", "");
      structure.ensurePatternFromLegacy();
      Iterator<ControlStructure> it = controlStructures.iterator();
      while (it.hasNext()) {
         ControlStructure current = (ControlStructure)it.next();
         current.ensurePatternFromLegacy();
         if (current.sameSignature (structure)) return;
      }
      controlStructures.add (structure);
   } // end of installBuiltinControlStructure()

   /**
    * Validates global consistency of all declared structures.
    */
   void validateControlStructures() {
      Hashtable<String, String> roleKinds = new Hashtable<String, String>();
      Iterator<ControlStructure> it = controlStructures.iterator();
      while (it.hasNext()) {
         ControlStructure structure = (ControlStructure)it.next();
         structure.ensurePatternFromLegacy();
         validateRoleKind (roleKinds, structure.openRole, "OPEN", structure);
         validateRoleKind (roleKinds, structure.closeRole, "CLOSE", structure);
         for (int i = 0; i < structure.boundaryCount(); i++)
            validateRoleKind (roleKinds, structure.boundaryRoleAt (i), "MID",
               structure);
      }
   } // end of validateControlStructures()

   /**
    * Validates that a role is used consistently as OPEN, MID, or CLOSE.
    * @param roleKinds accumulated role-position map
    * @param role role to validate
    * @param kind position kind
    * @param structure declaring structure
    */
   static void validateRoleKind (Hashtable<String, String> roleKinds,
      String role, String kind, ControlStructure structure) {
      if (role == null || role.length() == 0) return;
      String previous = (String)roleKinds.get (role);
      if ((previous != null) && !previous.equals (kind))
         throw new RuntimeException ("Control role " + role +
            " cannot be both " + previous + " and " + kind +
            " in structure " + structure.name);
      roleKinds.put (role, kind);
   } // end of validateRoleKind()

   /**
    * Validates that every CONTROL role used by a word spec is declared.
    */
   void validateDeclaredControlWords() {
      Iterator<String> words = keySet().iterator();
      while (words.hasNext()) {
         String word = (String)words.next();
         Spec spec = (Spec)get (word);
         if ((spec == null) || !spec.isControlWord()) continue;
         if (Spec.CONTROL_END.equals (spec.controlMode) ||
             Spec.CONTROL_INDEX.equals (spec.controlMode))
            continue;
         if (!isDeclaredStructureRole (spec.controlMode))
            throw new RuntimeException ("Unknown control role " +
               spec.controlMode + " for " + word);
      }
   } // end of validateDeclaredControlWords()

   /**
    * Tells whether the role appears in any declared structure.
    * @param role control role
    * @return true when the role is known
    */
   boolean isDeclaredStructureRole (String role) {
      Iterator<ControlStructure> it = controlStructures.iterator();
      while (it.hasNext()) {
         if (((ControlStructure)it.next()).usesRole (role)) return true;
      }
      return false;
   } // end of isDeclaredStructureRole()

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
      if (key == null) return null;
      if (key.length() == 0) return null;
      return key;
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
      boolean immediate, String stateMode,
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
          !Spec.PARSE_DEFINITION.equals (parseMode) &&
          !Spec.PARSE_WORD.equals (parseMode))
         throw new RuntimeException ("DEFINE COLON requires PARSE WORD " +
            "(or legacy PARSE DEFINITION) for " + word + " in " +
            sourceLocationText (span));
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
      if ((((parseMode != null) && (parseMode.length() > 0)) ||
           ((defineMode != null) && (defineMode.length() > 0)) ||
           (((controlMode != null) && (controlMode.length() > 0)) &&
            !Spec.CONTROL_INDEX.equals (controlMode))) &&
          !immediate)
         throw new RuntimeException ("IMMEDIATE is required for " + word +
            " in " + sourceLocationText (span));
      if (Spec.CONTROL_INDEX.equals (controlMode) && immediate)
         throw new RuntimeException ("CONTROL INDEX must not be IMMEDIATE " +
            "for " + word + " in " + sourceLocationText (span));
   } // end of validateParserMetadata()

   /**
    * Tells whether metadata implies immediate execution for compatibility.
    * @param parseMode parser mode
    * @param defineMode defining mode
    * @param controlMode control role
    * @return true when the word should default to IMMEDIATE
    */
   static boolean impliedImmediate (String parseMode, String defineMode,
      String controlMode) {
      if ((parseMode != null) && (parseMode.length() > 0)) return true;
      if ((defineMode != null) && (defineMode.length() > 0)) return true;
      if ((controlMode != null) && (controlMode.length() > 0) &&
          !Spec.CONTROL_INDEX.equals (controlMode))
         return true;
      return false;
   } // end of impliedImmediate()

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
      if (spec.immediate) writer.write (" IMMEDIATE");
      if ((spec.stateMode != null) && (spec.stateMode.length() > 0)) {
         writer.write (" STATE ");
         writer.write (spec.stateMode);
      }
   } // end of appendSpecMetadata()

   /**
    * Writes one control-structure declaration block.
    * @param writer destination
    * @param structure declaration to render
    * @throws IOException when the destination write fails
    */
   static void appendControlStructure (BufferedWriter writer,
      ControlStructure structure) throws IOException {
      if (writer == null || structure == null) return;
      String nl = System.getProperty ("line.separator");
      if ((structure.syntaxText != null) && (structure.syntaxText.length() > 0)) {
         appendStructureDirective (writer, "", "SYNTAX", structure.syntaxText,
            false);
         if ((structure.compilationText != null) &&
             (structure.compilationText.length() > 0))
            appendStructureDirective (writer, "  ", "COMPILATION",
               structure.compilationText, false);
         if ((structure.runtimeText != null) &&
             (structure.runtimeText.length() > 0))
            appendStructureDirective (writer, "  ", "RUN-TIME",
               structure.runtimeText, false);
         appendStructureDirective (writer, "  ", "EFFECT",
            structure.meaningText, false);
      } else {
         writer.write ("STRUCTURE ");
         writer.write (formatWordForFile (structure.name));
         writer.write (nl);
         writer.write ("OPEN ");
         writer.write (formatWordForFile (structure.openRole));
         writer.write (nl);
         if (structure.hasMidRole()) {
            writer.write ("MID ");
            writer.write (formatWordForFile (structure.midRole));
            if (structure.midOptional) writer.write (" OPTIONAL");
            writer.write (nl);
         }
         writer.write ("CLOSE ");
         writer.write (formatWordForFile (structure.closeRole));
         writer.write (nl);
         if ((structure.compilationText != null) &&
             (structure.compilationText.length() > 0))
            appendStructureDirective (writer, "", "COMPILATION",
               structure.compilationText, false);
         if ((structure.runtimeText != null) &&
             (structure.runtimeText.length() > 0))
            appendStructureDirective (writer, "", "RUN-TIME",
               structure.runtimeText, false);
         appendStructureDirective (writer, "", "EFFECT", structure.meaningText,
            false);
         writer.write ("ENDSTRUCTURE");
      }
   } // end of appendControlStructure()

   /**
    * Writes one structure directive either inline or as an indented block.
    * @param writer destination
    * @param directive directive keyword without ':'
    * @param text body text
    * @param quoteInline true when one-line bodies should be quoted
    * @throws IOException when the destination write fails
    */
   static void appendStructureDirective (BufferedWriter writer, String indent,
      String directive, String text, boolean quoteInline) throws IOException {
      String nl = System.getProperty ("line.separator");
      String baseIndent = indent == null ? "" : indent;
      String value = text == null ? "" : text;
      if (value.indexOf ('\n') < 0) {
         writer.write (baseIndent + directive + ": ");
         if (quoteInline)
            writer.write (TextScanner.quotedText (value));
         else
            writer.write (value);
         writer.write (nl);
         return;
      }
      writer.write (baseIndent + directive + ":");
      writer.write (nl);
      appendIndentedBlock (writer, value, baseIndent + "  ");
      writer.write (nl);
   } // end of appendStructureDirective()

   /**
    * Writes block text with a simple two-space indent.
    * @param writer destination
    * @param text block text
    * @throws IOException when the destination write fails
    */
   static void appendIndentedBlock (BufferedWriter writer, String text,
      String indent) throws IOException {
      String nl = System.getProperty ("line.separator");
      String baseIndent = indent == null ? "" : indent;
      String value = text == null ? "" : text.replace ("\r", "");
      int start = 0;
      while (start <= value.length()) {
         int end = value.indexOf ('\n', start);
         String line = end >= 0 ? value.substring (start, end) :
            value.substring (start);
         writer.write (baseIndent);
         writer.write (line);
         if (end < 0) break;
         writer.write (nl);
         start = end + 1;
      }
   } // end of appendIndentedBlock()

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
      Iterator<ControlStructure> structures = controlStructures.iterator();
      while (structures.hasNext()) {
         ControlStructure structure = (ControlStructure)structures.next();
         if ((structure == null) || structure.builtin) continue;
         if ((structure.syntaxText != null) &&
             (structure.syntaxText.length() > 0)) {
            result.append (nl);
            result.append (formatStructureDirective ("", "SYNTAX",
               structure.syntaxText, false, nl));
            if ((structure.compilationText != null) &&
                (structure.compilationText.length() > 0))
               result.append (formatStructureDirective ("  ", "COMPILATION",
                  structure.compilationText, false, nl));
            if ((structure.runtimeText != null) &&
                (structure.runtimeText.length() > 0))
               result.append (formatStructureDirective ("  ", "RUN-TIME",
                  structure.runtimeText, false, nl));
            result.append (formatStructureDirective ("  ", "EFFECT",
               structure.meaningText, false, nl));
         } else {
            result.append (nl + "STRUCTURE " +
               formatWordForFile (structure.name));
            result.append (nl + "OPEN " +
               formatWordForFile (structure.openRole));
            if (structure.hasMidRole()) {
               result.append (nl + "MID " +
                  formatWordForFile (structure.midRole));
               if (structure.midOptional) result.append (" OPTIONAL");
            }
            result.append (nl + "CLOSE " +
               formatWordForFile (structure.closeRole));
            if ((structure.compilationText != null) &&
                (structure.compilationText.length() > 0))
               result.append (formatStructureDirective ("", "COMPILATION",
                  structure.compilationText, false, nl));
            if ((structure.runtimeText != null) &&
                (structure.runtimeText.length() > 0))
               result.append (formatStructureDirective ("", "RUN-TIME",
                  structure.runtimeText, false, nl));
            result.append (formatStructureDirective ("", "EFFECT",
               structure.meaningText, false, nl));
            result.append (nl + "ENDSTRUCTURE");
         }
      }
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
         if (spec.immediate) result.append (" IMMEDIATE");
         if ((spec.stateMode != null) && (spec.stateMode.length() > 0))
            result.append (" STATE " + spec.stateMode);
         result.append ("\t" + spec.toString());
      }
      result.append (nl);
      return result.toString();
   } // end of toString()

   /**
    * Formats one structure directive for debug/string output.
    * @param directive directive keyword
    * @param text body text
    * @param quoteInline true when inline text should be quoted
    * @param nl line separator
    * @return rendered directive text including its leading newline
    */
   static String formatStructureDirective (String indent, String directive,
      String text, boolean quoteInline, String nl) {
      String baseIndent = indent == null ? "" : indent;
      String value = text == null ? "" : text;
      if (value.indexOf ('\n') < 0) {
         if (quoteInline)
            return nl + baseIndent + directive + ": " +
               TextScanner.quotedText (value);
         return nl + baseIndent + directive + ": " + value;
      }
      StringBuffer result = new StringBuffer (nl + baseIndent + directive + ":");
      String sanitized = value.replace ("\r", "");
      int start = 0;
      while (start <= sanitized.length()) {
         int end = sanitized.indexOf ('\n', start);
         String line = end >= 0 ? sanitized.substring (start, end) :
            sanitized.substring (start);
         result.append (nl + baseIndent + "  " + line);
         if (end < 0) break;
         start = end + 1;
      }
      return result.toString();
   } // end of formatStructureDirective()

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
