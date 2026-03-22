// file: TextScanner.java

package evaluator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;

/**
 * Source-aware text scanner with Forth-style word parsing helpers.
 * @author Codex
 * @version 0.1
 * @since 1.5
 */
public class TextScanner {

   String sourceName;
   String sourceText;
   LinkedList<String> sourceLines;
   int offset = 0;
   int line = 1;
   int column = 1;
   int lastLine = 1;
   int lastColumn = 1;

   TextScanner (String name, String text) {
      sourceName = name;
      sourceText = text == null ? "" : text;
      sourceLines = splitLines (sourceText);
   } // end of constructor

   /**
    * Reads a scanner input from a text file.
    * @param fileName local file name
    * @return scanner over the file text
    * @throws IOException if the file cannot be read
    */
   static TextScanner fromFile (String fileName) throws IOException {
      BufferedReader reader = null;
      StringBuffer text = new StringBuffer ("");
      String nl = "\n";
      boolean firstLine = true;
      try {
         reader = new BufferedReader (new FileReader (fileName));
         String line;
         while ((line = reader.readLine()) != null) {
            if (!firstLine) text.append (nl);
            text.append (line);
            firstLine = false;
         }
      } finally {
         if (reader != null) {
            try {
               reader.close();
            } catch (IOException e) {
               // ignore close failure in demo code
            }
         }
      }
      return new TextScanner (fileName, text.toString());
   } // end of fromFile()

   /**
    * Returns the reconstructed source text.
    * @return source text
    */
   String sourceText() {
      return sourceText;
   } // end of sourceText()

   /**
    * Returns the source split into physical lines.
    * @return source lines copy
    */
   LinkedList<String> sourceLines() {
      return new LinkedList<String> (sourceLines);
   } // end of sourceLines()

   /**
    * Reads all whitespace-delimited words until end of input.
    * @return scanned words
    */
   LinkedList<SourceWord> allWords() {
      LinkedList<SourceWord> result = new LinkedList<SourceWord>();
      SourceWord current = null;
      while ((current = nextWord()) != null) {
         result.add (current);
      }
      return result;
   } // end of allWords()

   /**
    * Reads the next Forth-style word using whitespace as the delimiter.
    * A backslash starts a line comment and is not returned as part of a word.
    * @return scanned word or null at end of input
    */
   SourceWord nextWord() {
      return nextWord ("");
   } // end of nextWord()

   /**
    * Reads the next program word using whitespace as the delimiter.
    * Unlike nextWord(), this leaves parser-word comments such as '\' or '#'
    * in the token stream so shared spec files can model them explicitly.
    * @return scanned program word or null at end of input
    */
   SourceWord nextProgramWord() {
      skipWhitespace();
      return readProgramWord ("");
   } // end of nextProgramWord()

   /**
    * Reads the next atom, allowing quoted strings in addition to plain words.
    * @return scanned atom or null at end of input
    */
   SourceWord nextAtom() {
      return nextAtom ("");
   } // end of nextAtom()

   /**
    * Reads the next word using whitespace and the given extra stop
    * characters as delimiters. The stop character itself is left unread.
    * @param stopChars additional delimiter characters
    * @return scanned word or null at end of input
    */
   SourceWord nextWord (String stopChars) {
      skipIgnorable();
      return readWord (stopChars);
   } // end of nextWord()

   /**
    * Reads the next atom using whitespace and the given extra stop
    * characters as delimiters. Quoted atoms are unescaped automatically.
    * @param stopChars additional delimiter characters
    * @return scanned atom or null at end of input
    */
   SourceWord nextAtom (String stopChars) {
      skipIgnorable();
      return readAtom (stopChars);
   } // end of nextAtom()

   /**
    * Reads all words until the current physical line ends.
    * @return words from one line, empty list for blank/comment line,
    *   or null at end of input
    */
   LinkedList<SourceWord> nextLineWords() {
      if (atEnd()) return null;
      LinkedList<SourceWord> result = new LinkedList<SourceWord>();
      while (!atEnd()) {
         char current = currentChar();
         if (current == '\\') {
            skipLineComment();
            continue;
         }
         if (current == '\n') {
            advance();
            return result;
         }
         if (Character.isWhitespace (current)) {
            advance();
            continue;
         }
         result.add (readWord (""));
      }
      return result;
   } // end of nextLineWords()

   /**
    * Reads all atoms until the current physical line ends.
    * @return atoms from one line, empty list for blank/comment line,
    *   or null at end of input
    */
   LinkedList<SourceWord> nextLineAtoms() {
      if (atEnd()) return null;
      LinkedList<SourceWord> result = new LinkedList<SourceWord>();
      while (!atEnd()) {
         char current = currentChar();
         if (current == '\\') {
            skipLineComment();
            continue;
         }
         if (current == '\n') {
            advance();
            return result;
         }
         if (Character.isWhitespace (current)) {
            advance();
            continue;
         }
         result.add (readAtom (""));
      }
      return result;
   } // end of nextLineAtoms()

   /**
    * Consumes the expected delimiter after skipping ignorable text.
    * @param expected required delimiter
    * @return source span of the consumed character or null if absent
    */
   SourceSpan consumeChar (char expected) {
      skipIgnorable();
      if (atEnd()) return null;
      if (currentChar() != expected) return null;
      SourceSpan result = new SourceSpan (sourceName, line, column, line,
         column);
      advance();
      return result;
   } // end of consumeChar()

   /**
    * Reads raw text until the given delimiter and consumes the delimiter.
    * The current scanner position is used as the start of the returned span.
    * @param delimiter closing delimiter
    * @return captured text or null if the delimiter is missing
    */
   SourceWord parseUntil (char delimiter) {
      return parseUntil (String.valueOf (delimiter));
   } // end of parseUntil()

   /**
    * Reads raw text until the given delimiter and consumes the delimiter.
    * @param delimiter closing delimiter string
    * @return captured text or null if the delimiter is missing
    */
   SourceWord parseUntil (String delimiter) {
      if ((delimiter == null) | (delimiter.length() == 0))
         return new SourceWord ("", positionSpan());
      int startLine = line;
      int startColumn = column;
      int endLine = line;
      int endColumn = Math.max (1, column);
      boolean hasText = false;
      StringBuffer text = new StringBuffer ("");
      while (!atEnd()) {
         if (startsWith (delimiter)) {
            for (int i = 0; i < delimiter.length(); i++) {
               advance();
            }
            return new SourceWord (text.toString(), new SourceSpan (
               sourceName, startLine, startColumn,
               hasText ? endLine : startLine,
               hasText ? endColumn : startColumn));
         }
         char current = currentChar();
         text.append (current);
         hasText = true;
         endLine = line;
         endColumn = column;
         advance();
      }
      return null;
   } // end of parseUntil()

   /**
    * Returns the current source position as a span.
    * @return current position
    */
   SourceSpan positionSpan() {
      return new SourceSpan (sourceName, line, column, line, column);
   } // end of positionSpan()

   /**
    * Returns the source span of the last consumed character.
    * @return last consumed character span or current position if none
    */
   SourceSpan lastConsumedSpan() {
      return new SourceSpan (sourceName, lastLine, lastColumn, lastLine,
         lastColumn);
   } // end of lastConsumedSpan()

   /**
    * Skips whitespace only, leaving line comments in place.
    */
   void skipWhitespace() {
      while (!atEnd()) {
         if (!Character.isWhitespace (currentChar())) return;
         advance();
      }
   } // end of skipWhitespace()

   /**
    * Skips whitespace and backslash-comments.
    */
   void skipIgnorable() {
      while (!atEnd()) {
         char current = currentChar();
         if (Character.isWhitespace (current)) {
            advance();
            continue;
         }
         if (current == '\\') {
            skipLineComment();
            continue;
         }
         return;
      }
   } // end of skipIgnorable()

   /**
    * Reads one word from the current scanner position.
    * @param stopChars extra delimiter characters
    * @return scanned word or null if a delimiter is encountered immediately
    */
   SourceWord readWord (String stopChars) {
      if (atEnd()) return null;
      int startLine = line;
      int startColumn = column;
      int endLine = line;
      int endColumn = column;
      StringBuffer text = new StringBuffer ("");
      while (!atEnd()) {
         char current = currentChar();
         if (Character.isWhitespace (current) | (current == '\\') |
             isStopChar (current, stopChars))
            break;
         text.append (current);
         endLine = line;
         endColumn = column;
         advance();
      }
      if (text.length() == 0) return null;
      return new SourceWord (text.toString(), new SourceSpan (sourceName,
         startLine, startColumn, endLine, endColumn));
   } // end of readWord()

   /**
    * Reads one program word without treating parser-word comment markers such
    * as '\' or '#' as implicit delimiters.
    * @param stopChars extra delimiter characters
    * @return scanned word or null if a delimiter is encountered immediately
    */
   SourceWord readProgramWord (String stopChars) {
      if (atEnd()) return null;
      int startLine = line;
      int startColumn = column;
      int endLine = line;
      int endColumn = column;
      StringBuffer text = new StringBuffer ("");
      while (!atEnd()) {
         char current = currentChar();
         if (Character.isWhitespace (current) |
             isStopChar (current, stopChars))
            break;
         text.append (current);
         endLine = line;
         endColumn = column;
         advance();
      }
      if (text.length() == 0) return null;
      return new SourceWord (text.toString(), new SourceSpan (sourceName,
         startLine, startColumn, endLine, endColumn));
   } // end of readProgramWord()

   /**
    * Reads one atom from the current scanner position.
    * @param stopChars extra delimiter characters
    * @return scanned atom or null if a delimiter is encountered immediately
    */
   SourceWord readAtom (String stopChars) {
      if (atEnd()) return null;
      if (currentChar() == '"') return readQuotedAtom();
      return readWord (stopChars);
   } // end of readAtom()

   /**
    * Reads a quoted atom with simple backslash escapes.
    * @return scanned quoted atom or null if unterminated
    */
   SourceWord readQuotedAtom() {
      int startLine = line;
      int startColumn = column;
      advance();   // opening quote
      StringBuffer text = new StringBuffer ("");
      while (!atEnd()) {
         char current = currentChar();
         if (current == '"') {
            advance();
            return new SourceWord (text.toString(), new SourceSpan (
               sourceName, startLine, startColumn, lastLine, lastColumn),
               true);
         }
         if (current == '\\') {
            advance();
            if (atEnd()) return null;
            current = currentChar();
            switch (current) {
               case 'n': text.append ('\n'); break;
               case 'r': text.append ('\r'); break;
               case 't': text.append ('\t'); break;
               case '"': text.append ('"'); break;
               case '\\': text.append ('\\'); break;
               default: text.append (current); break;
            }
            advance();
         } else {
            text.append (current);
            advance();
         }
      }
      return null;
   } // end of readQuotedAtom()

   /**
    * Tells whether the current input has been fully consumed.
    * @return true at end of input
    */
   boolean atEnd() {
      return offset >= sourceText.length();
   } // end of atEnd()

   /**
    * Returns the current input character.
    * @return current character
    */
   char currentChar() {
      return sourceText.charAt (offset);
   } // end of currentChar()

   /**
    * Advances the scanner by one character while keeping line/column state.
    */
   void advance() {
      char current = sourceText.charAt (offset);
      lastLine = line;
      lastColumn = column;
      offset++;
      if (current == '\n') {
         line++;
         column = 1;
      } else if (current != '\r') {
         column++;
      }
   } // end of advance()

   /**
    * Skips one backslash-style line comment used by type/spec scanners.
    */
   void skipLineComment() {
      while (!atEnd()) {
         char current = currentChar();
         if (current == '\n') return;
         advance();
      }
   } // end of skipLineComment()

   /**
    * Tells whether a character belongs to the extra stop set.
    * @param current character under consideration
    * @param stopChars extra stop characters
    * @return true if current is a stop character
    */
   static boolean isStopChar (char current, String stopChars) {
      return (stopChars != null) & (stopChars.indexOf (current) >= 0);
   } // end of isStopChar()

   /**
    * Tells whether the unread input starts with the given delimiter.
    * @param delimiter candidate delimiter
    * @return true if delimiter matches at the current position
    */
   boolean startsWith (String delimiter) {
      if (delimiter == null) return false;
      if (delimiter.length() == 0) return true;
      if ((offset + delimiter.length()) > sourceText.length()) return false;
      return sourceText.regionMatches (offset, delimiter, 0,
         delimiter.length());
   } // end of startsWith()

   /**
    * Quotes one text fragment for scanner/type/spec file output.
    * @param text raw text
    * @return quoted representation
    */
   static String quotedText (String text) {
      String value = text;
      if (value == null) value = "";
      StringBuffer result = new StringBuffer ("\"");
      for (int i = 0; i < value.length(); i++) {
         char current = value.charAt (i);
         switch (current) {
            case '\n': result.append ("\\n"); break;
            case '\r': result.append ("\\r"); break;
            case '\t': result.append ("\\t"); break;
            case '"': result.append ("\\\""); break;
            case '\\': result.append ("\\\\"); break;
            default: result.append (current); break;
         }
      }
      result.append ('"');
      return result.toString();
   } // end of quotedText()

   /**
    * Splits text into physical lines for later diagnostics.
    * @param text source text
    * @return list of source lines
    */
   static LinkedList<String> splitLines (String text) {
      LinkedList<String> result = new LinkedList<String>();
      StringBuffer line = new StringBuffer ("");
      for (int i = 0; i < text.length(); i++) {
         char current = text.charAt (i);
         if (current == '\r') continue;
         if (current == '\n') {
            result.add (line.toString());
            line = new StringBuffer ("");
         } else {
            line.append (current);
         }
      }
      if ((text.length() == 0) | (text.charAt (text.length() - 1) != '\n'))
         result.add (line.toString());
      return result;
   } // end of splitLines()

} // end of TextScanner

// end of file
