# Project Summary: `evaluator`

## What This Repository Is

This repository is a compact Java prototype for **symbolic stack-effect analysis of Forth programs**. Its purpose is not to execute Forth code, but to reason about how a sequence of Forth words transforms the stack:

- what types must be present before execution,
- what types are produced after execution,
- which stack items are "the same item moved around",
- and where type conflicts make a program suspicious or invalid.

The project sits at the intersection of:

- Forth stack-effect notation,
- static typing for originally typeless stack languages,
- abstract interpretation / must-analysis,
- and algebraic models of stack behavior.

In practical terms, the repository contains:

- a small Java package `evaluator/` that implements the symbolic core,
- five PDF papers/slides that show the theory and its evolution,
- and a minimal `README.md` showing how to compile and run the demo.

## Materials Reviewed

I reviewed:

- `README.md`
- `EuroForth90_Algebraic.pdf`
- `EuroForth91_Multiple.pdf`
- `euro02.pdf`
- `ef06poial2.pdf`
- `ef08poial.pdf`
- all Java files in `evaluator/`

Note: the 1990 and 1991 EuroForth papers are scanned/image-heavy PDFs, so their text had to be inspected from rendered pages rather than clean text extraction. The later papers/slides were text-extractable and align closely with the current Java code.

## Executive Summary

The repository is best understood as a **research prototype** that implements the core of a typed stack-effect calculus for linear Forth code.

The theory evolves across the papers like this:

1. **1990**: define stack-effects algebraically as an abstract structure for reasoning about Forth-generated programs.
2. **1991**: extend the model from a single effect to **sets of effects**, so control structures and multiple possible behaviors can be represented.
3. **2002**: refine the model with **typed wildcards**, **subtyping**, and **polymorphism**, so symbolic effects can preserve both type precision and item identity.
4. **2006**: frame the work explicitly as **must-analysis** for typeless stack languages, emphasizing `glb`, idempotents, and loop reasoning.
5. **2008**: package the core ideas as a Java framework with classes that mirror the formal objects: types, symbols, effects, sets of effects, sequences of effects, and a small evaluator/demo.

The Java code in this repo implements mainly the **linear-sequence core** of that theory. It does a solid job of:

- representing typed stack effects,
- composing them symbolically,
- propagating more specific types through a program,
- preserving wildcard identities across stack manipulation,
- and detecting mismatches.

It does **not** yet implement the full vision described in the papers:

- no full multiple-stack-effects engine for branches or other alternatives,
- no broad Forth front end beyond the current source-level parser,
- no toolchain or IDE integration,
- and only a small profile-driven demo vocabulary.

So the repository is less "finished analyzer" and more "well-focused computational kernel for a larger analyzer."

## Historical Arc Across the PDFs

## 1. `EuroForth90_Algebraic.pdf`

This is the earliest layer of the project. Its title is **"Algebraic Specifications of Stack-Effects for Forth Programs."**

The paper introduces the main idea that each Forth word can be described by a stack effect of the form:

`input-types --- output-types`

or, in the paper's algebraic notation, `(s1, s2)` over lists of types.

The key contribution is an algebra for composing such effects:

- stack effects form a structure related to the **polycyclic monoid**,
- there is an empty effect / identity,
- there is a null or clash effect for errors,
- and composition models parameter passing through the stack.

The focus here is still fairly algebraic and language-agnostic. The paper is concerned with:

- checking whether stack usage is consistent,
- describing correctness/closure properties of programs,
- and providing a formal basis for reasoning about Forth-like generated code.

This paper is the conceptual ancestor of the `SpecList.multiply(...)` logic in the Java code.

## 2. `EuroForth91_Multiple.pdf`

This paper is titled **"Multiple Stack-effects of Forth-programs."**

The main shift here is from a **single stack effect** to a **set of stack effects**. That matters because real Forth programs contain control structures:

- `IF ... THEN`
- `IF ... ELSE ... THEN`
- `BEGIN ... WHILE ... REPEAT`
- `BEGIN ... UNTIL`
- `BEGIN ... AGAIN`
- counted loops

Those constructs can have more than one possible local stack behavior, so the paper defines:

- specifications as sets of stack effects,
- closure operators over those sets,
- and formulas for control constructs in terms of products and closures.

This is an important evolutionary step: it recognizes that linear composition alone is not enough for real programs.

However, the current Java repo still carries only a **partial** version of this stage:

- `SpecSet` is still a dictionary from words to single `Spec` values, not sets of alternative effects,
- `Spec.glb(...)`, `idemp(...)`, and `piStar(...)` preserve the later branch/loop reasoning ideas,
- `ProgText` and the declarative control-structure support now parse colon definitions containing `IF ... ELSE ... FI`, `BEGIN ... WHILE ... REPEAT`, `BEGIN ... UNTIL`, `BEGIN ... AGAIN`, `DO ... LOOP`, and custom `syntax:` / `effect:` structures,
- but completed control constructs are still checked by collapsing them to one merged `Spec` rather than carrying an explicit set of alternatives.

In other words, the 1991 paper points toward the larger intended system, but the code in this repo implements only the simpler linear kernel.

## 3. `euro02.pdf`

These slides are especially important because they line up directly with the Java implementation.

The title is **"Stack effect calculus with typed wildcards, polymorphism and inheritance."**

This is where the modern version of the idea becomes clear:

- types form a hierarchy,
- the more exact type wins during matching,
- wildcarded symbols are numbered uniquely,
- and wildcard identity means "same symbolic stack item" rather than just "some arbitrary variable."

This step adds the features that make the calculus useful rather than merely formal:

- **subtyping**
- **typed wildcards**
- **symbolic identity tracking**
- **polymorphic words**

The slides also distinguish between a loose generic `+` and a stricter
same-type operator. The current bundled demo vocabulary has since been made
more Forth-like, so the shipped `+` is numeric `( n n -- n )`, while the
non-standard helper `PLUS` is retained as a same-type demo word
`( x[1] x[1] -- x[1] )`.

The 2002 slides are the clearest conceptual match for:

- `TypeSystem.relation(...)`
- `TypeSymbol.position`
- wildcard renaming via `incrementWild(...)` / `normalize(...)`
- and sequence-wide substitution during composition.

## 4. `ef06poial2.pdf`

This paper, **"Typing Tools for Typeless Stack Languages,"** repositions the work in program-analysis terms.

The important shift is that the project is now explicitly described as **must-analysis**:

- composition models a linear program segment,
- `glb` models merging of alternative branches,
- and an **idempotent** effect models a loop whose net stack state is preserved.

The paper also makes a mathematically important point:

- the full set of stack effects forms a semilattice with greatest lower bounds,
- but only the subset of idempotents forms a lattice suitable for the loop reasoning being used.

This paper explains the intended meaning of several methods that appear in the Java code:

- `Spec.glb(...)`
- `Spec.idemp(...)`
- `Spec.piStar(...)`

It also frames the practical goal clearly: validate legacy Forth or other stack-machine code statically, especially where runtime typing is unavailable or too expensive.

## 5. `ef08poial.pdf`

This paper, **"Java Framework for Static Analysis of Forth Programs,"** is the direct map from theory to code.

It explains the package almost class-for-class:

- `TypeSymbol`
- `TypeSystem`
- `Tvector`
- `Spec`
- `SpecSet`
- `SpecList`
- `ProgText`
- `Evaluator`

It also says explicitly that:

- the original framework focused on **linear sequences of words**,
- the package was a **prototype**,
- and real tooling would require extensibility and richer parsing.

That still fits the repository at a high level, although the current code now also includes file-backed profiles, parser words, colon definitions, literal classes, and declarative control structures.

## Core Theory Behind the Project

## Stack Effect Notation

The project uses the familiar Forth stack comment style:

`( before -- after )`

The stack top is on the **right**, so:

- `( a b -- c )` means `b` is the topmost input,
- and `c` becomes the topmost output.

The code models these sides as:

- `leftSide`: symbolic input stack
- `rightSide`: symbolic output stack

inside the `Spec` class.

## Types and Subtypes

The shipped demo now includes three type-system profiles:

- `real` in `ex1types.txt`
- `legacy` in `legacytypes.txt`
- `forth2012` in `forth2012types.txt`

The default `real` profile is more Forth-like:

- `a-addr < c-addr < addr < X`
- `char < n < X`
- `flag < n < X`
- `xt < X`

with aliases such as `cell = X`, `aa = a-addr`, `ca = c-addr`, `a = addr`,
`c = char`, `x = X`, `N = n`, and `f = flag`.

The preserved `legacy` profile keeps the earlier stricter separation:

- `a-addr < c-addr < addr < X`
- `char < n < X`
- `flag < X`

The stricter `forth2012` profile follows the standard lattice more closely:

- `+n < n`
- `+n < u`
- `char < +n`
- `flag < X`
- `a-addr < c-addr < addr < u < X`

This is still intentionally small, but it demonstrates the main idea: when
two symbolic items are matched, the analyzer tries to keep the **most exact
compatible type**. The `real` profile is a more convenient Forth-like default,
while `legacy` and `forth2012` keep stricter distinctions.

## Wildcards and Positional Identity

The most distinctive idea in the project is that a stack item is represented not only by a type, but also by a **wildcard index**.

For example:

- `SWAP` is not just `(X X -- X X)`
- it is `(X[2] X[1] -- X[1] X[2])`

That means:

- there are two distinct stack items,
- and the operation swaps their positions without forgetting which is which.

This is why the analyzer can preserve identity through stack shuffling words such as:

- `SWAP`
- `DUP`
- `OVER`
- `ROT`

Without indexed wildcards, the analyzer would know only coarse types, not the flow of symbolic items.

## Composition

Composition is the operation that evaluates a sequence of Forth words.

Conceptually:

- the output stack of one effect meets the input stack of the next,
- matching symbols are unified,
- the more exact compatible type wins,
- fresh wildcard identities are introduced when two local symbols become one shared symbol,
- and substitutions are propagated through the whole sequence under analysis.

This last point matters: composition is **context-sensitive across the current analysis scope**, not a purely local concatenation of two isolated signatures.

That matches the 2002 slides and is implemented in `SpecList.multiply(...)`.

## Greatest Lower Bound (`glb`)

`glb` is used in the papers to merge different alternative branches conservatively.

In must-analysis terms, the result is the strongest guarantee that is still valid for all branches.

In the code, `Spec.glb(...)`:

- checks whether the two effects have compatible net stack shape,
- aligns longer and shorter effects,
- unifies corresponding symbols,
- and normalizes the result.

The current repo now parses branch syntax inside colon definitions, and this
operation is the reusable merge primitive those control structures rely on.

## Idempotence and `piStar`

Loops are treated in the theory as effects that should preserve stack state overall:

- an idempotent effect satisfies `e = e * e`
- intuitively, applying the loop body again does not change the abstract stack shape

The code provides two related operations:

- `idemp(...)`: try to derive the nearest idempotent by matching left and right sides
- `piStar(...)`: evaluate two copies of the effect and then compute a `glb` with the original, corresponding to the paper's loop approximation idea `glb(e, ee)`

The current repo now also parses loop syntax inside colon definitions, so
these mathematical primitives are part of the executable checking path rather
than dead theoretical leftovers.

## Java Architecture

## `TypeSymbol`

`TypeSymbol` is the atom of the whole system.

It stores:

- a symbolic type name such as `X`, `n`, `flag`, `a-addr`
- a wildcard/position index such as `1`, `2`, `3`

Its role is simple but fundamental:

- type says what kind of value this is,
- position says which symbolic stack item it is.

## `Tvector`

`Tvector` is a mutable list of `TypeSymbol` values representing one side of a stack effect.

It provides:

- deep cloning
- string formatting
- substitution of one symbol by another

This class is small, but it is heavily used by all higher-level transformations.

## `TypeSystem`

`TypeSystem` implements the subtype relation.

It does this with:

- a mapping from type names to integer indices,
- a relation matrix,
- and a normalization pass that fills implied inverse/synonym/transitive relationships.

The transitive closure is computed using a Floyd-Warshall-like triple loop.

This means the Java implementation does not encode type rules procedurally one case at a time. Instead, it turns subtype reasoning into a matrix query:

- `0` = incompatible
- `1` = first is subtype of second
- `2` = first is supertype of second
- `3` = equal/synonym

That relation is the decision procedure used everywhere else.

## `Spec`

`Spec` is the central semantic object: one symbolic stack effect.

It contains:

- `leftSide`
- `rightSide`
- a `TypeSystem` reference
- a `parseString` work field
- `maxPosIndex` for fresh wildcard generation

The most important methods are:

- `glb(...)`
- `idemp(...)`
- `piStar(...)`
- `cprefix(...)`
- `unify(...)`
- `incrementWild(...)`
- `substitute(...)`
- `maxPos()`

If `TypeSymbol` is the atom, `Spec` is the molecule.

## `SpecSet`

`SpecSet` maps Forth words to their stack-effect specs.

In the paper this mapping is meant to be dynamic and extensible. In the current repo it can now:

- load specs from a file,
- define or replace specs programmatically,
- and save the current set back to disk in the same text format.

The bundled example vocabulary now includes a more Forth-like core:

- stack shuffles such as `OVER`, `SWAP`, `DUP`, `DROP`, `ROT`,
- arithmetic and comparison words such as `+`, `-`, `*`, `/`, `MOD`,
  `1+`, `1-`, `2*`, `2/`, `NEGATE`, `ABS`, `0=`, `0<`, `0>`, `=`, `<`, `>`,
- memory and address words such as `@`, `C@`, `!`, `C!`, `HERE`, `DP`,
  `ALLOT`, `CELL+`, `CHAR+`, `CELLS`, `CHARS`,
- and I/O-style words such as `.`, `KEY`, `EMIT`, `CR`, `SPACE`,
  `SPACES`, `TYPE`.

Dot is modeled with stack effect `( X -- )`, so the evaluator can treat it as
printing and consuming an arbitrary symbolic value.

The repository now ships three file-backed demo profiles: `real`,
`legacy`, and `forth2012`. The primary evaluator interface is explicit
filename parameters, so the evaluator entrypoints themselves no longer
hardcode any of those files. The convenience wrappers are now split by
runtime and platform: `run-evaluator-gforth.sh`,
`run-evaluator-java.sh`, `run-evaluator-gforth.bat`, and
`run-evaluator-java.bat`. They all supply the bundled demo filenames,
defaulting to `forth2012` and accepting `real` or `legacy` as the first
argument.

The bundled vocabularies are no longer just fixed Java tables. They are
loaded from spec files, and those files can now describe parser words,
defining words, literal classes, and declarative control roles alongside
ordinary stack effects.

This is enough to demonstrate:

- stack shuffling,
- generic vs same-type operators,
- address/character distinctions,
- and type refinement through composition.

## `ProgText`

`ProgText` is the internal representation of the program being analyzed.

It is still backed by a `LinkedList<String>` of top-level words, but it now
does substantially more than hold raw tokens.

Two details matter:

1. `ProgText(String[] text, TypeSystem ts, SpecSet ss)` tokenizes CLI input, interprets parser words, tracks interpretation vs compilation state, parses colon definitions, and registers any defined words before evaluating the remaining top-level program.
2. `ProgText(String fileName, TypeSystem ts, SpecSet ss)` does the same for a text file, including support for declared control structures, file-based diagnostics with line/column spans, and recovery after many source errors so later problems can still be reported.

So the class is still a compact front end rather than a full Forth parser, but it is now a real supported entry point for both command-line and file-based checking.

## `SpecList`

`SpecList` is the operational engine.

It represents the linear top-level sequence of `Spec` objects that remains after parsing any colon definitions in the source text, and performs the actual evaluation.

Its core jobs are:

- clone the word specs for a program,
- globally freshen wildcard indices to avoid collisions,
- compose the sequence with recursive symbolic unification,
- propagate substitutions through the full list,
- and normalize the printed wildcard indices afterward.

This class is the best candidate to view as "the evaluator proper."

## `Evaluator`

`Evaluator` is the demo-oriented entry point.

It creates a `TypeSystem`, loads a `SpecSet`, parses either a program file or
command-line program words into `ProgText`, evaluates the resulting `SpecList`,
and prints the chosen profile/files, the reconstructed program text, the
top-level word sequence, and an annotated stack-effect listing. When parsing
collects recoverable errors, it prints the rendered diagnostics and exits with
failure.

This is useful for experimentation, but it is still a compact CLI rather than
a polished toolchain integration point.

## End-to-End Evaluation Flow

For a direct invocation such as:

`java evaluator.Evaluator --types forth2012types.txt --specs forth2012specs.txt SWAP DUP @`

the flow is:

1. `TypeSystem` is loaded from the indicated type file.
2. `SpecSet` is loaded from the indicated spec file.
3. `ProgText` stores the token list `["SWAP", "DUP", "@"]`.
4. `SpecList` looks up each word in `SpecSet` and clones its `Spec`.
5. `evaluate(...)` freshens wildcard indices to make all local placeholders unique.
6. The list is reduced left-to-right by repeated `multiply(...)`.
7. Each boundary match compares top output of the left effect to top input of the right effect.
8. If types are compatible, a fresh merged symbol is created with the most exact type.
9. That fresh symbol is substituted across the current result, the remaining effect being composed, and the `SpecList` itself.
10. After the whole list is composed, `normalize(...)` renumbers wildcard identities for readability.
11. `annotate(...)` prints the per-word symbolic effects between an inferred input and output stack state.

That is the essence of the project.

## The Key Algorithms in More Detail

## 1. Freshening Wildcards

Different word specs reuse local indices like `X[1]`, `X[2]`, etc. Those indices only make sense locally.

Before evaluating a whole program, `SpecList.evaluate(...)` calls `incrementWild(...)` on each spec so that:

- existing positive indices are shifted upward,
- zero indices are turned into fresh unique indices,
- and collisions between different words are avoided.

This gives the analyzer a clean global namespace of symbolic items for the current run.

## 2. Recursive Multiplication

`SpecList.multiply(...)` implements symbolic composition recursively.

The algorithm:

- starts with accumulator effect `1`, represented as empty left and right vectors,
- compares the top of the current right side with the top of the next left side,
- merges them using subtype information,
- removes the matched boundary pair,
- then recurses on the shortened intermediate effects.

If a boundary pair is incompatible, the result is `null` and the evaluator reports a type conflict.

This is the operational heart of the analyzer.

## 3. Sequence-Wide Substitution

One subtle but important design choice is that when a new merged symbol is created, the substitution is applied not only locally but also across the whole `SpecList`.

This is what allows information to flow:

- forward,
- backward,
- and through stack-manipulation words.

That behavior is exactly why examples like `SWAP DUP @` become more informative than a naive local type checker would allow.

## 4. Normalization

During evaluation, wildcard indices become large and messy.

`SpecList.normalize(...)` performs a cleanup pass so that the final output uses smaller, human-readable indices.

Conceptually, normalization:

- makes all current indices safely large,
- records which symbolic items remain shared,
- assigns fresh compact indices to the symbols that still matter globally,
- and rewrites both the final result and the per-word list.

This is mostly for readability, but it is essential if the tool is meant to support human understanding.

## 5. `glb`, `cprefix`, and `unify`

`glb(...)` is not simple pairwise intersection.

The implementation first compares the left/right lengths of the two effects. If one effect is longer, it uses `cprefix(...)` to reconcile the extra prefix while preserving type compatibility. Then `unify(...)` merges corresponding symbols across both sides.

This is a concrete implementation of the paper idea that branch merging should preserve the strongest guaranteed common structure.

## 6. `idemp` and `piStar`

`idemp(...)` succeeds only when the effect has equal numbers of inputs and outputs. It then tries to unify corresponding positions so the effect becomes stack-preserving.

`piStar(...)` duplicates the effect, evaluates the sequence `e e`, and then computes a `glb` with `e`.

This is a neat example of the code carrying paper mathematics almost directly.

## What the Current Prototype Can Demonstrate

The repo is small, but it can already demonstrate several meaningful properties.

## Stronger Than Plain Stack Comments

It can distinguish:

- generic values,
- numbers,
- flags,
- character addresses,
- address addresses,

and can refine types based on use.

That is more powerful than informal Forth stack comments, which often collapse everything to `x`.

## Identity Through Stack Shuffling

Because indexed wildcards are preserved, the analyzer does not lose track of which stack item is which after:

- `SWAP`
- `DUP`
- `OVER`
- `ROT`

This is one of the most valuable aspects of the design.

## Conservative Error Detection

The analyzer can reject suspicious programs when incompatible symbolic types meet.

For example:

- `C@ !` produces a conflict because a `char` is not an `a-addr`.
- `0= PLUS 0=` also conflicts: `0=` yields a `flag`, but the final `0=` expects an `n`.

That is exactly the kind of strong-stack-discipline failure the papers are targeting.

## Precision Differences Between Standard and Demo Operators

The distinction between standard `+` and non-standard `PLUS` is still
instructive:

- `+` is now modeled more like Forth, as numeric `( n n -- n )`,
- `PLUS` remains as a stricter same-type helper `( X[1] X[1] -- X[1] )`
  for stack-effect experiments.

So the shipped vocabulary is closer to ordinary Forth, while the repo still
preserves one extra demo word that is useful for illustrating wildcard-based
type equality.

## Observed Runtime Behavior

Running the current demo confirms the conceptual design.

### Successful example: `SWAP DUP @`

The evaluator produces an annotated result equivalent to:

`( a-addr[2] X[1] -- X[1] a-addr[2] X )`

This is a good demonstration of:

- identity preservation,
- backward propagation of address precision,
- and stack-shuffle awareness.

### Conflict example: `0= + 0=`

This now fails in the more Forth-like bundled vocabulary, because `+` expects
numeric inputs and `0=` produces a `flag`.

### Conflict example: `0= PLUS 0=`

This also fails, but for a different reason: `PLUS` is the retained demo helper
that requires both inputs to be the same symbolic type, and the later `0=`
still requires an `n`.

That is useful: the prototype is catching a semantic misuse, not just a stack-depth mismatch.

### Conflict example: `C@ !`

This also fails, because the value produced by `C@` is a `char`, while `!` expects an `a-addr` as its second argument pattern.

## Important Gaps and Limitations

This repository is very informative, but it is visibly unfinished.

## 1. File-Based APIs Are Real, But Still Minimal

Both of these constructors take filenames:

- `new TypeSystem(typesFile)`
- `new SpecSet(specsFile, typeSystem)`

These now read real example files from disk, and `ProgText(String fileName, TypeSystem ts, SpecSet ss)` likewise loads a program text file. The repo also keeps three bundled demo environments side by side, with an optional helper script choosing those demo filenames outside Java.

That is a meaningful step forward, but the loaders are still deliberately simple:

- the formats are example-oriented rather than full Forth source formats,
- the bundled profiles still ship with a deliberately small vocabulary,
- and the surrounding evaluator remains prototype-level.

## 2. Control Parsing Exists, But Effects Are Still Single-Valued

The papers discuss:

- branches,
- loops,
- closures,
- multiple stack-effects,
- and a larger Forth grammar.

The current code does parse branches, loops, parser words, and dictionary
growth through source text, but each completed construct is still reduced to
one symbolic effect rather than a richer set of alternatives.

That makes the implementation a **core engine with a practical front end**,
not a full Forth analyzer.

## 3. `SpecSet` Is Less Static, But Still Limited

`SpecSet` can now load, define, and save single stack-effect specs, so it is no longer just a fixed constructor-time table.

But there is still no support for:

- first-class sets of alternative stack effects per word,
- a broader compile-time data stack model,
- many Forth-standard compile-time semantics beyond the current metadata,
- or separate full runtime/compile-time effect universes.

## 4. Error Handling Is Better, But Still Prototype-Level

The current CLI now reports structured diagnostics with source spans and can
recover from many file-parsing errors, which is a substantial step beyond the
original prototype behavior.

The semantic core is still stronger than the demo shell around it, but the
user-facing behavior is much safer and more informative than the original
prototype.

## 5. Output Is Verbose and Not Tool-Oriented Yet

The demo currently prints:

- the chosen profile and file set,
- the source text,
- the parsed top-level program,
- and the annotated stack-effect listing.

That is appropriate for a research prototype but not yet for a normal CLI or editor backend.

## 6. Scope Is Intentionally Small

The type lattice is tiny, the vocabularies are intentionally small, and the
parser still covers only a focused subset of Forth source.

That is not a flaw by itself; it is consistent with the repo being a compact proof of concept. But it means the project should be judged as an analysis kernel, not as a production Forth validator.

## Overall Assessment

This project is a **tight, faithful prototype of a typed symbolic stack-effect calculus**.

Its main strengths are:

- a clear research lineage,
- a direct code-to-theory mapping,
- a smart treatment of symbolic identity through wildcard indices,
- subtype-aware composition,
- and enough implementation to demonstrate real static-analysis behavior on small Forth fragments.

Its main weakness is not conceptual but developmental:

- the surrounding infrastructure never caught up with the theory.

So the most accurate summary is:

> `evaluator` is the semantic core of a much bigger static-analysis idea for Forth, captured in a small Java package that now includes a practical source-level front end, declarative control structures, and file-backed demo profiles, but still stops well before full multiple-effect control-flow analysis or a production-quality tool.

## If Someone Continues This Project

The most natural next steps would be:

1. extend the current source parser toward a broader slice of real Forth syntax,
2. lift the evaluator from single merged effects to branch-aware program constructs,
3. model more compile-time semantics explicitly instead of approximating them through metadata,
4. keep hardening diagnostics and recovery around malformed source,
5. add a maintained automated test harness around the bundled examples and diagnostics,
6. and turn the demo into a stable CLI or editor-facing library.

That would extend the current code in the same direction the papers have already laid out.
