// file: SourceWord.java

package evaluator;

/**
 * One scanned source token together with its original span.
 * @author Codex
 * @version 0.1
 * @since 1.5
 */
public class SourceWord {

   String text;
   SourceSpan span;
   boolean quoted;

   SourceWord (String word, SourceSpan where) {
      this (word, where, false);
   } // end of constructor

   SourceWord (String word, SourceSpan where, boolean wasQuoted) {
      text = word;
      span = where;
      quoted = wasQuoted;
   } // end of constructor

} // end of SourceWord

// end of file
