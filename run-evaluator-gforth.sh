#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$script_dir"

gforth_bin=
for candidate in gforth-fast gforth \
   "$script_dir/../gforth/gforth-fast" "$script_dir/../gforth/gforth"
do
   if command -v "$candidate" >/dev/null 2>&1 || [ -x "$candidate" ]; then
      gforth_bin=$candidate
      break
   fi
done

if [ -z "$gforth_bin" ]; then
   echo "Error: gforth executable not found on PATH or in $script_dir/../gforth" >&2
   exit 1
fi

libcc_dir=$script_dir/../gforth/lib/gforth/0.7.9_20260224/amd64/libcc-named
if [ -d "$libcc_dir" ]; then
   export libccnameddir=$libcc_dir/
fi

export XDG_CACHE_HOME=${XDG_CACHE_HOME:-/tmp/gforth-cache}
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

exec "$gforth_bin" "$script_dir/gforth-evaluator.fs" \
   --types "$types_file" \
   --specs "$specs_file" \
   --prog "$prog_file" \
   "$@"
