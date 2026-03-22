# Test Layout

`positive/` contains programs that are expected to load and type-check
successfully.

`positive/forth2012/` contains plain Forth source examples intended to run
with the bundled launcher defaults, for example
`./run-evaluator-gforth.sh --prog ...` or `./run-evaluator-java.sh --prog ...`
on Linux, or `run-evaluator-gforth.bat --prog ...` /
`run-evaluator-java.bat --prog ...` on Windows.

`positive/custom_types/` contains small self-contained example profiles where
the program, specs, and type system are bundled together to demonstrate
non-standard evaluator configurations.

`positive/custom_types/control_equation/` shows custom control words declared
through one declarative `syntax:` / `effect:` block instead of hard-coded Java
roles.

`positive/custom_types/control_switch_like/` shows a fixed multi-part
switch-like structure with several captured segments and repeated boundary
words.

`negative/` contains programs that are expected to fail with diagnostics.

Both runtimes also create adjacent `.log` files during these runs, recording
created definitions and reported errors in a specs-like text format.
