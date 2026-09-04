#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$script_dir"

vfx_bin=${VFXFORTH_BIN:-}
if [ -n "$vfx_bin" ] && command -v "$vfx_bin" >/dev/null 2>&1; then
   vfx_bin=$(command -v "$vfx_bin")
elif [ -z "$vfx_bin" ]; then
   for candidate in vfx64 vfxlin64 VfxForth_x64_lin.elf
   do
      if command -v "$candidate" >/dev/null 2>&1; then
         vfx_bin=$(command -v "$candidate")
         break
      fi
   done
fi

if [ -z "$vfx_bin" ] || [ ! -x "$vfx_bin" ]; then
   echo "Error: VFX Forth 64 5.43 was not found; set VFXFORTH_BIN." >&2
   exit 1
fi

# Support both an installed VFX and an unpacked Community distribution.
vfx_bin_dir=$(CDPATH= cd -- "$(dirname -- "$vfx_bin")" && pwd)
vfx_support_dir=$vfx_bin_dir/../VfxSupport/Lin64
if [ -d "$vfx_support_dir" ]; then
   LD_LIBRARY_PATH=$vfx_bin_dir:$vfx_support_dir${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}
   export LD_LIBRARY_PATH
fi

profile=forth2012
case "${1-}" in
   real|legacy|forth2012|ans94|gforth1.0|vfxforth5.43|vfx)
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
   ans94)
      types_file=$script_dir/ans94types.txt
      specs_file=$script_dir/ans94specs.txt
      prog_file=$script_dir/forth2012prog.txt
      ;;
   gforth1.0)
      types_file=$script_dir/gforth1.0types.txt
      specs_file=$script_dir/gforth1.0specs.txt
      prog_file=$script_dir/forth2012prog.txt
      ;;
   vfxforth5.43|vfx)
      types_file=$script_dir/vfxforth5.43types.txt
      specs_file=$script_dir/vfxforth5.43specs.txt
      prog_file=$script_dir/forth2012prog.txt
      ;;
esac

if [ "$#" -eq 1 ] && [ -f "$1" ]; then
   prog_file=$1
   shift
fi

# VFX evaluates its operating-system arguments as Forth input. The evaluator
# recognizes and skips this INCLUDE pair, then parses the remaining arguments.
exec "$vfx_bin" include "$script_dir/vfx-evaluator.fth" \
   --types "$types_file" \
   --specs "$specs_file" \
   --prog "$prog_file" \
   "$@"
