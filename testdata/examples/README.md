# Example Layout

`forth2012/` contains plain Forth source examples intended to run with
the bundled launcher, for example `./run-evaluator.sh forth2012 --prog ...`
on Linux or `run-evaluator.bat forth2012 --prog ...` on Windows.

`custom_types/` contains small self-contained example profiles where the
program, specs, and type system are bundled together to demonstrate
non-standard evaluator configurations.

`custom_types/control_equation/` shows custom control words declared through
one declarative `syntax:` / `effect:` block instead of hard-coded Java roles.

`custom_types/control_switch_like/` shows a fixed multi-part switch-like
structure with several captured segments and repeated boundary words.
