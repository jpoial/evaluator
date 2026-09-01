# Pseudocode for `gforth-evaluator.fs`

## 1. Purpose

`gforth-evaluator.fs` is a static stack-effect evaluator. It does not execute the analyzed Forth program. Instead, it:

1. loads a type system;
2. loads word, literal, parser, defining-word, and control-structure specifications;
3. scans the program;
4. converts every runtime word into a symbolic stack effect;
5. composes those effects while checking type compatibility;
6. infers effects for definitions and control structures; and
7. prints an annotated program or source-aware diagnostics.

The pseudocode below describes the basic building blocks and high-level execution logic. It is intentionally language-neutral rather than a word-for-word translation of the GForth source.

---

## 2. Core conventions

```text
StackEffect = (left -- right)

left  = values required before execution
right = values remaining after execution

A vector is written from the deeper stack item to the top item.
Therefore, the last item in a vector is the stack top.
```

A type symbol has the form:

```text
TypeSymbol(typeName, wildcardPosition, explicitlyIndexed)
```

Examples:

```text
n       -> TypeSymbol("n", 0, false)
x[1]    -> TypeSymbol("x", 1, true)
```

Equal wildcard positions correlate occurrences of the same abstract value. During evaluation, wildcard positions are freshened, unified, substituted, and finally renumbered for readable output.

---

## 3. Basic storage building blocks

The Forth implementation manually allocates all records and vectors. Conceptually, they are ordinary structures.

### 3.1 Persistent strings

```pseudocode
record PersistentString:
    length
    bytes[length]

function COPY_STRING(characters):
    result = ALLOCATE(sizeof(length) + characters.length)
    result.length = characters.length
    result.bytes = characters
    return result

function CONCAT(a, b):
    return COPY_STRING(a.bytes + b.bytes)

function CANONICALIZE_WORD(text):
    return UPPERCASE(TRIM(text))
```

Word, scanner, type-alias, and control-role lookup is ASCII case-insensitive. Quoted delimiters and source text preserve their original contents.

### 3.2 Dynamic pointer vectors

```pseudocode
record Vector:
    count
    capacity
    data[capacity]

function VECTOR_NEW(initialCapacity):
    capacity = MAX(initialCapacity, 4)
    return Vector(count = 0, capacity, allocated data)

procedure VECTOR_PUSH(vector, value):
    if vector.count + 1 > vector.capacity:
        vector.capacity = vector.capacity * 2
        RESIZE(vector.data, vector.capacity)
    vector.data[vector.count] = value
    vector.count += 1

function VECTOR_LAST(vector):
    return vector.data[vector.count - 1]

procedure VECTOR_REMOVE_LAST(vector):
    if vector.count > 0:
        vector.count -= 1
```

### 3.3 Source positions and diagnostics

```pseudocode
record Span:
    sourceName
    startLine
    startColumn
    endLine
    endColumn

record SourceWord:
    text
    span
    quoted

record Diagnostic:
    message
    reason
    span
    sourceLine
    caretMarker
```

```pseudocode
procedure RAISE_DIAGNOSTIC(message, reason, span):
    global currentDiagnostic = Diagnostic(message, reason, span,
                                          FIND_SOURCE_LINE(span),
                                          MAKE_CARET_MARKER(span))
    THROW(EVALUATOR_ERROR)

procedure REPORT_CURRENT_DIAGNOSTIC():
    print message, location, reason, source line, and caret marker
    write the diagnostic to the log if the log is open
    global diagnosticCount += 1
    global currentDiagnostic = null
```

Expected evaluator failures use a dedicated exception code. Unexpected GForth exceptions are rethrown rather than converted into ordinary diagnostics.

---

## 4. Scanner

### 4.1 Scanner state

```pseudocode
record Scanner:
    sourceName
    text
    sourceLines
    offset
    line
    column
    lastLine
    lastColumn
```

### 4.2 File normalization

```pseudocode
function LOAD_NORMALIZED_FILE(path):
    raw = READ_ALL_BYTES(path)
    text = REPLACE_CRLF_AND_CR_WITH_LF(raw)
    if text ends with LF:
        remove exactly one final LF
    return text
```

### 4.3 General scanning operations

```pseudocode
procedure ADVANCE(scanner):
    remember current line and column as the last position
    consume the current character
    if character == LF:
        scanner.line += 1
        scanner.column = 1
    else:
        scanner.column += 1

procedure SKIP_WHITESPACE(scanner):
    while not at end and current character is whitespace:
        ADVANCE(scanner)

procedure SKIP_IGNORABLE(scanner):
    repeat:
        skip whitespace
        if current character is backslash:
            consume through the end of the line
        else:
            stop
```

Type and specification files use `SKIP_IGNORABLE`, so backslash starts a line comment. Program scanning uses `SKIP_WHITESPACE`; program comments are therefore controlled by parser-word specifications rather than hard-coded scanner behavior.

```pseudocode
function READ_WORD(scanner, stopCharacters):
    start = current position
    consume characters until whitespace, backslash, or a stop character
    if no character was consumed:
        return null
    return SourceWord(consumed text, span from start to last character, false)

function READ_PROGRAM_WORD(scanner, stopCharacters):
    start = current position
    consume characters until whitespace or a stop character
    if no character was consumed:
        return null
    return SourceWord(consumed text, corresponding span, false)

function READ_QUOTED(scanner):
    consume opening quote
    decode escaped newline, carriage return, tab, quote, and backslash
    require a closing quote
    return SourceWord(decoded text, complete span, true)

function PARSE_UNTIL(scanner, delimiter):
    copy source characters until delimiter is found
    consume the delimiter
    return SourceWord(copied text, covered span, false)
    // Return null if the delimiter is absent.
```

`NEXT_LINE_ATOMS` is used by the type and specification loaders. It returns all quoted or unquoted atoms from one logical line while preserving source spans.

---

## 5. Type system

### 5.1 Data model

```pseudocode
record Alias:
    name
    typeIndex

record ScannerDefinition:
    canonicalName
    delimiter

record TypeSystem:
    sourceName
    sourceLines
    displayTypes[]
    aliases[]
    relationMatrix[][]
    scanners[]
```

Relation values conceptually mean:

```text
0 = unrelated
1 = first type is a subtype of the second
2 = first type is a supertype of the second
3 = equal/equivalent
```

### 5.2 Loading

```pseudocode
function LOAD_TYPE_SYSTEM(file):
    scanner = SCANNER_FROM_FILE(file)
    types = []
    aliases = []
    pendingRelations = []
    scanners = []

    for each nonempty line from NEXT_LINE_ATOMS(scanner):
        directive = CANONICALIZE_WORD(line[0].text)

        if directive == "TYPE":
            require at least one name
            typeIndex = types.count
            types.push(line[1].text)              // display name
            for each name in line[1..end]:
                reject duplicate aliases
                aliases.push(Alias(name.text, typeIndex))

        else if directive == "REL":
            require exactly: REL subtype < supertype
            pendingRelations.push(subtype, supertype, source span)

        else if directive == "SCANNER":
            require exactly: SCANNER name delimiter
            reject empty or duplicate scanner names
            scanners.push(canonical name and literal delimiter)

        else:
            RAISE_DIAGNOSTIC("Unknown directive", directive, line span)

    matrix = square zero matrix with one row per type
    typeSystem = TypeSystem(..., matrix, scanners)

    for each pending relation:
        resolve both aliases or report an unknown type
        matrix[subtype][supertype] = SUBTYPE

    NORMALIZE_RELATIONS(typeSystem)
    return typeSystem
```

### 5.3 Relation normalization

```pseudocode
procedure NORMALIZE_RELATIONS(typeSystem):
    for every pair (i, j):
        add identity relations i == j
        add inverse supertype/subtype relations
        turn two opposite subtype relations into equality
        make equality symmetric

    for every triple (i, k, j):
        if relation(i, k) and relation(k, j) are the same
           nonzero directional relation:
            relation(i, j) = that relation
```

```pseudocode
function TYPE_RELATION(typeA, typeB, typeSystem):
    if either name is missing or unknown:
        return UNRELATED
    return relationMatrix[index(typeA)][index(typeB)]
```

---

## 6. Stack-effect specifications

### 6.1 Data model

```pseudocode
record TypeSymbol:
    typeName
    position
    explicitIndex

record Specification:
    left[]
    right[]

    parseString       // delimiter, if any
    parseMode         // NONE, UNTIL, WORD, or DEFINITION
    defineMode        // NONE, COLON, CONSTANT, or VARIABLE
    controlRole       // null or canonical role such as IF, END, DO, INDEX
    immediate         // boolean
    stateMode         // ANY, INTERPRET, or COMPILE

    sourceSpan
    originLabel
    maxPosition
```

Metadata is retained in dictionary specifications. `RUNTIME_CLONE` copies only the left and right vectors because parser/definition/control metadata is not a runtime stack effect.

### 6.2 Parsing an effect

```pseudocode
function PARSE_TYPE_SYMBOL(text, span, typeSystem):
    if text has suffix "[unsignedInteger]":
        typeName = text before "["
        position = parsed integer
        explicit = true
    else:
        typeName = text
        position = 0
        explicit = false

    require typeName to exist in typeSystem
    return TypeSymbol(typeName, position, explicit)

function PARSE_STACK_EFFECT(body, typeSystem, span):
    arrow = position of "--" in body
    if arrow is absent:
        RAISE_DIAGNOSTIC("Missing -- in stack effect", null, span)

    leftText = body before arrow
    rightText = body after arrow
    left = parse whitespace-separated type symbols from leftText
    right = parse whitespace-separated type symbols from rightText

    spec = Specification(left, right, default metadata)
    spec.maxPosition = maximum symbol position
    return spec
```

### 6.3 Cloning and substitution

```pseudocode
function CLONE_SPEC(spec):
    deep-copy all symbols in left and right
    copy metadata fields
    return copy

procedure SUBSTITUTE(oldSymbol, newSymbol, specOrSpecList):
    replace every symbol equal to oldSymbol
    with a clone of newSymbol

procedure FRESHEN_WILDCARDS(amount, spec):
    add amount to every already-numbered wildcard
    assign a new unique position to every unnumbered wildcard
    update spec.maxPosition
```

Two symbols are considered equal for substitution when both their type name and wildcard position match.

---

## 7. Core stack-effect composition

### 7.1 Composing two effects

For effects `first` and `second`, composition means “execute `first`, then execute `second`.”

```pseudocode
function COMPOSE(effectList, first, second, typeSystem):
    prefix = first
    incoming = second

    loop:
        if prefix is null or incoming is null:
            return null

        resultInputs = clone(prefix.left)
        resultOutputs = clone(incoming.right)

        if prefix.right is empty:
            // All inputs of the second effect must come from before first.
            newInputs = clone(incoming.left) + resultInputs
            return SPEC_FROM_SIDES(newInputs, resultOutputs, incoming metadata)

        if incoming.left is empty:
            // The second effect consumes none of the first effect's output.
            newOutputs = clone(prefix.right) + resultOutputs
            return SPEC_FROM_SIDES(clone(prefix.left), newOutputs,
                                   incoming metadata)

        produced = LAST(prefix.right)
        required = LAST(incoming.left)
        relation = TYPE_RELATION(produced.typeName,
                                 required.typeName,
                                 typeSystem)

        if relation == UNRELATED:
            remember prefix, incoming, produced, and required as clash context
            return null

        // Keep the more specific of the two comparable types.
        unifiedType = required.typeName if produced is a supertype of required
                      else produced.typeName
        unified = NEW_SYMBOL(unifiedType, freshPosition,
                             produced.explicitIndex OR required.explicitIndex)

        // Preserve all data-flow correlations.
        substitute produced and required with unified in:
            resultInputs
            resultOutputs
            prefix.right
            incoming.left
            every specification in effectList

        remove the matched top item from prefix.right
        remove the matched top item from incoming.left

        prefix = SPEC_FROM_SIDES(resultInputs, prefix.right, prefix metadata)
        incoming = SPEC_FROM_SIDES(incoming.left, resultOutputs,
                                   incoming metadata)
```

### 7.2 Evaluating a sequence

```pseudocode
function EVALUATE_EFFECT_LIST(effectList, typeSystem):
    clear remembered clash
    assign every effect a disjoint wildcard range
    result = identity effect ( -- )

    for each effect in effectList from left to right:
        result = COMPOSE(effectList, result, effect, typeSystem)
        if result is null:
            return null

    return NORMALIZE_EFFECTS_TOGETHER(effectList, result)
```

Callers normally clone the list before evaluation because wildcard freshening and substitution mutate the list.

### 7.3 Final normalization

```pseudocode
function NORMALIZE_EFFECTS_TOGETHER(effectList, result):
    move every independently evaluated effect into a disjoint position range

    build a table of equivalent symbols and count:
        all occurrences
        occurrences visible in the final result
        whether an explicit index was present

    assign compact positions 1, 2, 3, ... only where a visible
    correlation needs to be shown

    substitute normalized symbols through result and effectList
    return result
```

The result and each per-word annotation are normalized together, so displayed indices refer to the same abstract values.

---

## 8. Branches and repetition

### 8.1 Unifying alternative effects

```pseudocode
function UNIFY_ALTERNATIVES(longer, shorter, typeSystem):
    require longer has at least as many inputs and outputs
    inputDepthDifference = longer.left.count - shorter.left.count
    outputDepthDifference = longer.right.count - shorter.right.count
    require both differences to be equal

    align shorter with longer after the implicit unchanged stack prefix

    for every aligned input pair:
        require comparable types
        merge using the subtype (the stronger accepted input requirement)
        propagate substitutions

    for every aligned output pair:
        require comparable types
        merge using the supertype (the guarantee common to both branches)
        retain a value correlation only when both alternatives guarantee it

    return normalized merged effect, or null on incompatibility
```

```pseudocode
function BRANCH_MEET(a, b, typeSystem):
    require input-depth difference == output-depth difference
    call UNIFY_ALTERNATIVES with the effect having the greater depth first
```

### 8.2 Repeated/idempotent effects

```pseudocode
function REPEAT_EFFECT(effect, typeSystem):
    squared = EVALUATE_EFFECT_LIST([clone(effect), clone(effect)], typeSystem)
    if squared is null:
        return null
    return BRANCH_MEET(effect, squared, typeSystem)
```

Prefix closure and related helper operations merge corresponding input/output prefixes when checking loop idempotence.

---

## 9. Specification dictionaries and control descriptions

### 9.1 Dictionaries

```pseudocode
record DictionaryEntry:
    canonicalKey
    surfaceName
    value

record SpecificationSet:
    words[]
    literals[]
    controlStructures[]
```

Word and literal lookup uses canonical uppercase keys. Duplicate declarations are rejected.

### 9.2 Declarative control structures

```pseudocode
record ControlStructure:
    name
    openingRole
    boundaryRoles[]
    boundaryIsOptional[]
    closingRole
    segmentNames[]
    meaningExpression
```

Meaning expressions form an AST:

```text
EMPTY
SEGMENT(name)
CONTROL(role)
SEQUENCE(left, right)
BRANCH_MEET(left, right)
REPEAT(inner)
```

Example conceptual syntax:

```text
OPEN <first-segment> [ BOUNDARY <second-segment> ] CLOSE
```

Example meaning:

```text
SEQUENCE(CONTROL(IF),
         BRANCH_MEET(SEGMENT(then), SEGMENT(else)))
```

If a specification file omits explicit syntax blocks, built-in descriptions are installed for these compatibility families:

```text
IF ... [ELSE ...] FI
BEGIN ... WHILE ... REPEAT
BEGIN ... AGAIN
BEGIN ... UNTIL
DO ... LOOP
```

---

## 10. Loading the specification file

```pseudocode
function LOAD_SPECIFICATIONS(file, typeSystem):
    set = new empty SpecificationSet
    scanner = SCANNER_FROM_FILE(file)
    pendingLine = null

    while another line exists:
        line = pendingLine if present, otherwise NEXT_LINE_ATOMS(scanner)
        pendingLine = null
        skip empty lines

        head = canonical first atom

        if head == "LITERAL":
            PARSE_LITERAL_LINE(line, typeSystem, set)

        else if head == "SYNTAX":
            collect following more-indented syntax and effect lines
            structure = PARSE_CONTROL_STRUCTURE(collected lines)
            set.controlStructures.add(structure)
            preserve the first non-indented line as pendingLine

        else:
            PARSE_WORD_SPECIFICATION(line, typeSystem, set)

    INSTALL_BUILTIN_CONTROL_STRUCTURES_IF_ABSENT(set)
    return set
```

### 10.1 Ordinary word metadata

```pseudocode
function PARSE_WORD_SPECIFICATION(line, typeSystem, set):
    word = line[0]
    locate matching "(" and ")"
    bodySpec = PARSE_STACK_EFFECT(text inside parentheses)

    parse clauses before "(":
        parse word
        parse until DELIMITER
        parse definition DELIMITER
        scan DELIMITER
        define [colon | constant | variable]
        control ROLE
        immediate
        state/context [interpret | outer | compile | definition]

    resolve an unquoted delimiter through the type-system scanner table
    infer bare DEFINE as CONSTANT or VARIABLE from its stack shape
    validate defining-word shape:
        COLON    must be ( -- )
        CONSTANT must be ( x -- )
        VARIABLE must be ( -- y )

    attach metadata and source span to bodySpec
    add bodySpec to the case-insensitive word dictionary
```

```pseudocode
function PARSE_LITERAL_LINE(line, typeSystem, set):
    parse: LITERAL kind ( effect )
    require effect.left to be empty
    add effect under canonical literal kind
```

### 10.2 Parsing control syntax and meaning

```pseudocode
function PARSE_CONTROL_SYNTAX(tokens):
    openingRole = first token
    require the next token to be a <segment>

    while tokens remain before the closing role:
        optionally consume "["
        consume a boundary role
        consume its <segment>
        if optional, require "]"

    closingRole = final token
    return ControlStructure(...)

function PARSE_CONTROL_MEANING(lines):
    for each line:
        if line starts with EITHER:
            fold alternatives using BRANCH_MEET nodes
        else if line starts with REPEAT:
            make REPEAT(SEQUENCE(rest of line))
        else:
            make SEQUENCE(each segment or control-role atom)

    return SEQUENCE(all nonempty line expressions)
```

---

## 11. Resolving program words

```pseudocode
function RESOLVE_RUNTIME_SPEC(token, doDepth, typeSystem, specSet):
    if token names a current local:
        return clone(local effect)

    if token is RECURSE:
        return clone(current provisional definition effect)
        or report that no recursive specification exists

    if token exists in word dictionary:
        if it is a control word:
            if its role is INDEX and doDepth > 0:
                return runtime effect for loop index
            report "Unexpected control word"
        return RUNTIME_CLONE(dictionary specification)

    if token is a signed decimal with a trailing period:
        return clone(LITERAL DOUBLE effect), or report missing literal spec

    if token is a signed decimal integer:
        return clone(LITERAL INTEGER effect), or report missing literal spec

    report "No specification found for token"
```

Parser words may consume either one following word or source through a delimiter. Their consumed text contributes to the source span but is not treated as program words.

---

## 12. Definition evaluation

### 12.1 Linear definition body

```pseudocode
function PARSE_DEFINITION_SEQUENCE(definitionName, scanner,
                                   typeSystem, specSet,
                                   doDepth, closingRole):
    sequence = []

    loop:
        token = NEXT_PROGRAM_WORD(scanner)
        if token is absent:
            report "Missing end of definition"

        dictionarySpec = lookup token unless token is a local

        if dictionarySpec is a control word:
            if dictionarySpec.role == closingRole:
                return EVALUATE_SEQUENCE(sequence, definitionName, typeSystem)

            if dictionarySpec.role opens a known structure:
                nestedEffect = PARSE_CONTROL_STRUCTURE_INSTANCE(...)
                ADD_AND_CHECK(sequence, token, nestedEffect)

            else if dictionarySpec.role == INDEX and doDepth > 0:
                ADD_AND_CHECK(sequence, token, loop-index effect)

            else:
                report "Unexpected control word in definition"

        else if dictionarySpec is immediate:
            reject defining words inside definitions

            if token starts a locals declaration:
                consume declaration text
                create a correlated symbolic input for each local
                register each local as a zero-input producer
                ADD_AND_CHECK(sequence, declaration binding effect)
            else:
                consume parser input
                ADD_AND_CHECK(sequence, runtime clone of parser word effect)

        else:
            effect = RESOLVE_RUNTIME_SPEC(token, doDepth, ...)
            ADD_AND_CHECK(sequence, token, effect)
```

```pseudocode
procedure ADD_AND_CHECK(sequence, token, effect):
    sequence.push(effect with token origin)
    if EVALUATE_EFFECT_LIST(clone(sequence)) fails:
        remove the just-added effect
        raise a source diagnostic at the offending token
```

Eager checking locates a clash near the word that introduced it rather than waiting until the complete definition has been read.

### 12.2 Parsing a control-structure instance

```pseudocode
function PARSE_CONTROL_STRUCTURE_INSTANCE(opener, openerSpec,
                                          definitionName, scanner,
                                          typeSystem, specSet, doDepth):
    candidates = all structures whose openingRole matches openerSpec.role
    segments = []
    currentSegment = []
    stage = 0
    innerDepth = doDepth + 1 if opening role is DO else doDepth

    loop:
        token = NEXT_PROGRAM_WORD(scanner)
        if absent:
            report "Missing control terminator"

        if token has a control role:
            boundaryMatches = candidates matching this role at current stage
            closeMatches = candidates that may close at current stage

            if closeMatches exist and boundaryMatches do not:
                segments.push(currentSegment)
                structure = one close match
                segmentEffects = EVALUATE each segment sequence
                span = from opener through closing token
                return EVALUATE_CONTROL_EXPRESSION(
                    structure.meaningExpression,
                    structure,
                    segmentEffects,
                    typeSystem,
                    specSet,
                    span)

            if boundaryMatches exist and closeMatches do not:
                segments.push(currentSegment)
                currentSegment = []
                candidates = boundaryMatches
                stage += 1
                continue

            if role opens a nested structure:
                effect = recursively parse nested structure
            else if role == INDEX and innerDepth > 0:
                effect = loop-index runtime effect
            else:
                report unexpected control word

            ADD_AND_CHECK(currentSegment, token, effect)

        else:
            process local, immediate/parser, or ordinary runtime word
            exactly as in a linear definition sequence
```

### 12.3 Evaluating a control meaning AST

```pseudocode
function EVALUATE_CONTROL_EXPRESSION(expr, structure,
                                     segmentEffects, typeSystem,
                                     specSet, span):
    match expr.kind:
        EMPTY:
            return identity effect

        SEGMENT:
            return clone(effect belonging to named segment)
            // Missing optional segments have identity effect.

        CONTROL:
            return runtime effect associated with this control role

        SEQUENCE:
            left = recursively evaluate expr.left
            right = recursively evaluate expr.right
            return EVALUATE_EFFECT_LIST([left, right], typeSystem)

        BRANCH_MEET:
            left = recursively evaluate expr.left
            right = recursively evaluate expr.right
            result = BRANCH_MEET(left, right, typeSystem)
            if result is null:
                report "Non-comparable control alternatives"
            return result

        REPEAT:
            inner = recursively evaluate expr.inner
            result = REPEAT_EFFECT(inner, typeSystem)
            if result is null:
                report "Non-idempotent repeated effect"
            return result
```

### 12.4 Top-level colon definitions

```pseudocode
procedure PARSE_COLON_DEFINITION(opener, openerSpec, scanner,
                                 typeSystem, specSet):
    require openerSpec effect == ( -- )
    name = consume and validate the new word name

    if the first body item is a recognized locals-style documentation clause:
        derive a generic provisional effect from documented input/output counts
        register that effect under name
        skip through the definition terminator
        log the definition
        return

    save outer local/recursion state
    create an empty local dictionary
    use any forward-seeded specification as the RECURSE seed

    try:
        effect = PARSE_DEFINITION_SEQUENCE(name, ..., closingRole = END)
    on evaluator diagnostic:
        report it
        recover by scanning to the matching definition terminator
        restore outer state
        return

    restore outer state
    replace any placeholder with the inferred effect
    log "name effect"
```

---

## 13. Forward-definition seeding

Before normal program parsing, the source is scanned once to install limited placeholders for words referenced before their full definition.

```pseudocode
procedure SEED_FORWARD_DEFINITIONS(sourceName, sourceText,
                                   typeSystem, specSet):
    previewScanner = new scanner over sourceText

    while token exists:
        spec = dictionary lookup(token)

        if spec is a defining word:
            definitionName = consume next word

            if name is not already defined:
                if defining mode is COLON and body starts with
                   a recognized locals documentation clause:
                    placeholder = generic effect with documented depths
                else if defining mode is CONSTANT:
                    placeholder = generic zero-input producer
                else if defining mode is VARIABLE:
                    placeholder = generic zero-input producer

                if placeholder exists:
                    temporarily register it under definitionName

            if defining mode is COLON:
                skip the complete, possibly nested, definition body

        else if spec is immediate:
            skip the source text consumed by that parser word
```

Ordinary comments such as `( -- n )` are not accepted as declarations. Only recognized locals-style documentation is used to seed a colon definition.

---

## 14. Top-level outer interpreter

### 14.1 Program representation

```pseudocode
record Program:
    sourceName
    sourceText
    sourceLines
    visibleWords[]
    spans[]
    runtimeEffects[]
```

A hidden effect may be added for top-level bookkeeping, such as consuming the value used by `CONSTANT`. Hidden entries participate in type evaluation but are omitted from annotation rows.

### 14.2 Top-level defining words

```pseudocode
procedure DEFINE_CONSTANT(token, definingSpec, scanner,
                          typeSystem, specSet, program):
    name = consume new word name
    require definingSpec shape == ( x -- )

    currentEffect = EVALUATE_EFFECT_LIST(clone(program.runtimeEffects))
    require currentEffect.right is nonempty

    top = LAST(currentEffect.right)
    expected = definingSpec.left[0]
    require top.type and expected.type to be comparable

    constantEffect = ( -- top.type )
    register constantEffect under name
    log definition

    // Model compile-time consumption of the defining value.
    program.addHiddenEffect(( top.type -- ))

procedure DEFINE_VARIABLE(token, definingSpec, scanner, specSet):
    name = consume new word name
    require definingSpec shape == ( -- y )
    register a runtime clone of definingSpec under name
    log definition
```

### 14.3 Processing one top-level token

```pseudocode
procedure PROCESS_TOP_LEVEL_TOKEN(token, dictionarySpec, scanner,
                                  typeSystem, specSet, program):
    if dictionarySpec is absent:
        effect = RESOLVE_RUNTIME_SPEC(token, doDepth = 0, ...)
        ADD_CHECKED_PROGRAM_WORD(program, token, effect)
        return

    require dictionarySpec is allowed in interpretation state

    if dictionarySpec is not immediate:
        effect = RESOLVE_RUNTIME_SPEC(token, 0, ...)
        ADD_CHECKED_PROGRAM_WORD(program, token, effect)
        return

    if dictionarySpec defines a word:
        dispatch by define mode:
            COLON    -> PARSE_COLON_DEFINITION(...)
            CONSTANT -> DEFINE_CONSTANT(...)
            VARIABLE -> DEFINE_VARIABLE(...)
            otherwise report unsupported defining word

    else if dictionarySpec is a control word:
        report "Unexpected control word in top-level program"

    else:
        span = consume parser input if required
        effect = RUNTIME_CLONE(dictionarySpec)
        ADD_CHECKED_PROGRAM_WORD(program, token with span, effect)
```

### 14.4 Complete program parse

```pseudocode
function PARSE_PROGRAM(sourceName, sourceText, typeSystem, specSet):
    SEED_FORWARD_DEFINITIONS(sourceName, sourceText, typeSystem, specSet)

    scanner = new Scanner(sourceName, sourceText)
    program = new Program(sourceName, sourceText, scanner.sourceLines)

    while token = NEXT_PROGRAM_WORD(scanner):
        dictionarySpec = lookup token in specSet.words

        try:
            PROCESS_TOP_LEVEL_TOKEN(token, dictionarySpec, scanner,
                                    typeSystem, specSet, program)
        on evaluator diagnostic:
            REPORT_CURRENT_DIAGNOSTIC()
            recover by:
                consuming the failed parser/defining-word payload, or
                scanning to the matching colon-definition terminator

    return program
```

Recoverable diagnostics are counted so parsing can continue and report more than one source error.

---

## 15. Annotation

```pseudocode
procedure PRINT_ANNOTATION(program, normalizedEffects, finalEffect):
    print "> " + symbols(finalEffect.left)

    for i from 0 to program.visibleWords.count - 1:
        if program.visibleWords[i] is not empty:       // not hidden
            print program.visibleWords[i]
            print normalizedEffects[i]

    print "< " + symbols(finalEffect.right)
```

The `>` line is the initial stack required by the complete program. The `<` line is the stack left after the complete program.

---

## 16. Command-line and complete execution logic

### 16.1 Argument parsing

```pseudocode
record Configuration:
    typesFile = "ex1types.txt"
    specsFile = "ex1specs.txt"
    programFile = "ex1prog.txt"
    commandLineProgramWords = []

function PARSE_ARGUMENTS(argv):
    cfg = Configuration with defaults

    for each argument after the GForth source name:
        if argument is --types:
            cfg.typesFile = require next argument
        else if argument is --specs:
            cfg.specsFile = require next argument
        else if argument is --prog:
            cfg.programFile = require next argument
        else if argument is --help or -h:
            raise usage diagnostic
        else:
            cfg.commandLineProgramWords.push(argument)

    if commandLineProgramWords is nonempty:
        command-line text takes precedence over programFile

    return cfg
```

### 16.2 Main evaluator run

```pseudocode
procedure RUN_NATIVE():
    reset current diagnostic, diagnostic count, and current token

    cfg = PARSE_ARGUMENTS(command line)
    open log:
        cfg.programFile + ".log" if using a program file
        "command-line.log" otherwise

    print selected type/specification/program sources

    typeSystem = LOAD_TYPE_SYSTEM(cfg.typesFile)
    specSet = LOAD_SPECIFICATIONS(cfg.specsFile, typeSystem)

    if cfg.commandLineProgramWords is nonempty:
        programName = "<command line>"
        programText = JOIN(cfg.commandLineProgramWords, " ")
    else:
        programName = cfg.programFile
        programText = LOAD_NORMALIZED_FILE(cfg.programFile)

    program = PARSE_PROGRAM(programName, programText, typeSystem, specSet)

    if diagnosticCount > 0:
        THROW(ALREADY_REPORTED_ERROR)

    // Keep final effect and annotation substitutions synchronized.
    normalizedEffects = deep clone(program.runtimeEffects)
    finalEffect = EVALUATE_EFFECT_LIST(normalizedEffects, typeSystem)

    if finalEffect is null:
        raise "Type clash in top-level program"

    print source text
    print normalized visible program words
    PRINT_ANNOTATION(program, normalizedEffects, finalEffect)
    close log
```

### 16.3 Process entrypoint

```pseudocode
function MAIN():
    try:
        RUN_NATIVE()
        return exit status 0

    catch ALREADY_REPORTED_ERROR:
        close log
        return exit status 1

    catch EVALUATOR_ERROR:
        close log
        REPORT_CURRENT_DIAGNOSTIC()
        return exit status 1

    catch unexpectedError:
        close log
        rethrow unexpectedError
```

The source ends by passing `MAIN()` to GForth's `bye`, making the returned value the process exit status.

---

## 17. End-to-end summary

```pseudocode
MAIN
  -> parse CLI
  -> open log
  -> load and normalize type relations
  -> load word/literal/parser/control specifications
  -> choose program file or command-line program text
  -> pre-scan limited forward definitions
  -> run the top-level symbolic outer interpreter
       -> process ordinary words as runtime effects
       -> consume parser-word input
       -> register constants and variables
       -> infer colon definitions
            -> recursively evaluate control structures
            -> merge branches and check repeated effects
       -> recover from reportable source errors
  -> clone all collected top-level effects
  -> compose and normalize them together
  -> print per-word annotation and final stack effect
  -> close log and return success/failure status
```
