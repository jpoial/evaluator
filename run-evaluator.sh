#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

profile=real
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

exec java -cp "$script_dir" evaluator.Evaluator \
   --types "$types_file" \
   --specs "$specs_file" \
   --prog "$prog_file" \
   "$@"
