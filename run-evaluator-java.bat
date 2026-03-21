@echo off
setlocal

set "script_dir=%~dp0"
set "profile=forth2012"

where /Q java.exe
if errorlevel 1 (
   echo Error: java.exe not found on PATH 1>&2
   exit /b 1
)

if not exist "%script_dir%evaluator\Evaluator.class" (
   echo Error: compiled Java classes not found under %script_dir%evaluator 1>&2
   echo Compile from the parent directory with: javac evaluator/*.java 1>&2
   exit /b 1
)

if /I "%~1"=="real" set "profile=real" & shift
if /I "%~1"=="legacy" set "profile=legacy" & shift
if /I "%~1"=="forth2012" set "profile=forth2012" & shift

if /I "%profile%"=="real" (
   set "types_file=%script_dir%ex1types.txt"
   set "specs_file=%script_dir%ex1specs.txt"
   set "prog_file=%script_dir%ex1prog.txt"
)
if /I "%profile%"=="legacy" (
   set "types_file=%script_dir%legacytypes.txt"
   set "specs_file=%script_dir%legacyspecs.txt"
   set "prog_file=%script_dir%legacyprog.txt"
)
if /I "%profile%"=="forth2012" (
   set "types_file=%script_dir%forth2012types.txt"
   set "specs_file=%script_dir%forth2012specs.txt"
   set "prog_file=%script_dir%forth2012prog.txt"
)

set "extra_args="
:collect_args
if "%~1"=="" goto launch
set "extra_args=%extra_args% "%~1""
shift
goto collect_args

:launch
java -cp "%script_dir%." evaluator.Evaluator ^
   --types "%types_file%" ^
   --specs "%specs_file%" ^
   --prog "%prog_file%" ^
   %extra_args%
exit /b %errorlevel%
