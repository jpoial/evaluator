#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$script_dir"

python_bin=${PYTHON_BIN:-python3}
if ! command -v "$python_bin" >/dev/null 2>&1; then
   echo "Error: $python_bin not found on PATH" >&2
   exit 1
fi

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

exec "$python_bin" "$script_dir/python3-evaluator.py" \
   --types "$types_file" \
   --specs "$specs_file" \
   --prog "$prog_file" \
   "$@"
