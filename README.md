# evaluator

Forth code symbolic executor and type checker.

## Overview

This repository contains two independent evaluator implementations that consume
the same input files:

- `gforth-evaluator.fs` is the single-file native `gforth` implementation.
- `evaluator.Evaluator` is the Java implementation.

Both read the same `--types`, `--specs`, and `--prog` text formats, and both
can be driven through the bundled launcher scripts.

## Quick Start

### Java directly

Compile from the parent directory of the `evaluator` package:

```sh
javac evaluator/*.java
```

Run with explicit input files:

```sh
java evaluator.Evaluator --types mytypes.txt --specs myspecs.txt --prog myprog.txt
```

Or pass the program words directly on the command line:

```sh
java evaluator.Evaluator --types mytypes.txt --specs myspecs.txt DUP SWAP +
```

The raw evaluator interface expects `--types` and `--specs`. If both a
`--prog` file and command-line program words are given, the command-line words
take precedence.

### Bundled launchers

The repository also ships convenience wrappers for the bundled demo profiles:

| Runtime | Linux | Windows |
| --- | --- | --- |
| `gforth` | `./run-evaluator-gforth.sh [profile]` | `run-evaluator-gforth.bat [profile]` |
| Java | `./run-evaluator-java.sh [profile]` | `run-evaluator-java.bat [profile]` |

The `gforth` launchers run `gforth-evaluator.fs` directly. The Java launchers
run `evaluator.Evaluator`. Both use the same bundled profile defaults.

Each launcher also accepts one existing bare filename as shorthand for
`--prog that-file`.

## Bundled Profiles

Without an explicit profile or user-supplied `--types` / `--specs`, the
launcher defaults to the `forth2012` profile.

| Profile | Types | Specs | Program |
| --- | --- | --- | --- |
| default / `forth2012` | `forth2012types.txt` | `forth2012specs.txt` | `forth2012prog.txt` |
| `real` | `ex1types.txt` | `ex1specs.txt` | `ex1prog.txt` |
| `legacy` | `legacytypes.txt` | `legacyspecs.txt` | `legacyprog.txt` |

## Bundled Vocabulary

The shipped demo specs cover a small Forth-like core, including:

- stack shuffles
- numeric words such as `+`, `-`, `*`, `/`, `MOD`, `1+`, `1-`, `2*`, `2/`,
  `NEGATE`, `ABS`, `0=`, `0<`, `0>`, `=`, `<`, and `>`
- memory words such as `@`, `C@`, `!`, `C!`, `HERE`, and `ALLOT`
- I/O words such as `.`, `KEY`, `EMIT`, `CR`, `SPACE`, `SPACES`, and `TYPE`

`.` is still modeled as a generic sink with stack effect `( X -- )`, so it can
print any one symbolic value. The non-standard `PLUS` word is retained only as
a same-type demo helper for stack-effect experiments.

## Spec Files

### Word metadata

Spec files may describe parser words explicitly with metadata such as
`parse until`, `parse word`, `parse definition`, `define`, `control`, and
`state`.

Examples:

```text
"(" parse until ")" ( -- )
".(" parse until ")" state interpret ( -- )
CONSTANT parse word define ( X -- )
: parse word define colon ( -- )
; control end ( -- )
IF control if ( flag -- )
DO control do ( n[2] n[1] -- )
```

Parser words, defining words, and control words are implicitly immediate, so
the bundled specs usually omit a separate `immediate` marker.

Program-file comment handling is profile-driven through these parser-word
entries. For example, if a specs file declares `"\\" parse until eol ( -- )`,
both the Java and `gforth` implementations treat backslash comments the same
way for that profile.

The evaluator follows an explicit outer-interpreter model with interpretation
state and compilation state:

- normal words execute in interpretation state or are compiled in compilation
  state
- defining words default to interpretation state
- control words default to compilation state
- bare `define` infers simple constant-like `( x -- )` and variable-like
  `( -- y )` definers from the declared stack effect
- colon definitions still use `define colon`

For compatibility, the evaluator still accepts older forms such as:

- delimiter shorthand like `"(" ")" ( -- )`
- the older explicit `scan` form
- `: parse definition ";" define colon`
- `context outer` and `context definition`
- an explicit `immediate` marker when it adds information

### Control-structure declarations

Control structures may also be declared with a top-level `syntax:` block and an
indented `effect:` block.

Example shape:

```text
syntax:
  IF <then branch>
  [ELSE <else branch>]
  FI
effect:
  either <then branch> <else branch>
```

The current control-effect algebra supports:

- line-by-line sequencing
- `either` for branch merge
- `repeat` for idempotent repetition
- bare control words such as `IF` or `DO`
- segment names taken from `<...>` metasymbols in `syntax`

For example, `repeat <loop body> UNTIL` means the repeated effect of the loop
body followed by the stack effect of `UNTIL` on each pass. This makes flag
consumption visible directly in declarations such as `IF ... ( flag -- )` or
`UNTIL ... ( flag -- )`.

`compilation:` and `run-time:` blocks are still accepted as documentation, but
they are not used for checking.

Compatibility is preserved for older structure forms, including:

- `structure ... endstructure`
- algebraic effect forms such as `sequence(...)`, `glb(...)`, `star(...)`,
  `word(...)`, and the older `meaning:` spelling
- the older `open` / `mid` / `close` structure form

A `syntax` line may also capture more than two parts, for example a fixed
switch-like form such as:

```text
SWITCH <selector> OF <first branch> OF <second branch> [DEFAULT <default branch>] ENDSWITCH
```

If no structure block is given, the legacy `IF` / `BEGIN` / `DO` families are
still loaded implicitly for compatibility. The bundled profiles also treat
`THEN` and `FI` as synonyms.

The standard-like profile additionally includes practical source words such as
backslash line comments, `CHAR`, `[CHAR]`, tick words `'` and `[']`,
`ABORT"`, `C"`, and defining words such as `CREATE`.

### Literal classes

Spec files may define literal classes with no inputs, for example:

```text
literal integer ( -- n )
literal double ( -- d )
```

Decimal integer tokens such as `0`, `17`, `-1`, and `+42` are recognized
directly in program text and use the stack effect declared for that literal
class. If a profile defines `literal double ( -- d )`, then decimal tokens with
a trailing period such as `1234.` or `-7.` are recognized as double-cell
integer literals.

## Program Language

### Definitions and lookup

Program text supports linear colon definitions through the outer interpreter:

```text
: NAME ... ;
```

`:` starts compilation and `;` finishes it. The bundled profiles also declare
`CONSTANT` and `VARIABLE` as defining words in the outer interpreter, so they
can be used at top level to introduce new words before later program text.

Word lookup is case-insensitive throughout the evaluator. Source text is left
as written, but names are treated internally as if all letters were uppercase.

### Conditionals

Inside colon definitions, the evaluator supports:

- `IF ... THEN`
- `IF ... FI`
- `IF ... ELSE ... THEN`
- `IF ... ELSE ... FI`

`IF` consumes a `flag`, and the branches must have compatible stack effects.

### Loops

Loop skeletons supported inside colon definitions include:

- `BEGIN ... WHILE ... REPEAT`
- `BEGIN ... UNTIL`
- `BEGIN ... AGAIN`

`WHILE` and `UNTIL` consume a `flag`, and the loop parts must satisfy the
existing idempotence approximation used by the evaluator.

Counted loops are supported as `DO ... LOOP`. The evaluator uses a
conservative approximation with stack effect `( n[2] n[1] -- )`, interpreted in
Forth order as `limit start`.

The model is:

- the loop index starts from `start`
- the implicit step is `1`
- execution stops before `limit`
- the last executed index is `limit-1`

For example, `7 0 DO I . LOOP` prints `0 1 2 3 4 5 6`. Inside a counted loop,
`I` is available as the innermost loop index with stack effect `( -- n )`. The
loop body must still be idempotent under the current approximation.

## Diagnostics

When program text is loaded from a file, diagnostics include:

- failing line and column
- the relevant source line
- a caret marker

This applies to semantic clashes such as linear type conflicts,
non-comparable `IF ... ELSE ... THEN` branches, and non-idempotent loop
bodies.

The checker also continues after a recoverable program error. It skips an
invalid top-level word or abandons an invalid definition, then keeps scanning
the rest of the file and reports later errors as separate diagnostics.

Each run also writes a log file beside the program input as `PROGRAM.log`, or
to `command-line.log` for command-line programs. The log records created
definitions in specs format and any reported errors.

## Type Profiles

The type system is profile-dependent.

- `real` is a more Forth-like convenience profile: flags are numbers,
  characters are numbers, and the top stack-cell type is also aliased as
  `cell`.
- `legacy` preserves the older stricter separation where `flag` is not a
  subtype of `n`.
- `forth2012` follows the Forth-2012 subtype lattice more closely: `flag` is
  separate from numeric types, addresses sit under `u`, `char` sits under `+n`,
  `.` is typed as `( n -- )`, and string-producing scanner words use the
  standard stack form `c-addr u`.

## Test Data

The examples under `testdata/` are intended to be exercised through the
launcher scripts.

Run a plain Forth example through the `gforth` implementation:

```sh
./run-evaluator-gforth.sh --prog testdata/positive/forth2012/string_scanner.fs
```

Run the same program through the Java implementation:

```sh
./run-evaluator-java.sh --prog testdata/positive/forth2012/string_scanner.fs
```

For custom profiles, pass explicit `--types`, `--specs`, and `--prog`
arguments. Test runs also create adjacent `.log` files for created definitions
and diagnostics.
