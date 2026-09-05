#!/usr/bin/env python3
"""Python 3 port of gforth-evaluator.fs.

This is a static stack-effect evaluator, not a Forth runtime.  It accepts the
same type, specification, and program inputs as the native GForth evaluator.
"""
from __future__ import annotations

import os
import re
import sys
from dataclasses import dataclass, field
from typing import Optional


# ---------------------------------------------------------------------------
# Source model and diagnostics


def canon(text: str) -> str:
    return text.strip().upper()


@dataclass
class Span:
    source: str
    sl: int
    sc: int
    el: int
    ec: int

    @staticmethod
    def cover(a: Optional["Span"], b: Optional["Span"]) -> Optional["Span"]:
        if a is None:
            return b
        if b is None:
            return a
        return Span(a.source, a.sl, a.sc, b.el, b.ec)

    def located(self) -> bool:
        return self.sl > 0 and self.sc > 0

    def start_text(self) -> str:
        if self.located():
            return f"{self.source}:{self.sl}:{self.sc}"
        return self.source


@dataclass
class Word:
    text: str
    span: Span
    quoted: bool = False


@dataclass
class Diagnostic:
    message: str
    reason: str = ""
    span: Optional[Span] = None
    source_line: Optional[str] = None
    marker: Optional[str] = None

    def summary(self) -> str:
        out = self.message
        if self.span is not None and self.span.located():
            out += " at " + self.span.start_text()
        if self.reason:
            out += ": " + self.reason
        if out and out[-1] not in ".!?":
            out += "."
        return out

    def render(self) -> str:
        # GForth's numeric `.` printer leaves one space after line and column.
        # Preserve that observable terminal layout while log summaries use the
        # conventional compact source:line:column form from summary().
        out = self.message
        if self.span is not None and self.span.located():
            terminal_location = f"{self.span.source}:{self.span.sl} :{self.span.sc} "
            out += " at " + terminal_location
        if self.reason:
            out += ": " + self.reason
        if out and out[-1] not in ".!?":
            out += "."
        if self.source_line and self.marker:
            if self.span is not None and self.span.located():
                out += "\n    --> " + f"{self.span.source}:{self.span.sl} :{self.span.sc} "
            out += "\n    " + self.source_line + "\n    " + self.marker
        return out


class EvalError(Exception):
    def __init__(self, diagnostic: Diagnostic):
        super().__init__(diagnostic.summary())
        self.diagnostic = diagnostic


class SourceContext:
    def __init__(self, lines: list[str]):
        self.lines = lines

    def error(self, message: str, reason: str = "", span: Optional[Span] = None) -> EvalError:
        line = marker = None
        if span is not None and span.located() and 1 <= span.sl <= len(self.lines):
            line = self.lines[span.sl - 1]
            indent = max(0, span.sc - 1)
            marker = "".join("\t" if c == "\t" else " " for c in line[:indent])
            width = max(1, span.ec - span.sc + 1) if span.sl == span.el else 1
            marker += "^" * width
        return EvalError(Diagnostic(message, reason, span, line, marker))


# ---------------------------------------------------------------------------
# Scanner


def normalized_file(path: str) -> str:
    with open(path, "rb") as f:
        raw = f.read()
    # Java/GForth inputs are textual.  latin-1 keeps byte/column correspondence.
    text = raw.decode("utf-8")
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    if text.endswith("\n"):
        text = text[:-1]
    return text


class Scanner:
    def __init__(self, name: str, text: str):
        self.name = name
        self.text = text or ""
        self.lines = self.text.split("\n")
        self.off = 0
        self.line = 1
        self.col = 1
        self.last_line = 1
        self.last_col = 1

    @classmethod
    def from_file(cls, path: str) -> "Scanner":
        return cls(path, normalized_file(path))

    def clone(self) -> "Scanner":
        other = Scanner(self.name, self.text)
        other.lines = self.lines
        other.off, other.line, other.col = self.off, self.line, self.col
        other.last_line, other.last_col = self.last_line, self.last_col
        return other

    def end(self) -> bool:
        return self.off >= len(self.text)

    def ch(self) -> str:
        return self.text[self.off]

    def advance(self) -> None:
        c = self.ch()
        self.last_line, self.last_col = self.line, self.col
        self.off += 1
        if c == "\n":
            self.line += 1
            self.col = 1
        elif c != "\r":
            self.col += 1

    def position_span(self) -> Span:
        return Span(self.name, self.line, self.col, self.line, self.col)

    def skip_whitespace(self) -> None:
        while not self.end() and self.ch().isspace():
            self.advance()

    def skip_line_comment(self) -> None:
        while not self.end() and self.ch() != "\n":
            self.advance()

    def skip_ignorable(self) -> None:
        while not self.end():
            if self.ch().isspace():
                self.advance()
            elif self.ch() == "\\":
                self.skip_line_comment()
            else:
                return

    def read_word(self, stops: str = "", program: bool = False) -> Optional[Word]:
        if self.end():
            return None
        sl, sc = self.line, self.col
        chars: list[str] = []
        el, ec = sl, sc
        while not self.end():
            c = self.ch()
            if c.isspace() or c in stops or (not program and c == "\\"):
                break
            chars.append(c)
            el, ec = self.line, self.col
            self.advance()
        if not chars:
            return None
        return Word("".join(chars), Span(self.name, sl, sc, el, ec))

    def read_quoted(self) -> Optional[Word]:
        if self.end() or self.ch() != '"':
            return None
        sl, sc = self.line, self.col
        self.advance()
        chars: list[str] = []
        escapes = {"n": "\n", "r": "\r", "t": "\t", '"': '"', "\\": "\\"}
        while not self.end():
            c = self.ch()
            if c == '"':
                self.advance()
                return Word("".join(chars), Span(self.name, sl, sc, self.last_line, self.last_col), True)
            if c == "\\":
                self.advance()
                if self.end():
                    return None
                c = self.ch()
                chars.append(escapes.get(c, c))
                self.advance()
            else:
                chars.append(c)
                self.advance()
        return None

    def read_atom(self, stops: str = "") -> Optional[Word]:
        if not self.end() and self.ch() == '"':
            return self.read_quoted()
        return self.read_word(stops)

    def next_word(self, stops: str = "") -> Optional[Word]:
        self.skip_ignorable()
        return self.read_word(stops)

    def next_program_word(self) -> Optional[Word]:
        self.skip_whitespace()
        return self.read_word(program=True)

    def next_line_atoms(self) -> Optional[list[Word]]:
        if self.end():
            return None
        result: list[Word] = []
        while not self.end():
            c = self.ch()
            if c == "\\":
                self.skip_line_comment()
            elif c == "\n":
                self.advance()
                return result
            elif c.isspace():
                self.advance()
            else:
                atom = self.read_atom()
                if atom is not None:
                    result.append(atom)
                else:
                    # Unterminated quote: consume the rest to avoid a loop.
                    while not self.end() and self.ch() != "\n":
                        self.advance()
        return result

    def parse_until(self, delimiter: str) -> Optional[Word]:
        if delimiter == "":
            return Word("", self.position_span())
        sl, sc = self.line, self.col
        chars: list[str] = []
        el, ec = sl, sc
        while not self.end():
            if self.text.startswith(delimiter, self.off):
                for _ in delimiter:
                    self.advance()
                return Word("".join(chars), Span(self.name, sl, sc, el, ec))
            chars.append(self.ch())
            el, ec = self.line, self.col
            self.advance()
        return None


# ---------------------------------------------------------------------------
# Type system


class TypeSystem(SourceContext):
    def __init__(self, path: str):
        sc = Scanner.from_file(path)
        super().__init__(sc.lines)
        self.path = path
        self.types: list[str] = []
        self.aliases: dict[str, int] = {}
        self.scanners: dict[str, str] = {}
        pending_rel: list[tuple[str, str, Span]] = []
        pending_scanners: list[tuple[str, str, Span]] = []

        while True:
            line = sc.next_line_atoms()
            if line is None:
                break
            if not line:
                continue
            head = line[0]
            directive = head.text.upper()
            span = Span.cover(head.span, line[-1].span)
            if directive == "TYPE":
                if len(line) < 2:
                    raise self.error("Type definition is too short", span=head.span)
                idx = len(self.types)
                self.types.append(line[1].text)
                for token in line[1:]:
                    if token.text in self.aliases:
                        raise self.error("Duplicate type name " + token.text, span=token.span)
                    self.aliases[token.text] = idx
            elif directive == "REL":
                if len(line) != 4 or line[2].text != "<":
                    raise self.error("Malformed relation", span=head.span)
                pending_rel.append((line[1].text, line[3].text, span))
            elif directive == "SCANNER":
                if len(line) != 3:
                    raise self.error("Malformed scanner definition", span=head.span)
                pending_scanners.append((line[1].text, line[2].text, span))
            else:
                raise self.error("Unknown directive " + head.text, span=head.span)

        n = len(self.types)
        self.rel = [[0] * n for _ in range(n)]
        for sub, sup, span in pending_rel:
            if sub not in self.aliases:
                raise self.error("Unknown type " + sub, span=span)
            if sup not in self.aliases:
                raise self.error("Unknown type " + sup, span=span)
            i, j = self.aliases[sub], self.aliases[sup]
            if i != j:
                self.rel[i][j] = 1
        for name, delimiter, span in pending_scanners:
            key = canon(name)
            if not key:
                raise self.error("Empty scanner name", span=span)
            if not delimiter:
                raise self.error("Empty scanner delimiter", span=span)
            if key in self.scanners:
                raise self.error("Duplicate scanner name " + name, span=span)
            self.scanners[key] = delimiter
        self._normalize()

    def _normalize(self) -> None:
        n = len(self.types)
        for i in range(n):
            for j in range(n):
                if self.rel[i][j] == 0:
                    if i == j:
                        self.rel[i][j] = 3
                    if self.rel[j][i] == 1:
                        self.rel[i][j] = 2
                    if self.rel[j][i] == 2:
                        self.rel[i][j] = 1
                    if self.rel[j][i] == 3:
                        self.rel[i][j] = 3
                elif self.rel[i][j] == 1:
                    if self.rel[j][i] == 0:
                        self.rel[j][i] = 2
                    elif self.rel[j][i] == 1:
                        self.rel[i][j] = self.rel[j][i] = 3
                    elif self.rel[j][i] == 3:
                        self.rel[i][j] = 3
                elif self.rel[i][j] == 2:
                    if self.rel[j][i] == 0:
                        self.rel[j][i] = 1
                    elif self.rel[j][i] == 2:
                        self.rel[i][j] = self.rel[j][i] = 3
                    elif self.rel[j][i] == 3:
                        self.rel[i][j] = 3
                elif self.rel[i][j] == 3:
                    self.rel[j][i] = 3
        for k in range(n):
            for i in range(n):
                for j in range(n):
                    if 0 < self.rel[i][k] < 4 and self.rel[i][k] == self.rel[k][j]:
                        self.rel[i][j] = self.rel[i][k]

    def relation(self, a: Optional[str], b: Optional[str]) -> int:
        if a not in self.aliases or b not in self.aliases:
            return 0
        return self.rel[self.aliases[a]][self.aliases[b]]


# ---------------------------------------------------------------------------
# Stack effects and calculus


@dataclass
class Symbol:
    type: str
    pos: int = 0
    explicit: bool = False

    def clone(self) -> "Symbol":
        return Symbol(self.type, self.pos, self.explicit)

    def key(self) -> tuple[str, int]:
        return self.type, self.pos

    def __str__(self) -> str:
        return f"{self.type}[{self.pos}] " if self.pos > 0 else self.type + " "


def clone_vec(vec: list[Symbol]) -> list[Symbol]:
    return [x.clone() for x in vec]


def subst_vec(vec: list[Symbol], old: Symbol, new: Symbol) -> int:
    count = 0
    key = old.key()
    for i, sym in enumerate(vec):
        if sym.key() == key:
            vec[i] = new.clone()
            count += 1
    return count


PARSE_NONE, PARSE_UNTIL, PARSE_WORD, PARSE_DEFINITION = "", "UNTIL", "WORD", "DEFINITION"
DEFINE_NONE, DEFINE_COLON, DEFINE_CONSTANT, DEFINE_VARIABLE = "", "COLON", "CONSTANT", "VARIABLE"
STATE_ANY, STATE_INTERPRET, STATE_COMPILE = "", "INTERPRET", "COMPILE"


@dataclass
class Spec:
    left: list[Symbol] = field(default_factory=list)
    right: list[Symbol] = field(default_factory=list)
    parse_string: str = ""
    parse_mode: str = PARSE_NONE
    define_mode: str = DEFINE_NONE
    control_mode: str = ""
    immediate: bool = False
    state_mode: str = STATE_ANY
    span: Optional[Span] = None
    origin: str = ""
    max_pos: int = 0

    def clone(self) -> "Spec":
        return Spec(clone_vec(self.left), clone_vec(self.right), self.parse_string,
                    self.parse_mode, self.define_mode, self.control_mode,
                    self.immediate, self.state_mode, self.span, self.origin,
                    self.max_pos)

    def runtime_clone(self) -> "Spec":
        return Spec(clone_vec(self.left), clone_vec(self.right))

    def substitute(self, old: Symbol, new: Symbol) -> int:
        return subst_vec(self.left, old, new) + subst_vec(self.right, old, new)

    def find_max(self) -> int:
        self.max_pos = max((s.pos for s in self.left + self.right), default=0)
        return self.max_pos

    def increment_wild(self, amount: int) -> None:
        self.left, self.right = clone_vec(self.left), clone_vec(self.right)
        for s in self.left + self.right:
            if s.pos > 0:
                s.pos += amount
        self.max_pos = self.find_max() + amount
        for s in self.left + self.right:
            if s.pos == 0:
                self.max_pos += 1
                s.pos = self.max_pos

    def defines_word(self) -> bool:
        return bool(self.define_mode)

    def consumes_until(self) -> bool:
        return self.parse_mode == PARSE_UNTIL and bool(self.parse_string)

    def consumes_word(self) -> bool:
        return self.parse_mode == PARSE_WORD

    def is_control(self) -> bool:
        return bool(self.control_mode)

    def is_immediate(self) -> bool:
        return (self.immediate or bool(self.parse_mode) or bool(self.define_mode) or
                (bool(self.control_mode) and canon(self.control_mode) != "INDEX"))

    def effective_state(self) -> str:
        if self.state_mode:
            return self.state_mode
        if self.define_mode:
            return STATE_INTERPRET
        if self.control_mode:
            return STATE_COMPILE
        return STATE_ANY

    def __str__(self) -> str:
        return "( " + "".join(map(str, self.left)) + " --  " + "".join(map(str, self.right)) + ") "


@dataclass
class Conflict:
    prefix: Spec
    incoming: Spec
    actual: Symbol
    expected: Symbol


class EffectList(list[Spec]):
    def __init__(self, values=()):
        super().__init__(values)
        self.cmax = 0
        self.conflict: Optional[Conflict] = None

    def clone(self) -> "EffectList":
        return EffectList(x.clone() for x in self)

    def substitute(self, old: Symbol, new: Symbol) -> None:
        for spec in self:
            spec.substitute(old, new)

    def multiply(self, s1: Spec, s2: Spec, ts: TypeSystem) -> Optional[Spec]:
        while True:
            rleft = clone_vec(s1.left)
            rright = clone_vec(s2.right)
            if not s1.right:
                return Spec(clone_vec(s2.left) + rleft, rright)
            if not s2.left:
                return Spec(rleft, clone_vec(s1.right) + rright)

            m1, m2 = s1.right[-1], s2.left[-1]
            rel = ts.relation(m1.type, m2.type)
            if rel == 0:
                self.conflict = Conflict(s1.clone(), s2.clone(), m1.clone(), m2.clone())
                return None
            typ = m2.type if rel == 2 else m1.type
            self.cmax += 1
            fresh = Symbol(typ, self.cmax, m1.explicit or m2.explicit)
            r1rs, r2ls = clone_vec(s1.right), clone_vec(s2.left)
            for vec in (r1rs, r2ls, rleft, rright):
                subst_vec(vec, m1, fresh)
                subst_vec(vec, m2, fresh)
            self.substitute(m1, fresh)
            self.substitute(m2, fresh)
            r1rs.pop()
            r2ls.pop()
            s1 = Spec(rleft, r1rs, span=s1.span, origin=s1.origin)
            s2 = Spec(r2ls, rright, span=s2.span, origin=s2.origin)

    def evaluate(self, ts: TypeSystem) -> Optional[Spec]:
        self.cmax = 0
        self.conflict = None
        for spec in self:
            spec.increment_wild(self.cmax)
            self.cmax = spec.find_max()
        result = Spec()
        for spec in self:
            result = self.multiply(result, spec, ts)
            if result is None:
                return None
        return self.normalize(result)

    def normalize(self, result: Spec) -> Spec:
        maximum = result.find_max()
        for spec in self:
            maximum = max(maximum, spec.find_max())
        maximum += 1
        for spec in self:
            spec.increment_wild(maximum)
        result.increment_wild(maximum)

        # key -> [occurrences, result occurrences, assigned index, explicit]
        table: dict[tuple[str, int], list] = {}

        def touch(sym: Symbol, is_result: bool = False) -> None:
            info = table.setdefault(sym.key(), [0, 0, 0, False])
            info[0] += 1
            if is_result:
                info[1] += 1
            info[3] = info[3] or sym.explicit

        for sym in result.left + result.right:
            touch(sym, True)
        for spec in self:
            for sym in spec.left + spec.right:
                touch(sym)

        def needs(info: list) -> bool:
            return info[1] > 1 and (info[3] or info[0] > 2)

        next_index = 0

        def assign(sym: Symbol) -> None:
            nonlocal next_index
            info = table[sym.key()]
            if needs(info) and info[2] == 0:
                next_index += 1
                info[2] = next_index

        for sym in reversed(result.left):
            assign(sym)
        for sym in result.right:
            assign(sym)
        for spec in self:
            for sym in reversed(spec.left):
                assign(sym)
            for sym in spec.right:
                assign(sym)

        for key, info in list(table.items()):
            old = Symbol(key[0], key[1])
            new = Symbol(key[0], info[2] if needs(info) else 0, info[3])
            result.substitute(old, new)
            self.substitute(old, new)
        return result


def normalize_one(spec: Spec) -> Spec:
    seq = EffectList([spec])
    return seq.normalize(spec)


def merged_symbol(a: Symbol, b: Symbol, relation: int, pos: int, output: bool) -> Optional[Symbol]:
    if relation == 0:
        return None
    second_wins = relation == 1 if output else relation == 2
    winner = b if second_wins else a
    return Symbol(winner.type, pos, a.explicit or b.explicit)


def unify_specs(s1: Spec, s2: Optional[Spec], ts: TypeSystem) -> Optional[Spec]:
    if s2 is None:
        return None
    p1, p2, q1, q2 = len(s1.left), len(s1.right), len(s2.left), len(s2.right)
    if p1 < q1 or p2 < q2:
        return None
    lo, ro = p1 - q1, p2 - q2
    if lo != ro:
        return None
    result = s1.clone()
    result.increment_wild(0)
    maxpos = result.find_max()
    tc = s2.clone()
    tc.increment_wild(maxpos)
    maxpos = max(maxpos, tc.find_max())
    for i in range(q1):
        a, b = result.left[i + lo], tc.left[i]
        maxpos += 1
        fresh = merged_symbol(a, b, ts.relation(a.type, b.type), maxpos, False)
        if fresh is None:
            return None
        for target in (result, tc):
            target.substitute(a, fresh)
            target.substitute(b, fresh)

    pairs: list[tuple[Symbol, Symbol, Symbol]] = []
    for i in range(p2):
        a = result.right[i]
        b = result.left[i] if i < ro else tc.right[i - ro]
        fresh: Optional[Symbol] = None
        if a.key() == b.key():
            fresh = a
        else:
            for pa, pb, pm in pairs:
                if pa.key() == a.key() and pb.key() == b.key():
                    fresh = pm
                    break
            if fresh is None:
                maxpos += 1
                fresh = merged_symbol(a, b, ts.relation(a.type, b.type), maxpos, True)
                if fresh is None:
                    return None
        pairs.append((a, b, fresh))
        result.right[i] = fresh.clone()
    result.max_pos = maxpos
    return normalize_one(result)


def glb_specs(a: Spec, b: Optional[Spec], ts: TypeSystem) -> Optional[Spec]:
    if b is None or len(a.left) - len(b.left) != len(a.right) - len(b.right):
        return None
    return unify_specs(a, b, ts) if len(a.left) >= len(b.left) else unify_specs(b, a, ts)


def pi_star(spec: Spec, ts: TypeSystem) -> Optional[Spec]:
    squared = EffectList([spec.clone(), spec.clone()]).evaluate(ts)
    return glb_specs(spec, squared, ts) if squared is not None else None


# ---------------------------------------------------------------------------
# Specification set and declarative control structures


@dataclass
class Expr:
    kind: str
    a: object = None
    b: object = None


@dataclass
class Structure:
    name: str
    opening: str
    boundaries: list[str]
    optional: list[bool]
    close: str
    segments: list[str]
    meaning: Expr

    def segment_index(self, name: str) -> int:
        key = canonical_segment(name)
        for i, item in enumerate(self.segments):
            if canonical_segment(item) == key:
                return i
        return -1

    def signature(self) -> tuple:
        return self.opening, tuple(self.boundaries), tuple(self.optional), self.close


def canonical_segment(text: str) -> str:
    return "".join(c.upper() if c.isalnum() and ord(c) < 128 else "_" for c in text)


def metasymbol(text: str) -> Optional[str]:
    if len(text) >= 3 and text.startswith("<") and text.endswith(">"):
        return canonical_segment(text[1:-1])
    return None


def control_tokenize(text: str) -> list[str]:
    out: list[str] = []
    i = 0
    while i < len(text):
        if text[i].isspace():
            i += 1
        elif text[i] in "[]":
            out.append(text[i]); i += 1
        elif text[i] == "<":
            j = text.find(">", i + 1)
            if j < 0:
                raise EvalError(Diagnostic("Unclosed metasymbol"))
            out.append(text[i:j + 1]); i = j + 1
        else:
            j = i
            while j < len(text) and not text[j].isspace() and text[j] not in "[]<":
                j += 1
            out.append(text[i:j]); i = j
    return out


def seq_expr(parts: list[Expr]) -> Expr:
    if not parts:
        return Expr("EMPTY")
    result = parts[0]
    for item in parts[1:]:
        result = Expr("SEQ", result, item)
    return result


def atom_expr(token: str) -> Expr:
    seg = metasymbol(token)
    return Expr("SEGMENT", seg) if seg is not None else Expr("CONTROL", canon(token))


def parse_effect_line(text: str) -> Expr:
    toks = control_tokenize(text.strip())
    if not toks:
        return Expr("EMPTY")
    if canon(toks[0]) == "EITHER":
        if len(toks) < 3:
            raise EvalError(Diagnostic("EITHER requires two alternatives"))
        result = atom_expr(toks[1])
        for tok in toks[2:]:
            result = Expr("GLB", result, atom_expr(tok))
        return result
    if canon(toks[0]) == "REPEAT":
        if len(toks) < 2:
            raise EvalError(Diagnostic("REPEAT requires a repeated effect"))
        return Expr("STAR", seq_expr([atom_expr(t) for t in toks[1:]]))
    return seq_expr([atom_expr(t) for t in toks])


def parse_control_meaning(text: str) -> Expr:
    return seq_expr([e for line in text.split("\n")
                     if (e := parse_effect_line(line)).kind != "EMPTY"])


def parse_control_syntax(text: str) -> Structure:
    toks = control_tokenize(text)
    if len(toks) < 3:
        raise EvalError(Diagnostic("Malformed SYNTAX clause"))
    opening = canon(toks[0]); idx = 1
    first = metasymbol(toks[idx])
    if first is None:
        raise EvalError(Diagnostic("Missing first segment in SYNTAX"))
    segments = [first]; idx += 1
    bounds: list[str] = []; optional: list[bool] = []
    while idx < len(toks) - 1:
        opt = toks[idx] == "["
        if opt: idx += 1
        if idx >= len(toks) - 1:
            raise EvalError(Diagnostic("Missing boundary word in SYNTAX"))
        role = canon(toks[idx]); idx += 1
        if idx >= len(toks) - 1 or metasymbol(toks[idx]) is None:
            raise EvalError(Diagnostic("Missing segment metasymbol in SYNTAX"))
        segment = metasymbol(toks[idx]); idx += 1
        if opt:
            if idx >= len(toks) - 1 or toks[idx] != "]":
                raise EvalError(Diagnostic("Missing ] in SYNTAX"))
            idx += 1
        bounds.append(role); optional.append(opt); segments.append(segment or "")
    if idx >= len(toks):
        raise EvalError(Diagnostic("Missing closing control word in SYNTAX"))
    return Structure("", opening, bounds, optional, canon(toks[idx]), segments, Expr("EMPTY"))


class SpecSet(SourceContext):
    def __init__(self, path: str, ts: TypeSystem):
        sc = Scanner.from_file(path)
        super().__init__(sc.lines)
        self.path = path
        self.words: dict[str, tuple[str, Spec]] = {}
        self.literals: dict[str, Spec] = {}
        self.structures: list[Structure] = []
        pending: Optional[list[Word]] = None
        while True:
            if pending is not None:
                line, pending = pending, None
            else:
                line = sc.next_line_atoms()
            if line is None:
                break
            if not line:
                continue
            head = line[0]
            directive = canon(head.text.rstrip(":"))
            if directive == "LITERAL":
                self._parse_literal(line, ts)
            elif directive == "SYNTAX":
                pending = self._parse_syntax_block(sc, head, line)
            else:
                self._parse_word(line, ts)
        self._install_builtins()

    def find(self, name: str) -> Optional[Spec]:
        entry = self.words.get(canon(name))
        return entry[1] if entry else None

    def set_word(self, surface: str, spec: Spec) -> None:
        self.words[canon(surface)] = (surface, spec)

    def add_word(self, surface: str, spec: Spec, span: Optional[Span]) -> None:
        key = canon(surface)
        if key in self.words:
            raise self.error("Duplicate specification for " + surface, span=span)
        self.words[key] = (surface, spec)

    def role_spec(self, role: str) -> Optional[Spec]:
        key = canon(role)
        for _, spec in self.words.values():
            if spec.control_mode and canon(spec.control_mode) == key:
                return spec
        return None

    def opening_structures(self, role: str) -> list[Structure]:
        key = canon(role)
        return [x for x in self.structures if x.opening == key]

    def _parse_symbol(self, text: str, span: Optional[Span], ts: TypeSystem) -> Symbol:
        match = re.fullmatch(r"(.*?)\[(\d+)\]", text)
        if "[" in text and match is None:
            raise self.error("Malformed type symbol " + text, span=span)
        if match:
            typ, pos, explicit = match.group(1), int(match.group(2)), True
        else:
            typ, pos, explicit = text, 0, False
        if typ not in ts.aliases:
            raise self.error("Unknown type " + typ, span=span)
        return Symbol(typ, pos, explicit)

    def parse_spec_body(self, body: str, ts: TypeSystem, span: Optional[Span]) -> Spec:
        at = body.find("--")
        if at < 0:
            raise self.error("Missing -- in stack effect", span=span)
        left = [self._parse_symbol(x, span, ts) for x in body[:at].split()]
        right = [self._parse_symbol(x, span, ts) for x in body[at + 2:].split()]
        spec = Spec(left, right)
        spec.find_max()
        return spec

    def _resolve_delimiter(self, tok: Word, ts: TypeSystem) -> str:
        if tok.quoted:
            return tok.text
        value = ts.scanners.get(canon(tok.text))
        if value is None:
            raise self.error("Unknown scanner delimiter " + tok.text, span=tok.span)
        return value

    def _parse_word(self, line: list[Word], ts: TypeSystem) -> None:
        word = line[0]
        open_i = next((i for i in range(1, len(line)) if not line[i].quoted and line[i].text == "("), -1)
        if open_i < 0:
            raise self.error("Missing ( in specification", span=word.span)
        close_i = next((i for i in range(open_i, len(line)) if not line[i].quoted and line[i].text == ")"), -1)
        if close_i < 0:
            raise self.error("Missing ) in specification", span=word.span)
        body = " ".join(x.text for x in line[open_i + 1:close_i])
        span = Span.cover(word.span, line[close_i].span)
        spec = self.parse_spec_body(body, ts, span)
        idx = 1; define_seen = False
        starters = {"PARSE", "DEFINE", "CONTROL", "STATE", "CONTEXT", "IMMEDIATE", "SCAN", "("}
        while idx < open_i:
            tok = line[idx]; key = canon(tok.text)
            if key == "PARSE":
                idx += 1
                if idx >= open_i:
                    raise self.error("Missing parser mode", span=tok.span)
                mode = canon(line[idx].text); idx += 1
                modes = {"UNTIL": PARSE_UNTIL, "WORD": PARSE_WORD, "DEFINITION": PARSE_DEFINITION}
                if mode not in modes:
                    raise self.error("Unknown parser mode", span=line[idx - 1].span)
                spec.parse_mode = modes[mode]
                if spec.parse_mode in (PARSE_UNTIL, PARSE_DEFINITION):
                    if idx >= open_i:
                        raise self.error("Missing parser delimiter", span=tok.span)
                    spec.parse_string = self._resolve_delimiter(line[idx], ts); idx += 1
            elif key == "DEFINE":
                define_seen = True; idx += 1
                if idx < open_i and canon(line[idx].text) not in starters:
                    mode = canon(line[idx].text); idx += 1
                    modes = {"COLON": DEFINE_COLON, "CONSTANT": DEFINE_CONSTANT, "VARIABLE": DEFINE_VARIABLE}
                    if mode not in modes:
                        raise self.error("Unknown defining mode", span=line[idx - 1].span)
                    spec.define_mode = modes[mode]
            elif key == "CONTROL":
                idx += 1
                if idx >= open_i:
                    raise self.error("Missing control mode", span=tok.span)
                spec.control_mode = canon(line[idx].text); idx += 1
            elif key in ("STATE", "CONTEXT"):
                idx += 1
                if idx >= open_i:
                    raise self.error("Missing state mode", span=tok.span)
                mode = canon(line[idx].text); idx += 1
                if mode in ("INTERPRET", "OUTER"):
                    spec.state_mode = STATE_INTERPRET
                elif mode in ("COMPILE", "DEFINITION"):
                    spec.state_mode = STATE_COMPILE
                else:
                    raise self.error("Unknown state mode", span=line[idx - 1].span)
            elif key == "IMMEDIATE":
                spec.immediate = True; idx += 1
            elif key == "SCAN":
                idx += 1
                if idx >= open_i:
                    raise self.error("Missing scanner delimiter", span=tok.span)
                spec.parse_mode = PARSE_UNTIL
                spec.parse_string = self._resolve_delimiter(line[idx], ts); idx += 1
            else:
                if spec.parse_mode:
                    raise self.error("Duplicate scanner/parser clause", span=tok.span)
                spec.parse_mode = PARSE_UNTIL
                spec.parse_string = self._resolve_delimiter(tok, ts); idx += 1
        if define_seen and not spec.define_mode:
            if len(spec.left) == 1 and not spec.right:
                spec.define_mode = DEFINE_CONSTANT
            elif not spec.left and len(spec.right) == 1:
                spec.define_mode = DEFINE_VARIABLE
            else:
                raise self.error("DEFINE without mode requires ( x -- ) or ( -- y )", span=word.span)
        if spec.define_mode == DEFINE_COLON and (spec.left or spec.right):
            raise self.error("DEFINE COLON must have stack effect ( -- )", span=word.span)
        if spec.define_mode == DEFINE_CONSTANT and (len(spec.left) != 1 or spec.right):
            raise self.error("DEFINE CONSTANT must have stack effect ( x -- )", span=word.span)
        if spec.define_mode == DEFINE_VARIABLE and (spec.left or len(spec.right) != 1):
            raise self.error("DEFINE VARIABLE must have stack effect ( -- y )", span=word.span)
        spec.span, spec.origin = span, word.text
        self.add_word(word.text, spec, word.span)

    def _parse_literal(self, line: list[Word], ts: TypeSystem) -> None:
        if len(line) < 4:
            raise self.error("Malformed literal specification", span=line[0].span)
        kind = line[1]
        if line[2].quoted or line[2].text != "(":
            raise self.error("Missing ( in literal specification", span=kind.span)
        close = next((i for i in range(2, len(line)) if not line[i].quoted and line[i].text == ")"), -1)
        if close < 0:
            raise self.error("Missing ) in literal specification", span=kind.span)
        spec = self.parse_spec_body(" ".join(x.text for x in line[3:close]), ts,
                                    Span.cover(kind.span, line[close].span))
        if spec.left:
            raise self.error("LITERAL must not consume stack input", span=kind.span)
        key = canon(kind.text)
        if key in self.literals:
            raise self.error("Duplicate specification for " + kind.text, span=kind.span)
        self.literals[key] = spec

    def _parse_syntax_block(self, sc: Scanner, head: Word, line: list[Word]) -> Optional[list[Word]]:
        base = head.span.sc
        syntax_lines: list[str] = []
        effect_lines: list[str] = []
        if len(line) > 1:
            syntax_lines.append(" ".join(x.text for x in line[1:]))
        have_effect = False
        pending = None
        while True:
            nxt = sc.next_line_atoms()
            if nxt is None:
                break
            if not nxt:
                continue
            if nxt[0].span.sc <= base:
                pending = nxt
                break
            first = canon(nxt[0].text.rstrip(":"))
            if first == "EFFECT":
                have_effect = True
                if len(nxt) > 1:
                    effect_lines.append(" ".join(x.text for x in nxt[1:]))
            elif have_effect:
                effect_lines.append(" ".join(x.text for x in nxt))
            else:
                syntax_lines.append(" ".join(x.text for x in nxt))
        st = parse_control_syntax("\n".join(syntax_lines))
        st.meaning = parse_control_meaning("\n".join(effect_lines))
        if st.signature() not in [x.signature() for x in self.structures]:
            self.structures.append(st)
        return pending

    def _add_structure(self, st: Structure) -> None:
        if st.signature() not in [x.signature() for x in self.structures]:
            self.structures.append(st)

    def _install_builtins(self) -> None:
        self._add_structure(Structure("IF", "IF", ["ELSE"], [True], "FI",
            [canonical_segment("THEN_BRANCH"), canonical_segment("ELSE_BRANCH")],
            seq_expr([Expr("CONTROL", "IF"), Expr("GLB", Expr("SEGMENT", canonical_segment("THEN_BRANCH")), Expr("SEGMENT", canonical_segment("ELSE_BRANCH")))])))
        self._add_structure(Structure("WHILE", "BEGIN", ["WHILE"], [False], "REPEAT",
            [canonical_segment("LOOP_PREFIX"), canonical_segment("LOOP_BODY")],
            seq_expr([Expr("STAR", seq_expr([Expr("SEGMENT", canonical_segment("LOOP_PREFIX")), Expr("CONTROL", "WHILE")])),
                      Expr("STAR", Expr("SEGMENT", canonical_segment("LOOP_BODY")))])))
        self._add_structure(Structure("AGAIN", "BEGIN", [], [], "AGAIN", [canonical_segment("LOOP_BODY")],
                                      Expr("STAR", Expr("SEGMENT", canonical_segment("LOOP_BODY")))))
        self._add_structure(Structure("UNTIL", "BEGIN", [], [], "UNTIL", [canonical_segment("LOOP_BODY")],
                                      Expr("STAR", seq_expr([Expr("SEGMENT", canonical_segment("LOOP_BODY")), Expr("CONTROL", "UNTIL")]))))
        self._add_structure(Structure("DO", "DO", [], [], "LOOP", [canonical_segment("LOOP_BODY")],
                                      seq_expr([Expr("CONTROL", "DO"), Expr("STAR", Expr("SEGMENT", canonical_segment("LOOP_BODY")))])))


# ---------------------------------------------------------------------------
# Program parser/evaluator


@dataclass
class Program:
    name: str
    text: str
    lines: list[str]
    words: list[str] = field(default_factory=list)
    spans: list[Optional[Span]] = field(default_factory=list)
    specs: EffectList = field(default_factory=EffectList)
    diagnostics: list[Diagnostic] = field(default_factory=list)
    logs: list[str] = field(default_factory=list)

    def add(self, word: str, span: Optional[Span], spec: Spec) -> None:
        spec.span, spec.origin = span, word
        self.words.append(word); self.spans.append(span); self.specs.append(spec)

    def discard_last(self) -> None:
        if self.words: self.words.pop()
        if self.spans: self.spans.pop()
        if self.specs: self.specs.pop()


class ProgramParser(SourceContext):
    def __init__(self, name: str, text: str, ts: TypeSystem, ss: SpecSet):
        self.name, self.text, self.ts, self.ss = name, text, ts, ss
        super().__init__(text.split("\n"))
        self.current_token: Optional[Word] = None
        self.locals: dict[str, Spec] = {}
        self.local_pos = 0
        self.local_seed: Optional[Spec] = None
        self.local_seed_index = 0
        self.program = Program(name, text, self.lines)

    def runtime_control_spec(self, role: str, span: Optional[Span]) -> Spec:
        found = self.ss.role_spec(role)
        if found is not None:
            return found.runtime_clone()
        body = "n[2] n[1] --" if canon(role) == "DO" else ("-- n" if canon(role) == "INDEX" else "flag --")
        return self.ss.parse_spec_body(body, self.ts, span)

    def resolve(self, tok: Word, do_depth: int) -> Spec:
        key = canon(tok.text)
        if key in self.locals:
            return self.locals[key].runtime_clone()
        if key == "RECURSE":
            if self.local_seed is None:
                raise self.error("No specification found for recursive word", span=tok.span)
            return self.local_seed.runtime_clone()
        spec = self.ss.find(tok.text)
        if spec is not None:
            if spec.is_control():
                if canon(spec.control_mode) == "INDEX" and do_depth > 0:
                    return self.runtime_control_spec(spec.control_mode, tok.span)
                raise self.error("Unexpected control word", span=tok.span)
            return spec.runtime_clone()
        literal = None
        if re.fullmatch(r"[+-]?\d+\.", tok.text):
            literal = self.ss.literals.get("DOUBLE")
            if literal is None:
                raise self.error("No literal specification for double literal", span=tok.span)
        elif re.fullmatch(r"[+-]?\d+", tok.text):
            literal = self.ss.literals.get("INTEGER")
            if literal is None:
                raise self.error("No literal specification for integer literal", span=tok.span)
        if literal is not None:
            return literal.runtime_clone()
        raise self.error("No specification found for " + tok.text, span=tok.span)

    def consume_parser(self, tok: Word, spec: Spec, sc: Scanner) -> Optional[Span]:
        if spec.consumes_word():
            nxt = sc.next_program_word()
            if nxt is None:
                raise self.error("Missing word after parser word", span=tok.span)
            return Span.cover(tok.span, nxt.span)
        if spec.consumes_until():
            sc.skip_whitespace()
            parsed = sc.parse_until(spec.parse_string)
            if parsed is None:
                raise self.error("Missing closing delimiter for parser word", span=tok.span)
            return Span.cover(tok.span, parsed.span)
        return tok.span

    def evaluate_sequence(self, seq: EffectList, context: str) -> Spec:
        work = seq.clone()
        result = work.evaluate(self.ts)
        if result is None:
            span = self.current_token.span if self.current_token else None
            raise self.error("Type clash in " + context, span=span)
        return result

    def add_checked(self, seq: EffectList, tok: Word, spec: Spec, context: str) -> None:
        spec.span, spec.origin = tok.span, tok.text
        seq.append(spec)
        try:
            self.evaluate_sequence(seq, context)
        except EvalError:
            seq.pop()
            raise

    def local_declaration(self, tok: Word, spec: Optional[Spec]) -> bool:
        return (spec is not None and canon(tok.text) in {"{", "{:"}
                and spec.consumes_until())

    def local_names(self, text: str) -> list[str]:
        sc = Scanner("<locals>", text)
        names: list[str] = []
        while (tok := sc.next_word()) is not None:
            if canon(tok.text) == "--": break
            if canon(tok.text) != "|": names.append(canon(tok.text))
        return names

    def local_input_count(self, text: str) -> int:
        sc = Scanner("<locals-inputs>", text)
        count = 0
        while (tok := sc.next_word()) is not None:
            if canon(tok.text) in {"--", "|"}: break
            count += 1
        return count

    def next_local_symbol(self) -> Symbol:
        if self.local_seed is not None and self.local_seed_index < len(self.local_seed.left):
            sym = self.local_seed.left[self.local_seed_index].clone()
            self.local_seed_index += 1
            return sym
        self.local_pos += 1
        return Symbol("x", self.local_pos)

    def consume_locals(self, tok: Word, spec: Spec, sc: Scanner) -> tuple[Span, Spec]:
        sc.skip_whitespace()
        parsed = sc.parse_until(spec.parse_string)
        if parsed is None:
            raise self.error("Missing closing delimiter for parser word", span=tok.span)
        names = self.local_names(parsed.text)
        input_count = self.local_input_count(parsed.text)
        left: list[Symbol] = []
        for index, name in enumerate(names):
            sym = self.next_local_symbol()
            if index < input_count:
                left.append(sym.clone())
            self.locals[name] = Spec([], [sym.clone()])
        bind = Spec(left, [])
        bind.find_max()
        return Span.cover(tok.span, parsed.span), bind

    def eval_structure_expr(self, expr: Expr, st: Structure, segments: list[Spec], span: Span) -> Spec:
        if expr.kind == "EMPTY": return Spec()
        if expr.kind == "SEGMENT":
            idx = st.segment_index(str(expr.a))
            if idx < 0: raise self.error("Unknown structure segment", span=span)
            return segments[idx].clone() if idx < len(segments) else Spec()
        if expr.kind == "CONTROL":
            return self.runtime_control_spec(str(expr.a), span)
        if expr.kind == "SEQ":
            a = self.eval_structure_expr(expr.a, st, segments, span)
            b = self.eval_structure_expr(expr.b, st, segments, span)
            return self.evaluate_sequence(EffectList([a, b]), self.structure_label(st))
        if expr.kind == "GLB":
            a = self.eval_structure_expr(expr.a, st, segments, span)
            b = self.eval_structure_expr(expr.b, st, segments, span)
            result = glb_specs(a, b, self.ts)
            if result is None: raise self.error("Non-comparable control alternatives", span=span)
            return result
        inner = self.eval_structure_expr(expr.a, st, segments, span)
        result = pi_star(inner, self.ts)
        if result is None: raise self.error("Non-idempotent repeated effect", span=span)
        return result

    @staticmethod
    def structure_label(st: Structure) -> str:
        return st.opening + "".join("..." + x for x in st.boundaries) + "..." + st.close

    @staticmethod
    def close_match(role: str, stage: int, st: Structure) -> bool:
        if canon(role) != st.close: return False
        return all(st.optional[i] for i in range(stage, len(st.boundaries)))

    def parse_structure(self, opener: Word, spec: Spec, defname: str, sc: Scanner, do_depth: int) -> Spec:
        candidates = self.ss.opening_structures(spec.control_mode)
        if not candidates: raise self.error("Unknown control structure", span=opener.span)
        segments: list[EffectList] = []
        current = EffectList()
        stage = 0
        inner_depth = do_depth + 1 if canon(spec.control_mode) == "DO" else do_depth
        while True:
            tok = sc.next_program_word(); self.current_token = tok
            if tok is None: raise self.error("Missing control terminator in definition", span=opener.span)
            tspec = None if canon(tok.text) in self.locals else self.ss.find(tok.text)
            if tspec is not None and tspec.is_control():
                role = canon(tspec.control_mode)
                by_boundary = [st for st in candidates if stage < len(st.boundaries) and st.boundaries[stage] == role]
                by_close = [st for st in candidates if self.close_match(role, stage, st)]
                if by_close and not by_boundary:
                    segments.append(current)
                    st = by_close[0]
                    effects = [self.evaluate_sequence(x, defname) for x in segments]
                    span = Span.cover(opener.span, tok.span)
                    return self.eval_structure_expr(st.meaning, st, effects, span)
                if by_boundary and not by_close:
                    segments.append(current); current = EffectList(); candidates = by_boundary; stage += 1
                    continue
                if self.ss.opening_structures(role):
                    effect = self.parse_structure(tok, tspec, defname, sc, inner_depth)
                elif role == "INDEX" and inner_depth > 0:
                    effect = self.runtime_control_spec(role, tok.span)
                else:
                    raise self.error("Unexpected control word in definition", span=tok.span)
                self.add_checked(current, tok, effect, defname)
            elif tspec is not None and tspec.is_immediate():
                if tspec.defines_word():
                    raise self.error("Defining words are not supported inside definitions", span=tok.span)
                if self.local_declaration(tok, tspec):
                    span, effect = self.consume_locals(tok, tspec, sc)
                    use = Word(tok.text, span)
                else:
                    span = self.consume_parser(tok, tspec, sc)
                    effect = tspec.runtime_clone(); use = Word(tok.text, span or tok.span)
                self.add_checked(current, use, effect, defname)
            else:
                effect = self.resolve(tok, inner_depth)
                self.add_checked(current, tok, effect, defname)

    def parse_definition_seq(self, defname: str, sc: Scanner, close_role: str = "END") -> Spec:
        seq = EffectList()
        while True:
            tok = sc.next_program_word(); self.current_token = tok
            if tok is None: raise self.error("Missing end of definition")
            tspec = None if canon(tok.text) in self.locals else self.ss.find(tok.text)
            if tspec is not None and tspec.is_control():
                role = canon(tspec.control_mode)
                if role == canon(close_role):
                    return self.evaluate_sequence(seq, defname)
                if self.ss.opening_structures(role):
                    effect = self.parse_structure(tok, tspec, defname, sc, 0)
                    self.add_checked(seq, tok, effect, defname)
                elif role == "INDEX":
                    raise self.error("Unexpected control word in definition", span=tok.span)
                else:
                    raise self.error("Unexpected control word in definition", span=tok.span)
            elif tspec is not None and tspec.is_immediate():
                if tspec.defines_word():
                    raise self.error("Defining words are not supported inside definitions", span=tok.span)
                if self.local_declaration(tok, tspec):
                    span, effect = self.consume_locals(tok, tspec, sc); use = Word(tok.text, span)
                else:
                    span = self.consume_parser(tok, tspec, sc); effect = tspec.runtime_clone(); use = Word(tok.text, span or tok.span)
                self.add_checked(seq, use, effect, defname)
            else:
                self.add_checked(seq, tok, self.resolve(tok, 0), defname)

    def next_defined_name(self, sc: Scanner, defining: Word) -> Word:
        name = sc.next_program_word()
        if name is None: raise self.error("Missing word name after " + defining.text, span=defining.span)
        if not name.text: raise self.error("Empty word name after " + defining.text, span=name.span)
        existing = self.ss.find(name.text)
        if existing and (existing.define_mode == DEFINE_COLON or (existing.is_control() and canon(existing.control_mode) == "END")):
            raise self.error("Illegal word name " + name.text, span=name.span)
        return name

    def documented_placeholder(self, sc: Scanner) -> Optional[Spec]:
        preview = sc.clone()
        tok = preview.next_program_word()
        if tok is None: return None
        spec = self.ss.find(tok.text)
        if not self.local_declaration(tok, spec): return None
        assert spec is not None
        body = preview.parse_until(spec.parse_string)
        if body is None: return None
        section = 0; inputs = outputs = 0
        doc = Scanner("<locals-doc>", body.text)
        while (item := doc.next_word()) is not None:
            key = canon(item.text)
            if key == "|": section = 1
            elif key == "--": section = 2
            elif section == 0: inputs += 1
            elif section == 2: outputs += 1
        return generic_spec(inputs, outputs)

    def definition_terminator(self, spec: Spec) -> Optional[str]:
        if spec.parse_mode == PARSE_DEFINITION:
            return canon(spec.parse_string or ";")
        return None

    def skip_definition_body(self, sc: Scanner, defspec: Spec) -> None:
        saved_locals = self.locals
        saved_pos = self.local_pos
        saved_seed = self.local_seed
        saved_seed_index = self.local_seed_index
        self.locals = {}
        self.local_pos = 0
        self.local_seed = None
        self.local_seed_index = 0
        try:
            terminator = self.definition_terminator(defspec)
            nested = 0
            while (tok := sc.next_program_word()) is not None:
                spec = None if canon(tok.text) in self.locals else self.ss.find(tok.text)
                if spec and spec.defines_word():
                    if spec.define_mode == DEFINE_COLON: nested += 1
                    sc.next_program_word()
                elif ((spec and spec.is_control() and canon(spec.control_mode) == "END")
                      or (terminator and canon(tok.text) == terminator)):
                    if nested: nested -= 1
                    else: return
                elif spec and self.local_declaration(tok, spec):
                    self.consume_locals(tok, spec, sc)
                elif spec and spec.is_immediate():
                    self.skip_immediate(spec, sc)
        finally:
            self.locals = saved_locals
            self.local_pos = saved_pos
            self.local_seed = saved_seed
            self.local_seed_index = saved_seed_index

    @staticmethod
    def skip_immediate(spec: Spec, sc: Scanner) -> None:
        if spec.consumes_word(): sc.next_program_word()
        elif spec.consumes_until():
            sc.skip_whitespace(); sc.parse_until(spec.parse_string)

    def parse_definition(self, tok: Word, spec: Spec, sc: Scanner) -> None:
        if spec.left or spec.right:
            raise self.error("Colon definition word must have stack effect ( -- )", span=tok.span)
        name = self.next_defined_name(sc, tok)
        documented = self.documented_placeholder(sc)
        if documented is not None:
            documented.span, documented.origin = name.span, name.text
            self.ss.set_word(name.text, documented)
            self.skip_definition_body(sc, spec)
            self.program.logs.append(name.text + " " + str(documented))
            return
        old = self.locals, self.local_pos, self.local_seed, self.local_seed_index
        self.locals, self.local_pos = {}, 0
        seeded = self.ss.find(name.text)
        self.local_seed = seeded
        self.local_seed_index = 0
        try:
            result = self.parse_definition_seq(name.text, sc)
        finally:
            self.locals, self.local_pos, self.local_seed, self.local_seed_index = old
        self.ss.set_word(name.text, result)
        self.program.logs.append(name.text + " " + str(result))

    def define_constant(self, tok: Word, spec: Spec, sc: Scanner) -> None:
        name = self.next_defined_name(sc, tok)
        span = Span.cover(tok.span, name.span)
        if len(spec.left) != 1 or spec.right:
            raise self.error(tok.text + " must have defining shape ( x -- )", span=span)
        prefix = self.evaluate_sequence(self.program.specs, "top-level program")
        if not prefix.right:
            raise self.error(tok.text + " " + name.text + " requires one value on the stack", span=span)
        top, expected = prefix.right[-1], spec.left[0]
        if self.ts.relation(top.type, expected.type) == 0:
            raise self.error(tok.text + " " + name.text + " expects a value comparable with " + expected.type +
                             " but the current stack provides " + top.type, span=span)
        made = Spec([], [Symbol(top.type)])
        self.ss.set_word(name.text, made)
        self.program.logs.append(name.text + " " + str(made))
        self.program.add("", span, Spec([Symbol(top.type)], []))

    def define_variable(self, tok: Word, spec: Spec, sc: Scanner) -> None:
        name = self.next_defined_name(sc, tok)
        span = Span.cover(tok.span, name.span)
        if spec.left or len(spec.right) != 1:
            raise self.error(tok.text + " must have defining shape ( -- y )", span=span)
        made = spec.runtime_clone()
        self.ss.set_word(name.text, made)
        self.program.logs.append(name.text + " " + str(made))

    def add_program_checked(self, tok: Word, spec: Spec) -> None:
        self.program.add(tok.text, tok.span, spec)
        try:
            self.evaluate_sequence(self.program.specs, "top-level program")
        except EvalError:
            self.program.discard_last()
            raise

    def process_top(self, tok: Word, spec: Optional[Spec], sc: Scanner) -> None:
        if spec is None:
            self.add_program_checked(tok, self.resolve(tok, 0)); return
        if spec.effective_state() == STATE_COMPILE:
            raise self.error("Word not supported in interpretation state", span=tok.span)
        if not spec.is_immediate():
            self.add_program_checked(tok, self.resolve(tok, 0)); return
        if spec.defines_word():
            if spec.define_mode == DEFINE_COLON: self.parse_definition(tok, spec, sc)
            elif spec.define_mode == DEFINE_CONSTANT: self.define_constant(tok, spec, sc)
            elif spec.define_mode == DEFINE_VARIABLE: self.define_variable(tok, spec, sc)
            else: raise self.error("Unsupported top-level defining word", span=tok.span)
        elif spec.is_control():
            raise self.error("Unexpected control word in top-level program", span=tok.span)
        else:
            span = self.consume_parser(tok, spec, sc)
            self.add_program_checked(Word(tok.text, span or tok.span), spec.runtime_clone())

    def forward_seed(self) -> None:
        sc = Scanner(self.name, self.text)
        while (tok := sc.next_program_word()) is not None:
            spec = self.ss.find(tok.text)
            if spec and spec.defines_word():
                name = sc.next_program_word()
                if name is not None and self.ss.find(name.text) is None:
                    placeholder = None
                    if spec.define_mode == DEFINE_COLON:
                        placeholder = self.documented_placeholder(sc)
                    elif spec.define_mode == DEFINE_CONSTANT:
                        placeholder = generic_spec(0, len(spec.left))
                    elif spec.define_mode == DEFINE_VARIABLE:
                        placeholder = generic_spec(0, len(spec.right))
                    if placeholder is not None:
                        self.ss.set_word(name.text, placeholder)
                if spec.define_mode == DEFINE_COLON:
                    self.skip_definition_body(sc, spec)
            elif spec and spec.is_immediate():
                self.skip_immediate(spec, sc)

    def recover_definition(self, sc: Scanner, badtok: Optional[Word],
                           badspec: Optional[Spec]) -> None:
        # The failing token has already been consumed.  If it was the outer
        # terminator, recovery is complete.  If it opened a nested definition,
        # account for that definition before scanning the remaining payload.
        if badtok is None:
            return
        if badspec and badspec.is_control() and canon(badspec.control_mode) == "END":
            return
        nested = 1 if badspec and badspec.define_mode == DEFINE_COLON else 0
        if badspec and badspec.is_immediate():
            self.skip_immediate(badspec, sc)
        while (tok := sc.next_program_word()) is not None:
            spec = self.ss.find(tok.text)
            if spec and spec.define_mode == DEFINE_COLON:
                nested += 1
                sc.next_program_word()
            elif spec and spec.is_control() and canon(spec.control_mode) == "END":
                if nested:
                    nested -= 1
                else:
                    return
            elif spec and spec.is_immediate():
                self.skip_immediate(spec, sc)

    def parse(self) -> Program:
        self.forward_seed()
        sc = Scanner(self.name, self.text)
        while (tok := sc.next_program_word()) is not None:
            self.current_token = tok
            spec = self.ss.find(tok.text)
            try:
                self.process_top(tok, spec, sc)
            except EvalError as exc:
                self.program.diagnostics.append(exc.diagnostic)
                self.program.logs.append("Error: " + exc.diagnostic.summary())
                if spec and spec.define_mode == DEFINE_COLON:
                    badtok = self.current_token
                    badspec = self.ss.find(badtok.text) if badtok is not None else None
                    self.recover_definition(sc, badtok, badspec)
                elif spec and spec.is_immediate():
                    self.skip_immediate(spec, sc)
        return self.program


def generic_spec(inputs: int, outputs: int) -> Spec:
    spec = Spec([Symbol("x") for _ in range(inputs)], [Symbol("x") for _ in range(outputs)])
    spec.find_max()
    return spec


# ---------------------------------------------------------------------------
# CLI


@dataclass
class Config:
    types: str = "ex1types.txt"
    specs: str = "ex1specs.txt"
    prog: Optional[str] = "ex1prog.txt"
    words: list[str] = field(default_factory=list)


def usage() -> str:
    return ("Usage: python3 python3-evaluator.py [--types TYPES] [--specs SPECS] "
            "[--prog PROGRAM] [word ...] (defaults: ex1types.txt, ex1specs.txt, ex1prog.txt)")


def parse_args(argv: list[str]) -> Config:
    cfg = Config()
    if not argv and not all(os.path.exists(x) for x in (cfg.types, cfg.specs, cfg.prog or "")):
        raise EvalError(Diagnostic(usage()))
    i = 0
    while i < len(argv):
        arg = argv[i]
        if canon(arg) in ("--HELP", "-H"):
            raise EvalError(Diagnostic(usage()))
        if canon(arg) in ("--TYPES", "--SPECS", "--PROG"):
            if i + 1 >= len(argv):
                raise EvalError(Diagnostic("Missing file name after " + arg))
            value = argv[i + 1]
            if canon(arg) == "--TYPES": cfg.types = value
            elif canon(arg) == "--SPECS": cfg.specs = value
            else: cfg.prog = value
            i += 2
        else:
            cfg.words.append(arg); i += 1
    return cfg


def write_log(path: str, lines: list[str]) -> None:
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for line in lines:
            f.write(line + "\n")


def annotate(program: Program, specs: EffectList, final: Spec) -> str:
    lines = ["> " + "".join(map(str, final.left))]
    if not program.words:
        lines.append("")
    for word, spec in zip(program.words, specs):
        if word.strip():
            lines.append("    " + word + " \t" + str(spec))
    lines.append("< " + "".join(map(str, final.right)))
    return "\n".join(lines) + "\n"


def run(argv: list[str]) -> int:
    cfg: Optional[Config] = None
    log_path: Optional[str] = None
    log_lines: list[str] = []
    try:
        cfg = parse_args(argv)
        log_path = (cfg.prog + ".log") if cfg.prog is not None else "command-line.log"
        # Match GForth: create/truncate the log before loading input files.
        write_log(log_path, [])
        print("Types file: " + cfg.types)
        print("Specs file: " + cfg.specs)
        if cfg.words:
            print("Program source: command line")
        else:
            print("Program file: " + str(cfg.prog))
        ts = TypeSystem(cfg.types)
        ss = SpecSet(cfg.specs, ts)
        if cfg.words:
            name, text = "<command line>", " ".join(cfg.words)
        else:
            assert cfg.prog is not None
            name, text = cfg.prog, normalized_file(cfg.prog)
        parser = ProgramParser(name, text, ts, ss)
        program = parser.parse()
        log_lines.extend(program.logs)
        if program.diagnostics:
            for diag in program.diagnostics:
                print("Error: " + diag.render())
            write_log(log_path, log_lines)
            return 1
        final_specs = program.specs.clone()
        final = final_specs.evaluate(ts)
        if final is None:
            raise parser.error("Type clash in top-level program", span=parser.current_token.span if parser.current_token else None)
        print("Program text:")
        print(program.text)
        visible = "".join((w + " ") for w in program.words if w)
        print("Program: " + visible)
        print(annotate(program, final_specs, final))
        write_log(log_path, log_lines)
        return 0
    except EvalError as exc:
        print("Error: " + exc.diagnostic.render())
        if log_path:
            log_lines.append("Error: " + exc.diagnostic.summary())
            try: write_log(log_path, log_lines)
            except OSError: pass
        return 1
    except OSError as exc:
        message = str(exc)
        print("Error: " + message)
        if log_path:
            try: write_log(log_path, log_lines + ["Error: " + message])
            except OSError: pass
        return 1


if __name__ == "__main__":
    raise SystemExit(run(sys.argv[1:]))
