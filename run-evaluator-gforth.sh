#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$script_dir"

gforth_bin=${GFORTH_BIN:-}
if [ -z "$gforth_bin" ]; then
   for candidate in "$HOME/.local/bin/gforth-fast" "$HOME/.local/bin/gforth" gforth-fast gforth
   do
      if command -v "$candidate" >/dev/null 2>&1 || [ -x "$candidate" ]; then
         gforth_bin=$candidate
         break
      fi
   done
fi

if [ -z "$gforth_bin" ]; then
   echo "Error: 64-bit Gforth 0.7.9 or newer was not found." >&2
   exit 1
fi

version=$("$gforth_bin" --version 2>&1 | sed -n '1p')
case "$version" in
   "gforth 0.7.9"*|"gforth 0.8"*|"gforth 0.9"*|"gforth "[1-9]*) ;;
   *)
      echo "Error: $gforth_bin is '$version'; 64-bit Gforth 0.7.9 or newer is required." >&2
      exit 1
      ;;
esac

if ! "$gforth_bin" -e 'cell 8 <> abort" This Gforth is not 64-bit" bye' >/dev/null 2>&1; then
   echo "Error: $gforth_bin is not a working 64-bit Gforth." >&2
   exit 1
fi

# Use the libraries belonging to the newest user-local installation, if present.
libcc_dir=$(find "$HOME/.local/lib/gforth" -type d -path '*/amd64/libcc-named' 2>/dev/null | sort | tail -n 1 || true)
if [ -n "$libcc_dir" ]; then
   export libccnameddir=$libcc_dir/
fi

export XDG_CACHE_HOME=${XDG_CACHE_HOME:-$HOME/.cache/gforth}
mkdir -p "$XDG_CACHE_HOME"

profile=forth2012
case "${1-}" in
   real|legacy|forth2012)
      profile=$1
      shift
      ;;
esac

case "$profile" in
   real)
      types_file=$script_dir/ex1types.txt
      specs_file=$script_dir/ex1specs.txt
      prog_file=$script_dir/ex1prog.txt
      ;;
   legacy)
      types_file=$script_dir/legacytypes.txt
      specs_file=$script_dir/legacyspecs.txt
      prog_file=$script_dir/legacyprog.txt
      ;;
   forth2012)
      types_file=$script_dir/forth2012types.txt
      specs_file=$script_dir/forth2012specs.txt
      prog_file=$script_dir/forth2012prog.txt
      ;;
esac

if [ "$#" -eq 1 ] && [ -f "$1" ]; then
   prog_file=$1
   shift
fi

# The evaluator is larger than the dictionary in some stock Gforth images.
exec "$gforth_bin" -m 4M "$script_dir/gforth-evaluator.fs" \
   --types "$types_file" \
   --specs "$specs_file" \
   --prog "$prog_file" \
   "$@"
