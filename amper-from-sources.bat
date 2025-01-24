@echo off

rem
rem Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
rem

rem Runs amper cli from sources
rem based on https://github.com/mfilippov/gradle-jvm-wrapper/blob/70c0c807169eb6818d10ee3f7fcc34153656e1eb/src/main/kotlin/me/filippov/gradle/jvm/wrapper/Plugin.kt#L129
rem and amper.template.bat

setlocal

if not defined AMPER_JRE_DOWNLOAD_ROOT set AMPER_JRE_DOWNLOAD_ROOT=https:/
if not defined AMPER_BOOTSTRAP_CACHE_DIR set AMPER_BOOTSTRAP_CACHE_DIR=%LOCALAPPDATA%\Amper
@rem remove trailing \ if present
if [%AMPER_BOOTSTRAP_CACHE_DIR:~-1%] EQU [\] set AMPER_BOOTSTRAP_CACHE_DIR=%AMPER_BOOTSTRAP_CACHE_DIR:~0,-1%

goto :after_function_declarations

REM ********** Download and extract any zip or .tar.gz archive **********

:download_and_extract
setlocal

set moniker=%~1
set url=%~2
set target_dir=%~3
set sha=%~4
set sha_size=%~5

set flag_file=%target_dir%\.flag
if exist "%flag_file%" (
    set /p current_flag=<"%flag_file%"
    if "%current_flag%" == "%sha%" exit /b
)

@rem This multiline string is actually passed as a single line to powershell, meaning #-comments are not possible.
@rem So here are a few comments about the code below:
@rem  - we need to support both .zip and .tar.gz archives (for the Amper distribution and the JBR)
@rem  - tar should be present in all Windows machines since 2018 (and usable from both cmd and powershell)
@rem  - tar requires the destination dir to exist
@rem  - DownloadFile requires the directories in the destination file's path to exist
set download_and_extract_ps1= ^
Set-StrictMode -Version 3.0; ^
$ErrorActionPreference = 'Stop'; ^
 ^
$createdNew = $false; ^
$lock = New-Object System.Threading.Mutex($true, ('Global\amper-bootstrap.' + '%target_dir%'.GetHashCode().ToString()), [ref]$createdNew); ^
if (-not $createdNew) { ^
    Write-Host 'Another Amper instance is bootstrapping. Waiting for our turn...'; ^
    [void]$lock.WaitOne(); ^
} ^
 ^
try { ^
    if ((Get-Content '%flag_file%' -ErrorAction Ignore) -ne '%sha%') { ^
        $temp_file = '%AMPER_BOOTSTRAP_CACHE_DIR%\' + [System.IO.Path]::GetRandomFileName(); ^
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
        Write-Host 'Downloading %moniker%... (only happens on the first run of this version)'; ^
        [void](New-Item '%AMPER_BOOTSTRAP_CACHE_DIR%' -ItemType Directory -Force); ^
        (New-Object Net.WebClient).DownloadFile('%url%', $temp_file); ^
 ^
        $actualSha = (Get-FileHash -Algorithm SHA%sha_size% -Path $temp_file).Hash.ToString(); ^
        if ($actualSha -ne '%sha%') { ^
          throw ('Checksum mismatch for ' + $temp_file + ' (downloaded from %url%): expected checksum %sha% but got ' + $actualSha); ^
        } ^
 ^
        if (Test-Path '%target_dir%') { ^
            Remove-Item '%target_dir%' -Recurse; ^
        } ^
        if ($temp_file -like '*.zip') { ^
            Add-Type -A 'System.IO.Compression.FileSystem'; ^
            [IO.Compression.ZipFile]::ExtractToDirectory($temp_file, '%target_dir%'); ^
        } else { ^
            [void](New-Item '%target_dir%' -ItemType Directory -Force); ^
            tar -xzf $temp_file -C '%target_dir%'; ^
        } ^
        Remove-Item $temp_file; ^
 ^
        Set-Content '%flag_file%' -Value '%sha%'; ^
        Write-Host 'Download complete.'; ^
        Write-Host ''; ^
    } ^
} ^
finally { ^
    $lock.ReleaseMutex(); ^
}

set powershell=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe
"%powershell%" -NonInteractive -NoProfile -NoLogo -Command %download_and_extract_ps1%
if errorlevel 1 exit /b 1
exit /b 0

:fail
echo ERROR: Amper bootstrap failed, see errors above
exit /b 1

:after_function_declarations

REM ********** Provision JRE for Amper **********

if defined AMPER_JAVA_HOME goto jre_provisioned

@rem Auto-updated from syncVersions.main.kts, do not modify directly here
set jbr_version=21.0.4
set jbr_build=b509.26
if "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    set jbr_arch=aarch64
    set jbr_sha512=9fd2333f3d55f0d40649435fc27e5ab97ad44962f54c1c6513e66f89224a183cd0569b9a3994d840b253060d664630610f82a02f45697e5e6c0b4ee250dd1857
) else if "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
    set jbr_arch=x64
    set jbr_sha512=6a639d23039b83cf1b0ed57d082bb48a9bff6acae8964192a1899e8a1c0915453199b501b498e5874bc57c9996d871d49f438054b3c86f643f1c1c4f178026a3
) else (
    echo Unknown Windows architecture %PROCESSOR_ARCHITECTURE% >&2
    goto fail
)

@rem URL for JBR (vanilla) - see https://github.com/JetBrains/JetBrainsRuntime/releases
set jbr_url=%AMPER_JRE_DOWNLOAD_ROOT%/cache-redirector.jetbrains.com/intellij-jbr/jbr-%jbr_version%-windows-%jbr_arch%-%jbr_build%.tar.gz
set jbr_target_dir=%AMPER_BOOTSTRAP_CACHE_DIR%\jbr-%jbr_version%-windows-%jbr_arch%-%jbr_build%
call :download_and_extract "JetBrains Runtime v%jbr_version%%jbr_build%" "%jbr_url%" "%jbr_target_dir%" "%jbr_sha512%" "512"
if errorlevel 1 goto fail

set AMPER_JAVA_HOME=
for /d %%d in ("%jbr_target_dir%\*") do if exist "%%d\bin\java.exe" set AMPER_JAVA_HOME=%%d
if not exist "%AMPER_JAVA_HOME%\bin\java.exe" (
  echo Unable to find java.exe under %jbr_target_dir%
  goto fail
)
:jre_provisioned

REM ********** Build Amper from sources **********

pushd "%~dp0"
if errorlevel 1 goto fail

echo Building Amper distribution from sources...
call amper.bat --log-level=warn task :cli:unpackedDist
if errorlevel 1 goto fail

echo Publishing Amper Android support plugin for delegated Gradle builds...
rem Amper needs a published Amper Android Gradle plugin support for the delegated Gradle builds
call amper.bat --log-level=warn publish -m gradle-plugin mavenLocal
if errorlevel 1 goto fail

cls
popd
if errorlevel 1 goto fail

REM ********** Launch Amper from unpacked dist **********

set jvm_args=-ea -XX:+EnableDynamicAgentLoading %AMPER_JAVA_OPTIONS%
"%AMPER_JAVA_HOME%\bin\java.exe" "-Damper.wrapper.path=%~f0" %jvm_args% -cp "%~dp0build\tasks\_cli_unpackedDist\dist\lib\*" org.jetbrains.amper.cli.MainKt --build-output="%~dp0build-from-sources" %*
exit /B %ERRORLEVEL%
