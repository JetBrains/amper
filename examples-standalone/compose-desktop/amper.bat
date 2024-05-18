@echo off

@rem
@rem Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@rem

@rem Possible environment variables:
@rem   AMPER_BOOTSTRAP_CACHE_DIR  Cache directory to store extracted JRE and Amper distribution, must end with \
@rem   AMPER_JAVA_HOME            JRE to run Amper itself (optional, does not affect compilation)

setlocal

set amper_version=0.3.0
set amper_url=https://maven.pkg.jetbrains.space/public/p/amper/amper/org/jetbrains/amper/cli/%amper_version%/cli-%amper_version%-dist.zip

@rem Establish chain of trust from here by specifying exact checksum of Amper distribution to be run
set amper_sha256=3573ad7585d7d32a5c085281d7344fa891add6a6f332c6e453a02104932137ae

if "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    set jvm_url=https://aka.ms/download-jdk/microsoft-jdk-17.0.6-windows-aarch64.zip
    set jvm_file_name=microsoft-jdk-17.0.6-windows-aarch64
    set jvm_sha256=0a24e2382841387bad274ff70f0c3537e3eb3ceb47bc8bc5dc22626b2cb6a87c
) else (
    if "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
        set jvm_url=https://corretto.aws/downloads/resources/17.0.6.10.1/amazon-corretto-17.0.6.10.1-windows-x64-jdk.zip
        set jvm_file_name=amazon-corretto-17.0.6.10.1-windows-x64
        set jvm_sha256=27dfa7189763bf5bee6250baef22bb6f6032deebe0edd11f79495781cc7955fe
    ) else (
        echo Unknown Windows architecture %PROCESSOR_ARCHITECTURE% >&2
        goto fail
    )
)

if defined AMPER_BOOTSTRAP_CACHE_DIR goto continue_with_cache_dir
set AMPER_BOOTSTRAP_CACHE_DIR=%LOCALAPPDATA%\Amper\
:continue_with_cache_dir

rem add \ to the end if not present
if not [%AMPER_BOOTSTRAP_CACHE_DIR:~-1%] EQU [\] set AMPER_BOOTSTRAP_CACHE_DIR=%AMPER_BOOTSTRAP_CACHE_DIR%\

set powershell=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

REM ********** Download and extract JVM **********

if defined AMPER_JAVA_HOME goto continue_with_jvm

set jvm_target_dir=%AMPER_BOOTSTRAP_CACHE_DIR%%jvm_file_name%\
call :download_and_extract "%jvm_url%" "%jvm_target_dir%" "%jvm_sha256%"
if errorlevel 1 goto fail

set AMPER_JAVA_HOME=
for /d %%d in ("%jvm_target_dir%"*) do if exist "%%d\bin\java.exe" set AMPER_JAVA_HOME=%%d
if not exist "%AMPER_JAVA_HOME%\bin\java.exe" (
  echo Unable to find java.exe under %jvm_target_dir%
  goto fail
)

:continue_with_jvm

REM ********** Download and extract Amper **********

set amper_target_dir=%AMPER_BOOTSTRAP_CACHE_DIR%amper-cli-%amper_version%\

call :download_and_extract "%amper_url%" "%amper_target_dir%" "%amper_sha256%"
if errorlevel 1 goto fail

REM ********** Run Amper **********

"%AMPER_JAVA_HOME%\bin\java.exe" -ea "-Damper.wrapper.dist.sha256=%amper_sha256%" "-Damper.wrapper.process.name=%~nx0" -cp "%amper_target_dir%lib\*" org.jetbrains.amper.cli.MainKt %*
exit /B %ERRORLEVEL%

REM ********** Download And Extract Any Zip Archive **********

:download_and_extract
setlocal

set url=%~1
set target_dir=%~2
set sha256=%~3

if not exist "%target_dir%.flag" goto download_and_extract_always

set /p current_flag=<"%target_dir%.flag"
if "%current_flag%" == "%url%" exit /b

:download_and_extract_always

set download_and_extract_ps1= ^
Set-StrictMode -Version 3.0; ^
$ErrorActionPreference = 'Stop'; ^
 ^
$createdNew = $false; ^
$lock = New-Object System.Threading.Mutex($true, ('Global\amper-bootstrap.' + '%target_dir%'.GetHashCode().ToString()), [ref]$createdNew); ^
if (-not $createdNew) { ^
    Write-Host 'Waiting for the other process to finish bootstrap'; ^
    [void]$lock.WaitOne(); ^
} ^
 ^
try { ^
    if ((Get-Content '%target_dir%.flag' -ErrorAction Ignore) -ne '%url%') { ^
        $temp_file = '%AMPER_BOOTSTRAP_CACHE_DIR%' + [System.IO.Path]::GetRandomFileName(); ^
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
        Write-Host 'Downloading %url%'; ^
        [void](New-Item '%target_dir%' -ItemType Directory -Force); ^
        (New-Object Net.WebClient).DownloadFile('%url%', $temp_file); ^
 ^
        $actualSha256 = (Get-FileHash -Algorithm SHA256 -Path $temp_file).Hash.ToString(); ^
        if ($actualSha256 -ne '%sha256%') { ^
          throw ('Checksum mismatch for ' + $temp_file + ' (downloaded from %url%): expected checksum %sha256% but got ' + $actualSha256); ^
        } ^
 ^
        Write-Host 'Extracting to %target_dir%'; ^
        if (Test-Path '%target_dir%') { ^
            Remove-Item '%target_dir%' -Recurse; ^
        } ^
        Add-Type -A 'System.IO.Compression.FileSystem'; ^
        [IO.Compression.ZipFile]::ExtractToDirectory($temp_file, '%target_dir%'); ^
        Remove-Item $temp_file; ^
 ^
        Set-Content '%target_dir%.flag' -Value '%url%'; ^
    } ^
} ^
finally { ^
    $lock.ReleaseMutex(); ^
}

"%powershell%" -NonInteractive -NoProfile -NoLogo -Command %download_and_extract_ps1%
if errorlevel 1 exit /b 1

exit /b 0

:fail
echo ERROR: Amper bootstrap failed, see errors above
exit /b 1
