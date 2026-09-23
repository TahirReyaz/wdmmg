@echo off
@REM ---------------------------------------------------------------------------
@REM Maven Wrapper for Windows (script-only). Downloads the pinned Maven on first
@REM use and runs it.   Usage:  mvnw spring-boot:run
@REM ---------------------------------------------------------------------------
setlocal EnableDelayedExpansion

set "BASEDIR=%~dp0"
if "%BASEDIR:~-1%"=="\" set "BASEDIR=%BASEDIR:~0,-1%"
set "PROPS=%BASEDIR%\.mvn\wrapper\maven-wrapper.properties"

set "DIST_URL="
if exist "%PROPS%" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%PROPS%") do (
    if "%%A"=="distributionUrl" set "DIST_URL=%%B"
  )
)
if "%DIST_URL%"=="" set "DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"

@REM Derive "apache-maven-X.Y.Z-bin.zip" from the URL (last path segment)
set "DIST_ZIP=%DIST_URL%"
:strip
set "DIST_ZIP=%DIST_ZIP:*/=%"
if not "%DIST_ZIP%"=="%DIST_ZIP:/=%" goto strip
set "DIST_NAME=%DIST_ZIP:-bin.zip=%"
if "%MAVEN_USER_HOME%"=="" set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "WRAPPER_HOME=%MAVEN_USER_HOME%\wrapper\dists"
set "MAVEN_HOME_DIR=%WRAPPER_HOME%\%DIST_NAME%"

if not exist "%MAVEN_HOME_DIR%\bin\mvn.cmd" (
  echo Downloading %DIST_URL%
  if not exist "%WRAPPER_HOME%" mkdir "%WRAPPER_HOME%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12;" ^
    "$zip=Join-Path $env:TEMP '%DIST_ZIP%';" ^
    "Invoke-WebRequest -UseBasicParsing -Uri '%DIST_URL%' -OutFile $zip;" ^
    "Expand-Archive -Force -Path $zip -DestinationPath '%WRAPPER_HOME%';" ^
    "Remove-Item $zip"
  if errorlevel 1 (
    echo Failed to download Maven.
    exit /b 1
  )
)

cd /d "%BASEDIR%"
call "%MAVEN_HOME_DIR%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
