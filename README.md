# Configurable static type checking for Forth

This directory contains a source-level stack-effect evaluator for Forth, several validation profiles, an example program, and the accompanying EuroForth material. The evaluator performs symbolic analysis; it **does not execute the input Forth program**.

A run has three inputs:

1. a **type file**, defining abstract types and their subtype relationships;
2. a **specification file**, defining primitive word effects and source-reading/control rules;
3. a Forth **program**, supplied in a file or on the command line.

The checker composes effects for linear phrases, merges the effects of control-flow alternatives, and checks a finite one-pass/two-pass approximation for loops. Colon definitions receive inferred effects that later definitions can use.

## Directory contents

### Evaluators

| Path | Description |
| --- | --- |
| `gforth-evaluator.fs` | Single-file native GForth implementation and command-line entry point. |
| `python3-evaluator.py` | Python 3 port with the same input formats and command-line behavior. |
| `vfx-evaluator.fth` | VFX Forth implementation of the evaluator. |
| `evaluator/` | Java implementation: command-line evaluator, scanner, source-location and diagnostic classes, and the type/specification model. |

### Validation profiles

A `*types.txt` file must be used with its corresponding `*specs.txt` file.

| Type file | Specification file | Profile |
| --- | --- | --- |
| `forth2012types.txt` | `forth2012specs.txt` | Compact, strict Forth-2012-like demonstration profile used by `examples.txt`. |
| `ans94types.txt` | `ans94specs.txt` | Maximal ANS Forth (ANSI X3.215-1994/DPANS94) profile, including optional word sets, subject to the modeling qualifications in the files. |
| `gforth1.0types.txt` | `gforth1.0specs.txt` | Gforth 1.0-development profile based on the upstream snapshot identified in the files. |
| `swiftforth4.1.10types.txt` | `swiftforth4.1.10specs.txt` | SwiftForth 4.1.10 profile. |
| `vfxforth5.43types.txt` | `vfxforth5.43specs.txt` | VFX Forth 5.43 profile. |

The implementation-specific profiles are static approximations of their documented vocabularies. They do not imply that every listed word is present in every installation, nor do they model every separate Forth stack or every variable/non-local effect exactly. Read the comments at the beginning of each profile for its source version and modeling choices.

### Examples and reference material

| Path | Description |
| --- | --- |
| `examples.txt` | Definitions and a top-level phrase exercising composition, conditionals, and loops. |
| `Euroforth_text.pdf` | Paper, *Configurable Static Type Checking for Forth*. |
| `Configurable_Static_Type_Checking_EuroForth.pdf` | EuroForth presentation slides. |
| `forth-2012.pdf` | Forth 2012 standard reference included with the project. |

## Using `gforth-evaluator.fs`

### Requirements

Install GForth and run the evaluator from a shell. The implementation is self-contained; the selected type, specification, and program files are loaded at run time.

The bundled Forth-2012 demonstration is the default, so the quickest complete run is:

```sh
gforth gforth-evaluator.fs
```

General form:

```sh
gforth gforth-evaluator.fs \
  [--types TYPES] \
  [--specs SPECS] \
  [--prog PROGRAM] [word ...]
```

Option names are case-insensitive. `-h` and `--help` print the usage text (the current command-line implementation reports it through the diagnostic path and returns a nonzero status).

### Analyze a source file

Run the included example with its demonstration profile:

```sh
gforth gforth-evaluator.fs \
  --types forth2012types.txt \
  --specs forth2012specs.txt \
  --prog examples.txt
```

Use another matched profile in the same way, for example:

```sh
gforth gforth-evaluator.fs \
  --types gforth1.0types.txt \
  --specs gforth1.0specs.txt \
  --prog my-program.fs
```

### Analyze words supplied on the command line

Any argument that is not an evaluator option is treated as a Forth source token. For example:

```sh
gforth gforth-evaluator.fs \
  --types forth2012types.txt \
  --specs forth2012specs.txt \
  3 4 +
```

This reports the inferred top-level effect `( -- n )`. Quote shell metacharacters, or quote an entire phrase, when necessary:

```sh
gforth gforth-evaluator.fs \
  --types forth2012types.txt \
  --specs forth2012specs.txt \
  ': SQUARED DUP * ; 3 SQUARED'
```

If one or more source words are present, command-line source is used instead of the file named by `--prog`.

### Defaults

With no options the evaluator uses the included Forth-2012 demonstration:

- types: `forth2012types.txt`
- specifications: `forth2012specs.txt`
- program: `examples.txt`

Each option independently overrides its corresponding default. For example, `--prog my-program.fs` checks that file with the default Forth-2012 profile, while `--types ans94types.txt --specs ans94specs.txt` selects the matched ANS94 profile.

### Output, diagnostics, and logs

A successful run prints:

- the selected input paths;
- the original program text and visible top-level words;
- the required input stack after `>`;
- one normalized effect beside each top-level word;
- the resulting stack after `<`.

For example, the core of the annotation for `3 4 +` is:

```text
> 
    3     (  --  n )
    4     (  --  n )
    +     ( n n  --  n )
< n
```

Inferred definitions and concise errors are written to a log. The log path is the configured program path plus `.log` (for example, `examples.txt.log`). This is also true for command-line source: because `examples.txt` remains the configured default program path, inline analysis writes `examples.txt.log` unless `--prog` changes the log prefix. For example, passing `--prog command-line` together with inline words produces `command-line.log`; the inline words are still the analyzed source. Source diagnostics include file, line, column, source line, and a marker where available. Exit status is `0` on success and `1` for handled evaluator errors.

The evaluator may recover after an invalid definition and report more than one source error. No final annotation is produced if diagnostics remain.

## Using the Java evaluator

### Compile

The Java sources declare package `evaluator`, so compile them from this directory (the parent of the `evaluator/` source directory). They use only the Java standard library:

```sh
mkdir -p build/classes
javac -d build/classes evaluator/*.java
```

This keeps generated `.class` files out of the source directory. A standard JDK is required; a JRE alone does not provide `javac`.

### Analyze a source file

Run the included example with:

```sh
java -cp build/classes evaluator.Evaluator \
  --types forth2012types.txt \
  --specs forth2012specs.txt \
  --prog examples.txt
```

After compilation, running without arguments analyzes the bundled demonstration:

```sh
java -cp build/classes evaluator.Evaluator
```

The Java entry point has the same Forth-2012 defaults as the GForth entry point.

General form:

```sh
java -cp build/classes evaluator.Evaluator \
  [--types TYPES] \
  [--specs SPECS] \
  [--prog PROGRAM] [word ...]
```

Java evaluator option names are case-sensitive and should be written exactly as shown. `-h` and `--help` print usage through the error path and return status `1`. The obsolete `--system` option is rejected; select profile files directly with `--types` and `--specs`.

### Analyze command-line words

```sh
java -cp build/classes evaluator.Evaluator \
  --types forth2012types.txt \
  --specs forth2012specs.txt \
  3 4 +
```

As with the GForth entry point, non-option arguments are joined into the analyzed Forth source, shell metacharacters must be quoted, and command-line words take precedence if `--prog` is also present.

The annotation and exit statuses have the same meaning as described above. Java names the log after the configured program path, including when inline words take precedence. Thus a default or inline run writes `examples.txt.log`; an explicit `--prog my-program.fs` writes `my-program.fs.log`.

To remove compiled output:

```sh
rm -rf build
```

## Writing a custom validation profile

A profile is a matched pair of text files. The type file answers **which abstract stack-item types exist and which are compatible?** The specification file answers **what does each source word consume or produce, and does that word parse or structure following source?** Keep those concerns separate:

| Put in the type file | Put in the specification file |
| --- | --- |
| Canonical type names and aliases | Stack effects for primitive/environment words |
| Subtype edges | Integer and double-literal effects |
| Reusable names for source delimiters | Parsing behavior for comments, strings, and name-consuming words |
|  | Defining-word, state, immediate, and control metadata |
|  | Declarative recipes for branches and loops |

Choose names that describe the contracts you want to check, not Java, Python, or Forth implementation classes. The checker only knows relationships explicitly present in the type file. It likewise only knows source words declared in the specification file, words inferred from recognized definitions, configured locals, and the two configured literal classes.

### Minimal complete profile

The following pair is enough to infer the effect of a small definition using integer literals, `DUP`, and `+`.

Create `mytypes.txt`:

```forth
\ A cell-like root and two distinct subsets.
type x cell
type n number
type flag boolean

rel n < x
rel flag < x

\ A reusable delimiter name for line-oriented parser words.
scanner EOL "\n"
```

Create `myspecs.txt`:

```forth
\ Lexical decimal integers produce one n.
literal integer ( -- n )

\ Ordinary runtime words.
DUP ( x[1] -- x[1] x[1] )
+   ( n n -- n )

\ Source and dictionary structure needed by the program.
:    parse word define colon ( -- )
;    control end ( -- )
"\\" parse until EOL ( -- )
```

Create `myprogram.fs`:

```forth
: TWICE DUP + ;
3 TWICE
```

Analyze it with any entry point, for example:

```sh
gforth gforth-evaluator.fs \
  --types mytypes.txt \
  --specs myspecs.txt \
  --prog myprogram.fs
```

The inferred effect of `TWICE` is `( n -- n )`. Although `DUP` begins with the generic type `x`, `+` requires both copies to be `n`; the shared identity `x[1]` lets that requirement refine the input.

This example also illustrates why a useful profile contains more than arithmetic effects. The evaluator must be told that `:` consumes a following name and starts a definition, that `;` ends it, and that `\` consumes the rest of a source line. Parser and control behavior are trusted parts of the profile.

### Recommended design process

1. **List abstract value categories.** Start with only distinctions that matter to checking, such as `x`, `n`, `u`, `flag`, `addr`, and `c-addr`. A physical two-cell or floating value may be represented by one abstract item if that matches the intended model.
2. **Add aliases sparingly.** Aliases are alternate spellings of one node, not subtypes. Do not declare the same spelling twice.
3. **Add subtype edges from specific to general.** For example, `rel c-addr < addr` says a character address is accepted where an address is required. Add enough edges to connect values that should interoperate, but do not connect categories merely because they occupy the same physical cell.
4. **Declare literals.** Without `literal integer`, a token such as `42` has no effect specification. Add `literal double` if trailing-dot decimal literals are needed.
5. **Specify ordinary words.** Translate each documented stack comment into declared type names. Use explicit identity indices for words that preserve, copy, or permute a particular cell.
6. **Specify source-reading words.** Comments, strings, tick words, defining words, and similar words consume source text in addition to having a stack effect. Model that with `parse word` or `parse until`.
7. **Specify compilation structure.** Declare definition terminators and control roles, then add `syntax:`/`effect:` blocks for target-specific branches and loops.
8. **Test small phrases first.** Check literals, one primitive, one parser word, one definition, and each control family separately before using the profile on a large source file.
9. **Document approximations.** If several run-time shapes are collapsed into one fixed effect, or another stack is omitted, state that prominently in comments at the beginning of the profile.

### Profile-writing checklist

Before using a new profile, verify that:

- every type appearing in a stack effect is declared by `type`, either canonically or as an alias;
- every subtype edge points from the more specific type on the left to the more general type on the right;
- unrelated types remain unrelated intentionally;
- copied/permuted cells use matching indices, while independent values do not accidentally share an index;
- every literal spelling used by the program has a corresponding supported literal class;
- every primitive source word has exactly one specification;
- words that consume names or delimited text have a parsing clause;
- source comments themselves are specified as parser words;
- all roles named in a control syntax have matching `control` declarations;
- `syntax:` continuation lines are indented;
- the type and specification files represent the same target vocabulary and modeling assumptions.

The following sections define both file formats precisely.

## Type-file metalanguage

Type files are line-oriented. Whitespace separates atoms, double quotes delimit string atoms, and `\` starts a comment outside a quoted atom. For portability among all evaluator implementations, write the directive keywords `type`, `rel`, and `scanner` in lowercase as shown. Scanner lookup and analyzed Forth word lookup are case-insensitive; use type and alias spellings exactly as declared.

The grammar is:

```text
type CANONICAL [ALIAS ...]
rel SUBTYPE < SUPERTYPE
scanner NAME "DELIMITER"
```

### `type`

Declares one abstract type. The first name is the canonical display name and every remaining name is an alias for the same node:

```text
type x X cell
type n signed number
type flag boolean
```

Names must be unique across canonical names and aliases. Types are abstract stack items, not necessarily physical cells: profiles may intentionally represent a double-cell number or a floating-stack item as one evaluator item.

### `rel`

Adds a strict subtype edge:

```text
rel +n < n
rel n < x
```

`rel a < b` means that `a` is more specific than `b`, so an `a` value may be used where `b` is required. The evaluator closes the relation transitively. Two stack types are compatible only when they are comparable in this hierarchy; it does not search for an unrelated third common subtype.

The profile is policy. For example, one profile may make `char` numeric while another may leave `char` and `n` incomparable.

### `scanner`

Names a delimiter for use by parser-word specifications:

```text
scanner EOL "\n"
```

The delimiter must be nonempty. Quoted strings recognize `\n`, `\r`, `\t`, `\"`, and `\\`. A specification may subsequently write `parse until EOL` instead of embedding the delimiter.

## Specification-file metalanguage

Specification files are also line-oriented and support `\` comments and quoted atoms. Word lookup in analyzed Forth source is case-insensitive. Quoting is useful when the specified word itself is punctuation or begins with a comment character:

```text
"(" parse until ")" ( -- )
"\\" parse until EOL ( -- )
```

There are three top-level forms:

```text
WORD [CLAUSE ...] ( INPUT ... -- OUTPUT ... )
literal KIND ( -- OUTPUT ... )
syntax: CONTROL-SYNTAX
  effect:
    CONTROL-EFFECT
```

### Stack effects

The conventional stack comment has the top of stack on the right:

```text
+    ( n n -- n )
DROP ( x -- )
```

Unmentioned deeper stack cells form an implicit frame and pass through unchanged.

A symbol can carry a positive identity index:

```text
DUP  ( x[1] -- x[1] x[1] )
SWAP ( x[2] x[1] -- x[1] x[2] )
OVER ( x[2] x[1] -- x[2] x[1] x[2] )
```

The index is a symbolic identity, **not** a stack position. Repeated `type[index]` occurrences denote the same abstract cell and preserve correlations through copying and permutation. Unindexed occurrences are independent fresh symbols. Indices are normalized in displayed results, so their final numbers need not match those in the profile.

When adjacent effects meet, their touching stack cells must have comparable types. Matching retains the more specific type, which allows a later operation to refine an earlier generic input. A mismatch between incomparable types is a type clash.

### Literal specifications

The evaluator currently recognizes two lexical literal classes:

```text
literal integer ( -- n )
literal double  ( -- d )
```

An integer is an optional `+` or `-` followed by decimal digits. A double has the same form with a trailing `.`. A literal specification may not consume input. These declarations classify source spelling only; the evaluator does not compute the literal's value.

### Word clauses

Clauses appear between the word name and its stack effect.

#### Parsing clauses

```text
WORD parse word                 ( ... -- ... )
WORD parse until DELIMITER      ( ... -- ... )
WORD parse definition DELIMITER ( ... -- ... )
```

- `parse word` consumes the next whitespace-delimited source word.
- `parse until` consumes source through the named or quoted delimiter.
- `parse definition` supplies a definition terminator for compatible custom defining-word handling.

`DELIMITER` is either a quoted string or a name introduced by `scanner`. Before `parse until`, leading source whitespace is skipped. The consumed parser text is not evaluated as Forth source, while the declared stack effect of the parser word still participates in analysis.

Two legacy spellings are accepted: `scan DELIMITER` is equivalent to `parse until DELIMITER`, and a bare delimiter/scanner atom before `(` also selects `parse until`.

Examples:

```text
CHAR parse word ( -- char )
S" parse until "\"" ( -- c-addr u )
"\\" parse until EOL ( -- )
```

Program comments are therefore profile-driven: the program scanner itself separates source words, while entries such as the last example tell it how much comment text to skip.

#### Defining clauses

```text
:        parse word define colon    ( -- )
CONSTANT parse word define constant ( x -- )
VARIABLE parse word define variable ( -- a-addr )
```

- `define colon` consumes a name, analyzes the definition body, and installs its inferred effect in the current dictionary. Its declared effect must be `( -- )`.
- `define constant` consumes one current top-level abstract value and installs a word that returns that refined type. Its defining shape must be `( x -- )` (where `x` may be another declared type).
- `define variable` installs the declared one-result effect. Its defining shape must be `( -- y )`.

A bare `define` infers `constant` from a one-input/no-output shape and `variable` from a no-input/one-output shape. Other shapes require an explicit supported mode.

A control word with role `end`, normally `;`, terminates colon definitions:

```text
; control end ( -- )
```

The checker supports forward seeding for recognizable definitions and special handling of configured `{ ... }`/`{: ... :}` local declarations. This is source analysis, not execution of arbitrary defining-word semantics.

#### Control clauses

```text
IF     control if    ( flag -- )
ELSE   control else  ( -- )
THEN   control then  ( -- )
I      control index ( -- n )
```

The role is an arbitrary case-insensitive name used by a `syntax:` declaration. Most control roles are compile-time markers. The special `index` role is allowed as a runtime effect only inside a counted loop. If a control-role effect is needed by an `effect:` recipe, the declared effect for that role is used; fallback effects exist for the built-in control families.

#### State and immediacy clauses

```text
WORD state interpret ( ... -- ... )
WORD state compile   ( ... -- ... )
WORD immediate       ( ... -- ... )
```

`context outer` is an alias for `state interpret`; `context definition` is an alias for `state compile`.

A compile-only word is rejected at top level. Defining and control words default to interpretation-only and compilation-only respectively when no explicit state is present. `immediate` marks a word for source-time handling. Parsing, defining, and most control clauses also imply immediate handling; `control index` is the exception.

### Declarative control structures

Control structure shape and meaning can be kept in the profile rather than hard-coded into each word. For example:

```text
IF control if ( flag -- )
ELSE control else ( -- )
THEN control then ( -- )

syntax: IF <then-branch> [ELSE <else-branch>] THEN
  effect:
    IF
    either <then-branch> <else-branch>
```

Continuation lines must be indented farther than the `syntax:` directive. The block ends at the next nonempty line whose first token is at the same or a smaller column.

The syntax grammar is:

```text
OPEN <segment> [BOUNDARY <segment>]... CLOSE
```

Square brackets in this grammar mark an optional boundary and its following segment. Angle-bracketed names identify captured source segments; punctuation and whitespace in a segment name are canonicalized for matching.

The indented `effect:` recipe supports:

- `A B ...` — sequential composition;
- `either A B [...]` — meet/merge of alternatives;
- `repeat A [B ...]` — the finite repetition operation;
- `<segment>` — the inferred effect of a captured segment;
- `ROLE` — the runtime stack effect declared for that control role.

Separate recipe lines are composed in order. Thus the example first applies the selector effect of `IF`, then merges the true and false branch effects. An omitted optional segment has the empty effect.

Legacy built-in recipes are installed for `IF ... [ELSE ...] FI`, `BEGIN ... WHILE ... REPEAT`, `BEGIN ... AGAIN`, `BEGIN ... UNTIL`, and `DO ... LOOP`; explicit `syntax:` blocks can describe target-specific closing and loop words.

## What the analysis guarantees—and what it does not

For the selected profile, successful analysis checks that:

- linear effect boundaries have enough modeled cells with comparable types;
- alternative branches admit one common stack contract;
- repeated effects pass the evaluator's finite stability test;
- recognized parser, state, defining-word, and control rules are structurally valid.

The evaluator tracks one abstract data stack and one fixed effect per word. It does not prove values, arithmetic properties, termination, or arbitrary run-time behavior. Separate return/floating stacks, variable-arity effects, unrestricted `EXECUTE`, non-local exceptions, native metaprogramming, and arbitrary defining words can only be approximated or omitted by these profiles. Loop checking uses the meet of one and two iterations; it is not an unbounded fixed-point proof.

Consequently, results are only as sound as the loaded type hierarchy, primitive effects, parser declarations, and control recipes. Use this checker alongside execution tests, target-system documentation, and code review. See `Euroforth_text.pdf` for the calculus, worked examples, and a fuller discussion of limitations.

## Python port

The Python implementation is useful where GForth is unavailable. With no arguments it uses `forth2012types.txt`, `forth2012specs.txt`, and `examples.txt`:

```sh
python3 python3-evaluator.py
```

It accepts the corresponding overrides:

```sh
python3 python3-evaluator.py \
  --types ans94types.txt \
  --specs ans94specs.txt \
  --prog my-program.fs
```

It uses the same profile metalanguage and produces the same style of annotation and log files.
