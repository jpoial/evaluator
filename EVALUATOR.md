# Gforth Stack-Effect Evaluator

This repository contains `gforth-evaluator.fs`, a single-file Gforth port of a stack-effect evaluator for a Forth-like language. Instead of executing a program for its runtime behavior, it loads a type lattice and a declarative specification set, analyzes the source, and prints the inferred stack effect of each token and of the whole program.

The bundled `types`, `specs`, and `prog.fs` files form a small working example. `forth-2012.pdf` is included as reference material for the language family the evaluator is modeling.

## What the evaluator does

- loads a type hierarchy and subtype relations from a `types` file
- loads stack-effect specifications for words, literals, parser words, defining words, and control structures from a `specs` file
- parses a program from `--prog FILE` or from trailing command-line words
- infers user-defined word effects for top-level `: ... ;`, `CONSTANT`, and `VARIABLE`
- checks composition and reports source-aware diagnostics when effects do not line up
- writes a log file containing inferred definitions and diagnostics

## Repository layout

- `gforth-evaluator.fs` - main analyzer and CLI entrypoint
- `forth-2012.pdf` - reference material

## Requirements

- Gforth available on your `PATH` as `gforth`

There is no build step. The evaluator is a single Gforth source file.

## Usage

```sh
gforth gforth-evaluator.fs --types TYPES --specs SPECS [--prog PROGRAM] [word ...]
```

You must provide `--types` and `--specs`, plus either:

- `--prog PROGRAM` to analyze a file
- trailing words to analyze a one-line program from the shell

Examples:

```sh
gforth gforth-evaluator.fs --types types --specs specs --prog prog.fs
gforth gforth-evaluator.fs --types types --specs specs 1 2 + DUP
```

When you pass words directly on the shell command line, quote any tokens that are special to your shell.

## Example output

Running the bundled sample expression:

```sh
gforth gforth-evaluator.fs --types types --specs specs 1 2 + DUP
```

produces an annotation of the program's effect:

```text
Types file: types
Specs file: specs
Program source: command line
Program text:
1 2 + DUP
Program: 1 2 + DUP
>
    1   ( --  n[2] )
    2   ( --  n[2] )
    +   ( n[2] n[2] --  n[1] )
    DUP ( n[1] --  n[1] n[1] )
< n[1] n[1]
```

Running the bundled `prog.fs` sample defines words at top level, so the top-level effect is empty and the inferred definitions are written to `prog.fs.log`.

## Input file formats

### `types`

The `types` file defines a small type lattice. Each non-empty line starts with one of these directives:

- `type NAME [ALIAS ...]`
- `rel SUB < SUPER`
- `scanner NAME "delimiter"`

Example:

```text
type a-addr
type c-addr
type addr
type x
type n
type flag
type char
rel a-addr < c-addr
rel c-addr < addr
rel addr < x
scanner EOL "\n"
```

`type` introduces a type name and optional aliases. `rel` adds a subtype relation. `scanner` defines named delimiters that can be referenced from the spec file.

### `specs`

The `specs` file defines ordinary words, literals, and control structures.

Ordinary word lines look like this:

```text
OVER ( x[2] x[1] -- x[2] x[1] x[2] )
CONSTANT parse word define ( x -- )
IF control if ( flag -- )
```

Supported clauses before the stack effect include:

- `parse word`
- `parse until DELIM`
- `define`
- `define colon`
- `define constant`
- `define variable`
- `control ROLE`
- `immediate`
- `state interpret`
- `state compile`
- `scan DELIM`

Literal lines use the form:

```text
literal integer ( -- n )
```

Control structures can also be described declaratively with `syntax:` and `effect:` blocks. The bundled `specs` file uses this to describe structures such as `IF ... ELSE ... FI`, `BEGIN ... WHILE ... REPEAT`, and `DO ... LOOP`.

### Stack-effect notation

Stack effects use the usual Forth-style `( in -- out )` notation with typed symbols. Indexed symbols such as `x[1]` and `x[2]` refer to related abstract values across the effect, while the subtype lattice from `types` is used to decide whether compositions are compatible.

## Output and logs

On success the evaluator prints:

- the input files or program source
- the normalized program text
- one stack-effect annotation per program token
- the final output stack after the last token

It also writes a log file:

- `<program>.log` when `--prog FILE` is used
- `command-line.log` when the source comes from trailing command-line words

The log contains inferred user-defined word definitions and diagnostics.

## Current scope and limitations

This project is a static evaluator, not a full Forth runtime. A few current boundaries are worth knowing:

- top-level defining words are limited to the forms described by the active spec set
- nested defining words inside definitions are explicitly rejected
- interpretation-state availability is enforced from the specs
- control-flow behavior is driven by the declarative structure model, plus built-in support for common `IF`, `BEGIN`/`WHILE`/`REPEAT`, `BEGIN`/`AGAIN`, `BEGIN`/`UNTIL`, and `DO`/`LOOP` families

If you want to model a different Forth dialect or vocabulary, the main extension points are the `types` and `specs` files rather than the evaluator core.

