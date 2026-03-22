@echo off
setlocal

set "script_dir=%~dp0"
set "profile=forth2012"
set "gforth_bin="

where /Q gforth-fast.exe
if not errorlevel 1 set "gforth_bin=gforth-fast.exe"

if not defined gforth_bin (
   where /Q gforth.exe
   if not errorlevel 1 set "gforth_bin=gforth.exe"
)

if not defined gforth_bin if exist "%script_dir%..\gforth\gforth-fast.exe" set "gforth_bin=%script_dir%..\gforth\gforth-fast.exe"
if not defined gforth_bin if exist "%script_dir%..\gforth\gforth.exe" set "gforth_bin=%script_dir%..\gforth\gforth.exe"

if not defined gforth_bin (
   echo Error: gforth executable not found on PATH or in %script_dir%..\gforth 1>&2
   exit /b 1
)

if exist "%script_dir%..\gforth\lib\gforth" (
   for /d /r "%script_dir%..\gforth\lib\gforth" %%D in (libcc-named) do (
      if not defined libccnameddir set "libccnameddir=%%~fD\"
   )
)

if not defined XDG_CACHE_HOME set "XDG_CACHE_HOME=%TEMP%\gforth-cache"
if not exist "%XDG_CACHE_HOME%" mkdir "%XDG_CACHE_HOME%" >nul 2>nul

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
"%gforth_bin%" "%script_dir%gforth-evaluator.fs" ^
   --types "%types_file%" ^
   --specs "%specs_file%" ^
   --prog "%prog_file%" ^
   %extra_args%
exit /b %errorlevel%
