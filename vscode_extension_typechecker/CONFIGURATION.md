# Configuration profiles

Forth Editor Assistant reads dialect information from plain-text **types** and
**specs** files. A workspace file named `.forth-evaluator.json` joins a pair of
files into a named profile and optionally assigns profiles by source-file glob.
Nothing in these files is executed.

For a new dialect, copy the closest pair from the distribution's `profiles/`
directory into your workspace and change the copy. Keeping the two files small at
first makes errors easier to locate. Editing reference copies does not change the
bundled profiles in the installed extension.

## Project file: `.forth-evaluator.json`

Open a workspace folder, then run **Forth: Configure Custom Profile**. If the file
does not exist at the chosen workspace root, the command asks you to select an
existing types file and specs file and writes their paths to a new configuration.
It does not generate profile files. If a configuration already exists, the command
opens it without changing it.

A minimal configuration is:

```json
{
  "defaultProfile": "myforth",
  "profiles": {
    "myforth": {
      "types": "./config/myforth-types.txt",
      "specs": "./config/myforth-specs.txt"
    }
  }
}
```

Several profiles can be selected by glob:

```json
{
  "defaultProfile": "standard",
  "profiles": {
    "standard": {
      "types": "./profiles/standard-types.txt",
      "specs": "./profiles/standard-specs.txt"
    },
    "device": {
      "types": "./profiles/device-types.txt",
      "specs": "./profiles/device-specs.txt"
    }
  },
  "files": [
    { "glob": "firmware/**/*.fth", "profile": "device" }
  ]
}
```

Rules:

- `defaultProfile` must name an entry in `profiles`.
- Every profile requires string-valued `types` and `specs` properties.
- `files` is optional. The first matching glob wins. Globs match source paths
  relative to the configuration directory; use `/` as the separator.
- Paths are resolved relative to `.forth-evaluator.json`. Absolute local paths and
  `file:` URIs are also accepted; other URI schemes are not.
- Each profile file must be no larger than 5 MB.
- Unknown properties are rejected by the bundled JSON schema.
- Selection precedence is the resource setting `forthAssistant.profile`, the first
  matching `files` rule, the nearest project default, then bundled `forth2012`.

VS Code validates this file automatically when the extension is installed. The
extension searches from the source file's directory up to its workspace root and
uses only the nearest configuration; parent configurations are not merged. Open
files outside a workspace do not use project configurations. A project profile
with the same name as a bundled profile takes precedence.

Choose **Automatic** in **Forth: Select Profile** (the empty-string setting) to
allow project globs/defaults to take effect. Selecting a specific profile overrides
those rules for the setting's scope, not only for the active editor. In a remote
workspace, configuration and profile paths refer to the remote filesystem.

## Common text syntax

Both profile files use one directive or declaration per line.

- Whitespace separates tokens.
- An unquoted backslash (`\`) starts a comment that continues to end of line.
- Double quotes preserve spaces and delimiters. Within a quoted token, `\n`, `\r`,
  and `\t` represent control characters; a backslash also escapes the next
  character. For example, `"\""` denotes a literal double quote.
- Directive and word lookup is case-insensitive. Type names and aliases should be
  written with the same case used in the types file.

## Types file

A types file declares canonical types, aliases, subtype relations, and named
scanner delimiters.

```text
\ A small cell type hierarchy
type x X cell
type n number
type u unsigned
type flag boolean
type char c
type addr
type c-addr
type a-addr

rel u < n
rel char < u
rel flag < x
rel n < x
rel addr < u
rel c-addr < addr
rel a-addr < c-addr

scanner EOL "\n"
```

### `type`

```text
type canonical-name [alias ...]
```

The first name is canonical; every following name is an equivalent spelling.
Names must be unique across all declarations. At least one type is required.
Aliases are useful when imported specs use different stack-comment conventions.

### `rel`

```text
rel subtype < supertype
```

Both names must have been declared by a `type` directive (declaration order does
not matter). Relations are transitive. Add only genuine substitutability
relations: an overly broad relation can hide a real stack error.

Types that need to interact must be equal or related. For example, if `char < u`
and `u < n`, a character can satisfy an input expecting `n`. Composition can also
refine a broader type to a related narrower type; it is not a one-way assignment
check. In the bundled `forth2012` hierarchy, `a-addr < n` transitively, so `1 @`
is accepted even though the checker cannot validate the address. Unrelated address
and execution-token types remain incompatible, even if both are subtypes of `x`.

### `scanner`

```text
scanner name "delimiter"
```

A scanner gives a reusable name to a nonempty delimiter used by parser words.
`scanner EOL "\n"` is the usual line-comment delimiter. Scanner names are
case-insensitive and must be unique.

## Specs file

A specs file contains word declarations, literal declarations, and structured
control declarations. Types in stack effects must exist in the paired types file.

### Ordinary words and stack effects

```text
word-name [metadata ...] ( input ... -- output ... )
```

Examples:

```text
DROP ( x -- )
DUP ( x[1] -- x[1] x[1] )
SWAP ( x[2] x[1] -- x[1] x[2] )
+ ( n n -- n )
@ ( a-addr -- x )
! ( x a-addr -- )
```

Stack order is left to right, with the top of stack on the right. Empty input or
output lists are valid. Every stack symbol is a declared type or alias.

An optional positive identity such as `[1]` says that occurrences represent the
same symbolic value. Use identities only when a word preserves, duplicates, or
reorders a value. The numbers are local labels; their numeric values have no
meaning. Unlabelled repeated types are independent values.

Word names are case-insensitive and must be unique. Quote a name when needed to
protect characters such as a backslash from the profile tokenizer. Forth source
word names remain whitespace-delimited.

### Parser words

Parser metadata tells the analyzer to consume source text that is data rather than
Forth words:

```text
"(" parse until ")" ( -- )
"\\" parse until EOL ( -- )
CHAR parse word state interpret ( -- char )
S" parse until "\"" ( -- c-addr u )
```

- `parse word` consumes the next whitespace-delimited source token.
- `parse until delimiter` consumes through a quoted delimiter or a named scanner.
- `parse definition delimiter` is intended for a colon-like defining word whose
  body ends at that delimiter rather than at a word with `control end`.

A missing parser payload is reported as an incomplete construct. Prefer an explicit
`parse ...` clause over the older shorthand scanner syntax.

### Defining words

```text
: parse word define colon ( -- )
; control end ( -- )
CONSTANT parse word define constant ( x -- )
VARIABLE parse word define variable ( -- a-addr )
```

Supported defining modes are:

- `define colon`: starts a named definition; its declared effect must be empty.
- `define constant`: consumes exactly one symbolic stack item and defines a word
  that returns its inferred type. An item may represent multiple runtime cells,
  as with the bundled double-number types.
- `define variable`: has no input and exactly one output; the new word returns that
  output type.

A bare `define` infers `constant` for a one-input/no-output effect and otherwise
tries `variable`, whose shape must still be no-input/one-output. Explicit modes are
clearer and are recommended. Defining words operate
at top level; nested definitions are not supported.

### State, immediate words, and special semantics

```text
EXIT state compile ( -- )
.( parse until ")" state interpret ( -- )
RECURSE state compile semantic recurse ( -- )
"{" parse until "}" state compile semantic locals x "|" "--" ( -- )
```

- `state compile` marks a definition-only word; using it at top level is an error.
  `state interpret` marks interpretation-state behavior. `state definition` and
  `context definition` are aliases for compile, while `state outer` and
  `context outer` are aliases for interpret. In 0.6.1, interpretation-only words
  are excluded from definition completions but are not diagnosed when entered
  inside a definition.
- `immediate` marks an immediate word. It also keeps the word out of ordinary
  completion candidates.
- `semantic recurse` gives a compile-state word the current definition's
  provisional effect.
- `semantic locals TYPE DIVIDER OUTPUT-MARKER` parses a locals declaration. Names
  before the divider are inputs of `TYPE`; names after it are local outputs; parsing
  stops at the output marker. The parser word must also use `parse until`.

These semantic annotations are extension profile syntax and may not be understood
by other evaluator implementations.

### Literals

```text
literal integer ( -- n )
literal double ( -- d )
```

Literal effects must have no inputs. `integer` matches signed decimal tokens such
as `17`, `-1`, and `+42`. `double` matches signed decimal tokens with a trailing
period, such as `1234.`. Declare only literal kinds needed by the dialect.

### Control words and structures

First assign a role to every control word:

```text
IF control if ( flag -- )
ELSE control else ( -- )
THEN control then ( -- )
BEGIN control begin ( -- )
WHILE control while ( flag -- )
REPEAT control repeat ( -- )
```

A `syntax:` block then describes the grammar and how segment effects compose:

```text
syntax: IF <then branch> [ELSE <else branch>] THEN
  effect:
    IF
    either <then branch> <else branch>
```

The names in the syntax header are **control roles**, not necessarily source
spellings. This allows several source words to share a role. Angle-bracketed names
identify source segments. A boundary and its following segment can be optional by
placing both in brackets.

The indented `effect:` body supports:

- normal line/token order for sequential composition;
- `either A B [...]` to merge compatible branch effects;
- `repeat A [...]` to require an idempotent repeated effect.

Loop examples:

```text
syntax: BEGIN <prefix> WHILE <body> REPEAT
  effect:
    repeat <prefix> WHILE
    repeat <body>

syntax: BEGIN <body> UNTIL
  effect:
    repeat <body> UNTIL
```

Every `syntax:` block must contain an opening role, an initial segment, and a
closing role. Segment names used in `effect:` must exist in the syntax header.
Indent all continuation and effect lines more than `syntax:`. Custom profiles must
declare their control structures; do not rely on spelling such as `IF` or `LOOP`
being recognized automatically.

## Authoring checklist

1. Copy a bundled profile close to the target dialect.
2. Reduce the type hierarchy to useful, defensible distinctions.
3. Add stack primitives and arithmetic before parser or control words.
4. Use identity labels for shuffles and preserved values; omit them for unrelated
   inputs and newly produced values.
5. Declare comments and string words so payload text is not analyzed as code.
6. Add explicit state and defining metadata.
7. Add each control family with both `control` roles and a `syntax:`/`effect:` block.
8. Open a small Forth file and inspect Problems after each change. Hover a word to
   confirm the loaded profile file and line.
9. Test successful compositions, deliberate type clashes, incomplete parser text,
   mismatched branches, and unfinished controls.

When an edited profile becomes invalid, the extension reports the error and keeps
the last valid profile for each open document that already had one. This fallback
is not persisted across a server restart. Formatting is disabled while a profile
error is active. Fix the first reported profile line before interpreting secondary
source diagnostics.
