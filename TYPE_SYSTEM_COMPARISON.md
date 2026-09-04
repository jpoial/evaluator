# Comparison of the Main Standard-Oriented Type Systems

This document compares the three standard-oriented evaluator profiles:

- `forth2012types.txt`
- `ans94types.txt`
- `gforth1.0types.txt`

The notation `A < B` means that **A is a subtype of B**. A value of type `A`
can therefore be used where `B` is required.

## Summary

| Feature | Forth 2012 profile | ANS94 profile | Gforth 1.0 profile |
|---|---|---|---|
| Generic cell | `x` | `x` | `x`, with `w` as an alias |
| Signed/unsigned relationship | Flattened: `u < n` | Separate branches joined by `n\|u` | Separate branches joined by `n\|u` |
| Flags | Separate subtype of `x` | `true` and `false` below `flag < x` | Same, with `f` as an alias for `flag` |
| Addresses | Below `u`, then `n` | Below `u`, then `n\|u` | Below `u`, then `n\|u` |
| Double numbers | Flattened: `ud < d` | Separate branches joined by `d\|ud` | Separate branches joined by `d\|ud` |
| Execution tokens | `xt < x` | `xt < x` | `xt < x` |
| Name tokens | Not present | Not present | `nt < x` |
| Floating-point value | Not present | Separate root `r` | Separate root `r` |
| Compilation-system types | Not present | Present as separate roots | Present as separate roots |
| Main purpose | Practical strict demo | Faithful ANS94-style hierarchy | ANS-style hierarchy using Gforth terminology |

## Forth 2012 profile

`forth2012types.txt` is a practical standard-like profile rather than a literal
encoding of every Forth 2012 type relationship.

### Numeric hierarchy

```text
char < +n < u < n < x
       +n < n
```

This makes unsigned values acceptable wherever signed numbers are expected:

```text
u < n
```

That flattening is convenient for an evaluator with only one specification per
word. For example, a numeric word can use `( n -- n )` and still accept an
unsigned input. The cost is reduced precision: signed and unsigned values are
not independent branches.

### Flags

```text
flag < x
```

Flags are cells but not numbers. Consequently, a flag cannot be passed directly
to a word requiring `n` or `u`.

### Addresses

```text
a-addr < c-addr < addr < u < n < x
```

Aligned addresses are the most specific address type. All addresses are also
unsigned values and, because of the flattened numeric hierarchy, signed-number
values.

### Double-cell numbers

```text
+d < ud < d < xd < x
```

The profile also declares `+d < d` directly. As with single-cell numbers,
unsigned double values are placed below signed double values for practical
composition.

### Additional cell types

The profile includes:

```text
xt < x
ior < n
fam < x
fileid < x
wid < x
```

## ANS94 profile

`ans94types.txt` preserves the branching structure of the ANS Forth type
relations more closely.

### Numeric hierarchy

```text
     n|u < x
     /   \
    n     u
     \   /
       +n
```

Expressed as relations:

```text
+n < n
+n < u
n  < n|u
u  < n|u
n|u < x
```

The important property is that `n` and `u` are incomparable. A signed value is
not automatically unsigned, and an unsigned value is not automatically signed.
Only non-negative numbers, `+n`, inhabit both branches.

`n|u` is an evaluator helper representing the Standard's contextual `n|u`
notation. It provides a common numeric supertype without incorrectly declaring
one signedness to be a subtype of the other.

### Characters and flags

```text
char < +n
true  < flag < x
false < flag < x
```

Characters are non-negative numeric values. Flags remain separate from the
numeric hierarchy, while `true` and `false` refine the general flag type.

### Addresses

```text
a-addr < c-addr < addr < u < n|u < x
```

Addresses belong to the unsigned branch. Unlike the Forth 2012 demo profile,
they do not thereby become signed numbers.

### Double-cell numbers

```text
    d|ud < xd
     /   \
    d     ud
     \   /
       +d
```

Expressed as relations:

```text
+d < d
+d < ud
d  < d|ud
ud < d|ud
d|ud < xd
```

Thus `d` and `ud` remain incomparable. The helper `d|ud` is their common join.

### System and optional word-set types

ANS94 adds separate compilation and execution types:

```text
colon-sys  do-sys  case-sys  of-sys
orig       dest    loop-sys  nest-sys
```

These are separate roots because they are implementation-dependent system
objects rather than ordinary cells.

It also includes file, search-order, and floating-point types:

```text
ior < n
fam < x
fileid < x
wid < x
```

The floating-point value type `r` is a separate root. It is not a subtype of
`x`, because the Standard permits a separate floating-point stack and an
implementation-dependent representation.

Floating-point addresses refine aligned addresses:

```text
f-addr  < a-addr
sf-addr < a-addr
df-addr < a-addr
```

## Gforth 1.0 profile

`gforth1.0types.txt` retains the ANS94-style branching hierarchy while adopting
the terminology documented by the Gforth 1.0 development series.

### Gforth aliases

Gforth commonly calls an unspecified cell `w` and a flag `f`:

```text
type x    X w cell
type flag f boolean
```

Therefore `w`, `X`, and `cell` all denote `x`; `f` and `boolean` denote
`flag`.

The profile also provides Gforth file aliases:

```text
wior = ior
fid  = fileid
```

These are aliases, not additional subtype levels.

### Numeric, flag, address, and double hierarchies

These are structurally the same as in the ANS94 profile:

```text
+n < n < n|u < x
+n < u < n|u < x

true  < flag < x
false < flag < x

a-addr < c-addr < addr < u

+d < d  < d|ud < xd
+d < ud < d|ud < xd
```

This means Gforth's `n` and `u`, and likewise `d` and `ud`, remain separate
branches in the static model.

### Name tokens

Gforth adds name tokens:

```text
nt < x
```

A name token identifies a dictionary name and is distinct from an execution
token, although both are cell-sized:

```text
nt < x
xt < x
```

Neither is a subtype of the other.

### Floating-point types

As in the ANS94 profile, `r` is separate from `x` and the floating-address types
are aligned-address subtypes:

```text
f-addr  < a-addr
sf-addr < a-addr
df-addr < a-addr
```

This follows Gforth's documented unified stack-effect notation while retaining
the semantic distinction between data-stack cells and floating-point values.

## Most important practical differences

### 1. Flattened versus branching numeric types

The Forth 2012 profile permits:

```text
u < n
ud < d
```

The ANS94 and Gforth profiles instead use joins:

```text
n, u   < n|u
d, ud  < d|ud
```

The Forth 2012 profile therefore composes more easily but catches fewer
signedness errors. ANS94 and Gforth are stricter.

### 2. Address compatibility

All three profiles place addresses under `u`. The consequence differs:

- In the Forth 2012 profile, `u < n`, so an address eventually also satisfies
  `n`.
- In ANS94 and Gforth, an address stays on the unsigned branch and does not
  satisfy a signed-only input.

### 3. Gforth-specific vocabulary

The Gforth profile adds aliases and `nt`, making stack effects copied from the
Gforth manual directly usable. For example, `( w -- w w )` uses the same type
as `( x -- x x )`, and `( nt -- xt )` can distinguish dictionary-name tokens
from executable tokens.

### 4. Scope

- `forth2012types.txt` favors convenient checking of ordinary Forth programs.
- `ans94types.txt` favors fidelity to the ANS type lattice.
- `gforth1.0types.txt` extends the stricter ANS-style model for Gforth's naming
  and documented implementation types.
