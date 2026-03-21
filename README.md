# evaluator
Forth code symbolic executor
<br>
Compile on the level of evaluator package parent directory: <code>javac evaluator/*.java</code>
<br>
Run the evaluator with explicit files:
<code>java evaluator.Evaluator --types mytypes.txt --specs myspecs.txt --prog myprog.txt</code>
<br>
Or pass the program itself on the command line:
<code>java evaluator.Evaluator --types mytypes.txt --specs myspecs.txt</code>
&lt;Sequence of Forth words&gt;
<br>
The evaluator binary itself requires <code>--types</code> and
<code>--specs</code>; no profile filenames are hardcoded inside Java.
If both a <code>--prog</code> file and command-line program words are given,
the command-line program words take precedence.
<br>
The bundled launcher is optional convenience for the shipped demo profiles:
Linux: <code>./run-evaluator.sh</code>,
<code>./run-evaluator.sh real</code>,
<code>./run-evaluator.sh legacy</code>
Windows: <code>run-evaluator.bat</code>,
<code>run-evaluator.bat real</code>,
<code>run-evaluator.bat legacy</code>
<br>
Without an explicit profile or user-supplied <code>--types</code> /
<code>--specs</code>, the launcher defaults to
<code>forth2012types.txt</code>, <code>forth2012specs.txt</code>, and
<code>forth2012prog.txt</code>.
<br>
In <code>real</code> mode it uses:
<code>ex1types.txt</code>, <code>ex1specs.txt</code>, <code>ex1prog.txt</code>
<br>
In the preserved old profile it uses:
<code>legacytypes.txt</code>, <code>legacyspecs.txt</code>, <code>legacyprog.txt</code>
<br>
In <code>forth2012</code> mode it uses:
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
<code>parse until</code>, <code>parse word</code>,
<code>parse definition</code>, <code>define</code>,
<code>control</code>, and <code>state</code>. Parser words, defining words,
and control words are implicitly immediate, so the bundled specs omit a
separate <code>immediate</code> marker. For example,
<code>"(" parse until ")" ( -- )</code>,
<code>".(" parse until ")" state interpret ( -- )</code>,
<code>CONSTANT parse word define ( X -- )</code>,
<code>: parse word define colon ( -- )</code>, and
<code>; control end ( -- )</code>. Structured compile
words can also be declared there, for example
<code>IF control if ( flag -- )</code> and
<code>DO control do ( n[2] n[1] -- )</code>, while
runtime words such as <code>I</code> may simply be restricted with
<code>state compile</code>. This lets scanner words, defining words, and the
compile-time control vocabulary live in the spec file rather than being
special only in Java. The evaluator now follows an explicit outer-interpreter
model with interpretation state and compilation state: normal words execute in
interpretation state or are compiled in compilation state, while
immediate behavior is implied for parser, defining, and control words.
Defining words also default to interpretation state, and control words default
to compilation state, so bundled specs now write <code>state</code> only where
it adds information. Bare <code>define</code> now infers simple
constant-like <code>( x -- )</code> and variable-like <code>( -- y )</code>
definers from the stack effect, while colon definitions still use
<code>define colon</code>.
The evaluator still
accepts the older delimiter shorthand such as <code>"(" ")" ( -- )</code>, the
older explicit <code>scan</code> form, and the older
<code>: parse definition ";" define colon</code> form for compatibility; type
files may still define named scanner delimiters when needed. This matches the
current bundled behavior where <code>."</code> is an immediate scanner word
that may appear in compilation state, while <code>.(</code> is treated as an
interpretation-state string-printing word with closing <code>)</code> as
delimiter. For compatibility, the older <code>context outer</code> and
<code>context definition</code> spellings are still accepted when loading spec
files, and an explicit <code>immediate</code> marker is still accepted when it
adds information. Control structures may also be declared explicitly with a top-level
<code>syntax:</code> block, followed by indented syntax lines such as
<code>IF &lt;then branch&gt;</code>, <code>[ELSE &lt;else branch&gt;]</code>,
and <code>FI</code>, and a lower-level indented <code>effect:</code> block.
For example, <code>IF</code> may be followed by
<code>either &lt;then branch&gt; &lt;else branch&gt;</code>. The small
control-effect algebra
currently supports line-by-line sequencing, <code>either</code> for branch
merge, <code>repeat</code> for idempotent repetition, bare control words such
as <code>IF</code> or <code>DO</code>, and segment names taken from the
<code>&lt;...&gt;</code> metasymbols in <code>syntax</code>. For example,
<code>repeat &lt;loop body&gt; UNTIL</code> means the repeated effect of the
body followed by the stack effect of <code>UNTIL</code> on each pass.
This makes flag consumption visible directly in the control-word declaration
such as <code>IF ... ( flag -- )</code> or <code>UNTIL ... ( flag -- )</code>.
Optional <code>compilation:</code> and <code>run-time:</code> blocks are still
accepted as extra documentation, but they are not used for checking.
The older wrapped <code>structure ... endstructure</code> form and the older
algebraic effect form are still accepted too, including
<code>sequence(...)</code>, <code>glb(...)</code>, <code>star(...)</code>,
<code>word(...)</code>, and the older <code>meaning:</code> spelling.
The older <code>open</code>/<code>mid</code>/<code>close</code> structure form
is still accepted for compatibility. A <code>syntax</code> line may also contain more
than two captured parts, for example a fixed switch-like form such as
<code>SWITCH &lt;selector&gt; OF &lt;first branch&gt; OF &lt;second branch&gt; [DEFAULT &lt;default branch&gt;] ENDSWITCH</code>.
If no structure block is given, the legacy
<code>IF</code>/<code>BEGIN</code>/<code>DO</code> families are still loaded
implicitly for compatibility. The bundled profiles now treat
<code>THEN</code> and <code>FI</code> as synonyms, and the standard-like
profile also includes practical source words such as backslash line comments,
<code>CHAR</code>, <code>[CHAR]</code>, tick words <code>'</code> and
<code>[']</code>, <code>ABORT"</code>, <code>C"</code>, and defining words
such as <code>CREATE</code>.
<br>
Spec files may also define literal classes with no inputs, for example
<code>literal integer ( -- n )</code>. Decimal integer tokens such as
<code>0</code>, <code>17</code>, <code>-1</code>, and <code>+42</code> are
recognized directly in program text, and their stack effect comes from that
literal specification so different type systems can choose a different result
type name when needed. A standard-like profile may also define
<code>literal double ( -- d )</code>, in which case decimal tokens with a
trailing period such as <code>1234.</code> or <code>-7.</code> are recognized
as double-cell integer literals.
<br>
Program text now supports linear colon definitions through the outer
interpreter: <code>: NAME ... ;</code> starts compilation, <code>;</code>
finishes it, and the bundled profiles also declare <code>CONSTANT</code> and
<code>VARIABLE</code> as defining words that therefore execute during
compilation.
<br>
Forth word lookup is case-insensitive throughout the evaluator; source text is
left as written, but word names are treated internally as if all letters were
uppercase.
<br>
Inside colon definitions, conditionals are supported as
<code>IF ... THEN</code> / <code>IF ... FI</code> and
<code>IF ... ELSE ... THEN</code> / <code>IF ... ELSE ... FI</code>;
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
<code>IF ... ELSE ... THEN</code> branches, and non-idempotent loop bodies.
The checker also continues after a recoverable program error: it skips an
invalid top-level word or abandons an invalid definition, then keeps scanning
the rest of the file and reports later errors as separate diagnostics.
<br>
The type system is also profile-dependent now. The <code>real</code> profile
is more Forth-like as a convenience profile: flags are numbers, characters are
numbers, and the top stack-cell type is also aliased as <code>cell</code>.
The <code>legacy</code> profile preserves the earlier stricter separation
where <code>flag</code> is not a subtype of <code>n</code>. The
<code>forth2012</code> profile follows the Forth-2012 subtype lattice more
closely: <code>flag</code> is separate from numeric types, addresses sit under
<code>u</code>, <code>char</code> sits under <code>+n</code>, dot is typed as
<code>( n -- )</code>, and string-producing scanner words use the standard
stack form <code>c-addr u</code>.
