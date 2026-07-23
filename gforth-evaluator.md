# `gforth-evaluator.fs`

## Overview

`gforth-evaluator.fs` is a single-file GForth implementation of a static stack-effect evaluator for Forth source. It does **not execute the input program**. Instead, it:

1. loads a type hierarchy;
2. loads stack-effect specifications for Forth words and literals;
3. scans a Forth program or a sequence supplied on the command line;
4. infers stack effects for definitions and control structures;
5. composes the effects and reports type clashes; and
6. prints a per-word stack-effect annotation.

The file is a native port of an evaluator prototype and has no project-local source dependencies. Its final line calls `ev-main` and then `bye`, so it is intended to be run as a program rather than included as a library.

## Requirements

- GNU Forth (`gforth`)
- Input files for the type system and word specifications
- Either a program file or program words on the command line

The implementation uses GForth facilities such as locals (`{ ... }`), dynamic allocation, file I/O, `catch`/`throw`, and `recurse`.

## Running the evaluator

```bash
gforth gforth-evaluator.fs \
  --types TYPES_FILE \
  --specs SPECS_FILE \
  --prog PROGRAM_FILE
```

Program text may be supplied directly as remaining command-line arguments:

```bash
gforth gforth-evaluator.fs \
  --types types.txt \
  --specs specs.txt \
  1 2 + .
```

When command-line program words are present, they take precedence over `--prog` and are joined with spaces into one source string.

### Options

| Option | Meaning |
|---|---|
| `--types FILE` | Select the type-system file. |
| `--specs FILE` | Select the word-specification file. |
| `--prog FILE` | Select the Forth program file. |
| `--help`, `-h` | Print usage text. In the current implementation this follows the diagnostic path and exits with status 1. |

With no arguments, the evaluator uses:

- `ex1types.txt`
- `ex1specs.txt`
- `ex1prog.txt`

All three defaults must exist in the current directory.

## Input formats

Blank lines are accepted in the type and specification files. A backslash (`\`) begins a line comment while those files are scanned. Quoted atoms support `\n`, `\r`, `\t`, `\"`, and `\\` escapes.

### Type-system file

The type-system loader recognizes three directives.

#### `TYPE`

```text
TYPE canonical-name [alias ...]
```

The first name is the stored display name. Every name on the line, including the first, becomes an alias for the same type.

Example:

```text
TYPE x any
TYPE n number integer
TYPE flag boolean
```

Type names must be unique.

#### `REL`

```text
REL subtype < supertype
```

Example:

```text
REL n < x
REL flag < x
```

Both names must already have been declared. The evaluator normalizes the relation matrix by adding identity, inverse, equality, and transitive relations. These relations determine whether adjacent stack symbols can be unified while stack effects are composed.

#### `SCANNER`

```text
SCANNER name delimiter
```

A scanner gives a reusable name to a delimiter consumed by parsing words in the specification file.

Example:

```text
SCANNER COMMENT ")"
SCANNER STRING "\""
```

Scanner names are canonicalized to uppercase. Names and delimiters must be nonempty and scanner names must be unique.

### Word-specification file

A basic word entry has the form:

```text
word [metadata ...] ( input-types -- output-types )
```

Examples:

```text
DUP  ( x[1] -- x[1] x[1] )
DROP ( x -- )
SWAP ( x[1] x[2] -- x[2] x[1] )
+    ( n n -- n )
```

Word lookup is ASCII case-insensitive. Duplicate word entries are rejected.

#### Type symbols and wildcard indices

A symbol is either a type name or a type name followed by an explicit index:

```text
x
x[1]
n[2]
```

Repeated occurrences of the same type and explicit index refer to the same logical stack value. This is how effects such as `DUP` and `SWAP` express data flow. Unindexed symbols are assigned internal wildcard positions during composition. The evaluator renumbers positions into a compact form before displaying the final result.

A stack effect must contain `--`. Empty input and output sides are allowed.

#### Literal specifications

```text
LITERAL INTEGER ( -- n )
LITERAL DOUBLE  ( -- d )
```

Integer tokens may have an optional sign. A “double” literal is recognized by a trailing period, for example `123.`. Literal effects are required to have no inputs.

#### Metadata clauses

Metadata appears between the surface word and the opening `(`.

| Clause | Purpose |
|---|---|
| `parse word` | Consume the next program word as parser input. |
| `parse until DELIMITER` | Consume text through a quoted delimiter or named scanner. |
| `parse definition DELIMITER` | Mark a definition opener and its terminator. |
| `scan DELIMITER` | Short form for `parse until DELIMITER`. |
| `define colon` | Define a colon-style word; its declared effect must be `( -- )`. |
| `define constant` | Define a constant-style word; its effect must have shape `( x -- )`. |
| `define variable` | Define a variable-style word; its effect must have shape `( -- y )`. |
| `define` | Infer constant or variable mode from the effect shape. |
| `control ROLE` | Assign a canonical control-structure role. |
| `immediate` | Process the word while parsing rather than as an ordinary runtime effect. |
| `state interpret` / `state outer` | Permit the word only at top level. |
| `state compile` / `state definition` | Permit the word only inside definitions. |
| `context ...` | Synonym for `state ...`. |

A quoted delimiter or scanner name may also be placed directly before `(` as shorthand for an `until` parser.

Representative declarations might look like:

```text
( parse until COMMENT immediate ( -- )
: parse definition ";" define colon ( -- )
CONSTANT parse word define constant ( x -- )
VARIABLE parse word define variable ( -- addr )
IF control IF immediate state compile ( flag -- )
```

The exact words and types depend on the accompanying type-system and specification files.

#### Declarative control structures

A `SYNTAX` block describes a control form. Its child lines must be indented farther than the `SYNTAX` line.

Conceptually, syntax consists of:

```text
OPEN <first-segment> [ BOUNDARY <next-segment> ] CLOSE
```

Square brackets mark an optional boundary/segment pair. Segment names are written as metasymbols such as `<BODY>` and are canonicalized for matching.

An `EFFECT` section defines how segment effects and control-role effects combine:

- a sequence composes effects from left to right;
- `EITHER a b ...` computes the greatest lower bound of alternatives;
- `REPEAT a ...` applies the repetition/idempotence operation;
- `<SEGMENT>` references a parsed segment;
- any other atom references a control role.

The loader also installs compatibility definitions for these legacy control families:

- `IF ... [ELSE ...] FI`
- `BEGIN ... WHILE ... REPEAT`
- `BEGIN ... AGAIN`
- `BEGIN ... UNTIL`
- `DO ... LOOP`

Surface words participate in these forms through their `control ROLE` metadata.

### Program source

Program text is scanned as whitespace-separated Forth words. Parsing behavior such as comments, strings, and definition termination comes from the specification assigned to each parser word; it is not hard-coded into the ordinary program scanner.

The evaluator supports:

- integer and trailing-period double literals;
- top-level colon, constant, and variable defining words;
- inferred effects for colon definitions;
- recursive references through `RECURSE`;
- nested declarative control structures;
- loop index words with the `INDEX` role inside `DO`-like structures;
- locals declarations recognized from a parser word whose surface name is `{`;
- limited forward-definition seeding from documented locals-style declarations and defining-word shapes;
- recovery after many source diagnostics so more than one error can be reported.

Defining words inside colon definitions are rejected.

## Stack-effect evaluation

A specification contains a left (input) vector and a right (output) vector. To compose two specifications, the evaluator repeatedly compares the top output symbol of the first effect with the top input symbol of the second effect.

- Compatible symbols are unified according to the type relation matrix.
- Wildcard substitutions are propagated through the current effects and the complete effect list.
- Unmatched inputs are carried to the resulting input side.
- Unmatched outputs are carried to the resulting output side.
- Incompatible symbols cause a type clash.

The implementation also provides operations used by control-flow analysis:

- **normalization** — compact wildcard numbering;
- **unification** — reconcile comparable effects;
- **greatest lower bound** — join branch alternatives;
- **idempotence and repetition** — verify and summarize loop effects;
- **prefix closure** — unify corresponding input/output prefixes.

## Output

On success, the evaluator prints the selected input files, source text, normalized program words, and an annotation similar to:

```text
> n[1]
    DUP      ( n[1]  --  n[1] n[1] )
    +        ( n[1] n[1]  --  n[1] )
< n[1]
```

The `>` line is the required initial stack, each middle line is the normalized effect associated with a program word, and the `<` line is the resulting stack.

Top-level defining words are handled immediately. Hidden bookkeeping effects may therefore participate in evaluation without appearing as visible annotation rows.

## Diagnostics and log files

Diagnostics include a message, source position, source line, and caret marker when location data is available. The evaluator catches its internal error code, prints diagnostics, attempts recovery where possible, and returns a nonzero process status if any diagnostic was reported.

A log is created after successful command-line parsing:

- program-file mode: `PROGRAM_FILE.log`
- command-line source mode: `command-line.log`

The log contains diagnostics and inferred user-definition effects. It is truncated on each run.

Exit status is `0` on success and `1` for handled evaluator errors. Unexpected GForth exceptions are rethrown.

## Source organization

The source is divided into the following layers:

1. **Storage helpers** — checked allocation, resizing, and cell arithmetic.
2. **Persistent strings** — heap strings stored as a length cell followed by bytes.
3. **Pointer vectors** — dynamically growing vectors of single-cell items.
4. **Spans and diagnostics** — source locations, line markers, console output, and logging.
5. **Scanner** — normalized file loading, line splitting, atoms, quoted strings, comments, and delimited text.
6. **Type system** — types, aliases, scanners, relation matrix, and normalization.
7. **Stack effects** — symbols, wildcard positions, parsing, cloning, substitution, composition, and joins.
8. **Specification dictionaries** — word and literal lookup plus declarative control structures.
9. **Specification loader** — parsing metadata, literals, and `SYNTAX`/`EFFECT` blocks.
10. **Program evaluator** — token resolution, locals, definitions, control forms, recovery, and annotation.
11. **CLI** — argument parsing, input loading, logging, exit handling, and `bye`.

Most internal words use the `ev-` prefix. Record layouts are represented by cell-offset constants such as `ev-spec.left` and `/ev-spec` rather than GForth structures.

## Important implementation notes

- Persistent strings, records, and vectors are allocated dynamically and are not individually freed. The intended lifetime is one evaluator process.
- Several evaluation operations mutate cloned specifications while substituting and renumbering wildcard symbols. Callers use `ev-spec-clone` or `ev-spec-list-clone` when the original value must remain stable.
- Input text is normalized to LF line endings and has a final line terminator removed to match the behavior of the reference evaluator.
- ASCII word/scanner keys are canonicalized to uppercase; quoted text and delimiters preserve their contents.
- `ev-error#` (`-4095`) represents an active evaluator diagnostic, while `ev-reported-error#` (`-4094`) indicates that one or more recovered diagnostics have already been printed.

## Using the file as a library

The source currently ends with:

```forth
ev-main (bye)
```

To load its definitions into an interactive GForth session, make a library copy without that final line (or guard the entrypoint). Useful high-level internal entrypoints include:

```forth
ev-ts-load              ( file$ -- ts )
ev-ss-load              ( file$ ts -- ss )
ev-parse-program        ( name text ts ss -- prog )
ev-spec-list-evaluate   ( list ts -- spec|0 )
ev-run-native           ( -- )
ev-main                 ( -- code )
```

Arguments marked `file$`, `name`, or `text` in these words are evaluator persistent-string pointers, not ordinary GForth `( c-addr u )` string pairs. Use `ev-scopy` to create one from a GForth string pair.
