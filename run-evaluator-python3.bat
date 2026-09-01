@echo off
setlocal

set "script_dir=%~dp0"
set "profile=forth2012"

where /Q python3.exe
if errorlevel 1 (
   echo Error: python3.exe not found on PATH 1>&2
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

if not "%~1"=="" if "%~2"=="" if exist "%~1" (
   set "prog_file=%~1"
   shift
)

set "extra_args="
:collect_args
if "%~1"=="" goto launch
set "extra_args=%extra_args% "%~1""
shift
goto collect_args

:launch
python3 "%script_dir%python3-evaluator.py" ^
   --types "%types_file%" ^
   --specs "%specs_file%" ^
   --prog "%prog_file%" ^
   %extra_args%
exit /b %errorlevel%
