@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "found="
set "failed="

for /d %%D in ("%~dp0*") do (
    if exist "%%~fD\pom.xml" (
        set "found=1"
        call :clean "%%~fD"
        if errorlevel 1 set "failed=1"
    )
)

if not defined found (
    echo No Maven services found beside clean-all.cmd. 1>&2
    exit /b 1
)

if defined failed (
    echo One or more Maven services failed to clean. See the output above. 1>&2
    exit /b 1
)

echo All Maven services cleaned successfully.
exit /b 0

:clean
echo.
echo Cleaning %~nx1 ...
if not exist "%~1\mvnw.cmd" (
    echo Missing Maven wrapper in "%~1". 1>&2
    exit /b 1
)
pushd "%~1" || exit /b 1
call mvnw.cmd clean
set "cleanResult=%ERRORLEVEL%"
popd
if not "%cleanResult%"=="0" (
    echo Clean failed in "%~1" with exit code %cleanResult%. 1>&2
    exit /b 1
)
exit /b 0
