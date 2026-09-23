@echo off
@REM Alias for mvnw.cmd  ->  maven spring-boot:run
call "%~dp0mvnw.cmd" %*
exit /b %ERRORLEVEL%
