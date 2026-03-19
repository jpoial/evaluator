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
Program text now supports linear colon definitions: <code>: NAME ... ;</code>
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
The type system is also profile-dependent now. The default <code>real</code>
profile is more Forth-like: flags are numbers, characters are numbers, and
the top stack-cell type is also aliased as <code>cell</code>. The
<code>legacy</code> profile preserves the earlier stricter separation where
<code>flag</code> is not a subtype of <code>n</code>.
