#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$script_dir"

if ! command -v java >/dev/null 2>&1; then
   echo "Error: java not found on PATH" >&2
   exit 1
fi

if [ ! -f "$script_dir/evaluator/Evaluator.class" ]; then
   echo "Error: compiled Java classes not found under $script_dir/evaluator" >&2
   echo "Compile from the parent directory with: javac evaluator/*.java" >&2
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

exec java -cp "$script_dir/." evaluator.Evaluator \
   --types "$types_file" \
   --specs "$specs_file" \
   --prog "$prog_file" \
   "$@"
