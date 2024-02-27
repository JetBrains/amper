@echo off

rem 
rem Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
rem 

rem Runs amper cli from sources
rem based on https://github.com/mfilippov/gradle-jvm-wrapper/blob/70c0c807169eb6818d10ee3f7fcc34153656e1eb/src/main/kotlin/me/filippov/gradle/jvm/wrapper/Plugin.kt#L129

setlocal

set BUILD_DIR=%LOCALAPPDATA%\gradle-jvm
set JVM_TARGET_DIR=%BUILD_DIR%\jdk-17.0.3.1_windows-x64_bin-d6ede5\

set JVM_URL=https://download.oracle.com/java/17/archive/jdk-17.0.3.1_windows-x64_bin.zip

set IS_TAR_GZ=0
set JVM_TEMP_FILE=gradle-jvm.zip

if /I "%JVM_URL:~-7%"==".tar.gz" (
    set IS_TAR_GZ=1
    set JVM_TEMP_FILE=gradle-jvm.tar.gz
)

set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

if not exist "%JVM_TARGET_DIR%" MD "%JVM_TARGET_DIR%"

if not exist "%JVM_TARGET_DIR%.flag" goto downloadAndExtractJvm

set /p CURRENT_FLAG=<"%JVM_TARGET_DIR%.flag"
if "%CURRENT_FLAG%" == "%JVM_URL%" goto continueWithJvm

:downloadAndExtractJvm

PUSHD "%BUILD_DIR%"
if errorlevel 1 goto fail

echo Downloading %JVM_URL% to %BUILD_DIR%\%JVM_TEMP_FILE%
if exist "%JVM_TEMP_FILE%" DEL /F "%JVM_TEMP_FILE%"
"%POWERSHELL%" -nologo -noprofile -Command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; (New-Object Net.WebClient).DownloadFile('%JVM_URL%', '%JVM_TEMP_FILE%')"
if errorlevel 1 goto fail

POPD

RMDIR /S /Q "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

MKDIR "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

PUSHD "%JVM_TARGET_DIR%"
if errorlevel 1 goto fail

echo Extracting %BUILD_DIR%\%JVM_TEMP_FILE% to %JVM_TARGET_DIR%

if "%IS_TAR_GZ%"=="1" (
    tar xf "..\\%JVM_TEMP_FILE%"
) else (
    "%POWERSHELL%" -nologo -noprofile -command "Set-StrictMode -Version 3.0; $ErrorActionPreference = \"Stop\"; Add-Type -A 'System.IO.Compression.FileSystem'; [IO.Compression.ZipFile]::ExtractToDirectory('..\\%JVM_TEMP_FILE%', '.');"
)
if errorlevel 1 goto fail

DEL /F "..\%JVM_TEMP_FILE%"
if errorlevel 1 goto fail

POPD

echo %JVM_URL%>"%JVM_TARGET_DIR%.flag"
if errorlevel 1 goto fail

:continueWithJvm

set AMPER_JAVA_HOME=
for /d %%d in ("%JVM_TARGET_DIR%"*) do if exist "%%d\bin\java.exe" set AMPER_JAVA_HOME=%%d
if not exist "%AMPER_JAVA_HOME%\bin\java.exe" (
  echo Unable to find java.exe under %JVM_TARGET_DIR%
  exit 1
  goto fail
)

endlocal & set AMPER_JAVA_HOME=%AMPER_JAVA_HOME%

pushd "%~dp0..\.."
if errorlevel 1 goto fail
call gradlew.bat --stacktrace --quiet :sources:cli:unpackedDistribution
if errorlevel 1 goto fail
popd
if errorlevel 1 goto fail

"%AMPER_JAVA_HOME%\bin\java.exe" -ea -cp "%~dp0build\unpackedDistribution\lib\*" org.jetbrains.amper.cli.MainKt %*
exit /b %ERRORLEVEL%

:fail
exit /b 1
