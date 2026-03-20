# evaluator
Forth code symbolic executor
<br>
Compile on the level of evaluator package parent directory: <code>javac evaluator/*.java</code>
<br>
Run the file-based example on the same level: <code>java evaluator.Evaluator</code>
<br>
Run with your own sequence of words: <code>java evaluator.Evaluator </code> &lt;Sequence of Forth words&gt;
<br>
Run the preserved strict profile: <code>java evaluator.Evaluator --system legacy</code>
<br>
Run the more standard-like strict profile:
<code>java evaluator.Evaluator --system forth2012</code>
<br>
Override the default input files when needed:
<code>java evaluator.Evaluator --types mytypes.txt --specs myspecs.txt --prog myprog.txt</code>
<br>
If these file parameters are omitted, the evaluator behaves exactly as before
and uses the files of the selected profile. If both a <code>--prog</code> file
and command-line program words are given, the command-line program words take
precedence.
<br>
The default <code>real</code> profile uses:
<code>ex1types.txt</code>, <code>ex1specs.txt</code>, <code>ex1prog.txt</code>
<br>
The preserved old profile uses:
<code>legacytypes.txt</code>, <code>legacyspecs.txt</code>, <code>legacyprog.txt</code>
<br>
The standard-like strict profile uses:
<code>forth2012types.txt</code>, <code>forth2012specs.txt</code>, <code>forth2012prog.txt</code>
<br>
The bundled demo specs now cover a small Forth-like core: stack shuffles;
numeric words such as <code>+</code>, <code>-</code>, <code>*</code>,
<code>/</code>, <code>MOD</code>, <code>1+</code>, <code>1-</code>,
<code>2*</code>, <code>2/</code>, <code>NEGATE</code>, <code>ABS</code>,
<code>0=</code>, <code>0&lt;</code>, <code>0&gt;</code>, <code>=</code>,
<code>&lt;</code>, and <code>&gt;</code>; memory words such as <code>@</code>,
<code>C@</code>, <code>!</code>, <code>C!</code>, <code>HERE</code>, and
<code>ALLOT</code>; and I/O words such as <code>.</code>, <code>KEY</code>,
<code>EMIT</code>, <code>CR</code>, <code>SPACE</code>, <code>SPACES</code>,
and <code>TYPE</code>. Dot is still modeled as a generic sink with stack
effect <code>( X -- )</code>, so it can print any one symbolic value.
The non-standard word <code>PLUS</code> is retained only as a same-type demo
helper for stack-effect experiments.
<br>
Spec files may now describe parser words explicitly with metadata such as
<code>PARSE UNTIL</code>, <code>PARSE WORD</code>,
<code>PARSE DEFINITION</code>, <code>DEFINE</code>,
<code>CONTROL</code>, <code>IMMEDIATE</code>, and <code>STATE</code>. For
example, <code>"(" PARSE UNTIL ")" IMMEDIATE ( -- )</code>,
<code>".(" PARSE UNTIL ")" IMMEDIATE STATE INTERPRET ( -- )</code>,
<code>CONSTANT PARSE WORD DEFINE CONSTANT IMMEDIATE STATE INTERPRET ( X -- )</code>,
<code>: PARSE WORD DEFINE COLON IMMEDIATE STATE INTERPRET ( -- )</code>, and
<code>; CONTROL END IMMEDIATE STATE COMPILE ( -- )</code>. Structured compile
words can also be declared there, for example
<code>IF CONTROL IF IMMEDIATE STATE COMPILE ( flag -- )</code> and
<code>DO CONTROL DO IMMEDIATE STATE COMPILE ( n[2] n[1] -- )</code>, while
runtime words such as <code>I</code> may simply be restricted with
<code>STATE COMPILE</code>. This lets scanner words, defining words, and the
compile-time control vocabulary live in the spec file rather than being
special only in Java. The evaluator now follows an explicit outer-interpreter
model with interpretation state and compilation state: normal words execute in
interpretation state or are compiled in compilation state, while
<code>IMMEDIATE</code> words execute during compilation. The evaluator still
accepts the older delimiter shorthand such as <code>"(" ")" ( -- )</code>, the
older explicit <code>scan</code> form, and the older
<code>: PARSE DEFINITION ";" DEFINE COLON</code> form for compatibility; type
files may still define named scanner delimiters when needed. This matches the
current bundled behavior where <code>."</code> is an immediate scanner word
that may appear in compilation state, while <code>.(</code> is treated as an
interpretation-state string-printing word with closing <code>)</code> as
delimiter. For compatibility, the older <code>CONTEXT OUTER</code> and
<code>CONTEXT DEFINITION</code> spellings are still accepted when loading spec
files.
<br>
Spec files may also define literal classes, for example
<code>LITERAL INTEGER ( -- n )</code>. Decimal integer tokens such as
<code>0</code>, <code>17</code>, <code>-1</code>, and <code>+42</code> are
recognized directly in program text, and their stack effect comes from that
literal specification so different type systems can choose a different result
type name when needed.
<br>
Program text now supports linear colon definitions through the outer
interpreter: <code>: NAME ... ;</code> starts compilation, <code>;</code>
finishes it, and the bundled profiles also declare <code>CONSTANT</code> and
<code>VARIABLE</code> as immediate defining words.
<br>
Forth word lookup is case-insensitive throughout the evaluator; source text is
left as written, but word names are treated internally as if all letters were
uppercase.
<br>
Inside colon definitions, conditionals are supported as
<code>IF ... FI</code> and <code>IF ... ELSE ... FI</code>;
<code>IF</code> consumes a <code>flag</code>
value and the two branches must have compatible stack effects.
<br>
Loop skeletons are also supported inside colon definitions as
<code>BEGIN ... WHILE ... REPEAT</code>,
<code>BEGIN ... UNTIL</code>, and <code>BEGIN ... AGAIN</code>;
<code>WHILE</code> and <code>UNTIL</code> consume a <code>flag</code>, and the
loop parts must satisfy the existing idempotence approximation used by the
evaluator.
<br>
Counted loops are supported as <code>DO ... LOOP</code>; the evaluator uses a
conservative approximation that consumes two numeric loop parameters
<code>( n[2] n[1] -- )</code>, interpreted in Forth order as
<code>limit start</code>. The loop index starts from <code>start</code>,
advances by implicit step <code>1</code>, and stops before <code>limit</code>,
so the last executed index is <code>limit-1</code>. For example,
<code>7 0 DO I . LOOP</code> prints <code>0 1 2 3 4 5 6</code>. Inside a
counted loop body, <code>I</code> is available as the innermost loop index
with stack effect <code>( -- n )</code>. The loop body must still be
idempotent under the evaluator's current approximation.
<br>
When program text is loaded from a file, user-facing diagnostics now include
the failing line and column together with the relevant source line and caret
marker for semantic clashes such as linear type conflicts, non-comparable
<code>IF ... ELSE ... FI</code> branches, and non-idempotent loop bodies.
<br>
The type system is also profile-dependent now. The default <code>real</code>
profile is more Forth-like as a convenience profile: flags are numbers,
characters are numbers, and the top stack-cell type is also aliased as
<code>cell</code>. The <code>legacy</code> profile preserves the earlier
stricter separation where <code>flag</code> is not a subtype of <code>n</code>.
The <code>forth2012</code> profile follows the Forth-2012 subtype lattice more
closely: <code>flag</code> is separate from numeric types, addresses sit under
<code>u</code>, <code>char</code> sits under <code>+n</code>, dot is typed as
<code>( n -- )</code>, and string-producing scanner words use the standard
stack form <code>c-addr u</code>.
