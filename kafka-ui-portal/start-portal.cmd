@echo off
setlocal
cd /d "%~dp0kafka-ui-portal"
where node >nul 2>nul
if errorlevel 1 (
  echo Install Node.js 22 or newer, then run this launcher again.
  pause
  exit /b 1
)
if not exist node_modules (
  call npm ci
  if errorlevel 1 exit /b 1
)
call npm run build
if errorlevel 1 exit /b 1
echo Open http://127.0.0.1:3001 in your browser.
call npm start
