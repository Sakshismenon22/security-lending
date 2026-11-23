@echo off
setlocal
cd /d "%~dp0"
call mvnw.cmd spring-boot:run "-Dspring-boot.run.jvmArguments=-Xms96m -Xmx384m -XX:MaxMetaspaceSize=160m" -Dspring-boot.run.profiles=lender
exit /b %errorlevel%
