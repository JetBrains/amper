@echo off

@rem
@rem Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@rem

@rem Runs amper cli from sources

@rem Possible environment variables:
@rem   AMPER_JRE_DOWNLOAD_ROOT    Url prefix to download Amper JRE from.
@rem                              default: https:/
@rem   AMPER_BOOTSTRAP_CACHE_DIR  Cache directory to store extracted JRE and Amper distribution
@rem   AMPER_JAVA_HOME            JRE to run Amper itself (optional, does not affect compilation)
@rem   AMPER_JAVA_OPTIONS         JVM options to pass to the JVM running Amper (does not affect the user's application)

setlocal

if not defined AMPER_JRE_DOWNLOAD_ROOT set AMPER_JRE_DOWNLOAD_ROOT=https:/
if not defined AMPER_BOOTSTRAP_CACHE_DIR set AMPER_BOOTSTRAP_CACHE_DIR=%LOCALAPPDATA%\JetBrains\Amper
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
set show_banner_on_cache_miss=%~6

set flag_file=%target_dir%\.flag
if exist "%flag_file%" (
    set /p current_flag=<"%flag_file%"
    if "%current_flag%" == "%sha%" exit /b
)

@rem This multiline string is actually passed as a single line to powershell, meaning #-comments are not possible.
@rem So here are a few comments about the code below:
@rem  - we need to support both .zip and .tar.gz archives (for the Amper distribution and the JRE)
@rem  - tar should be present in all Windows machines since 2018 (and usable from both cmd and powershell)
@rem  - tar requires the destination dir to exist
@rem  - We use (New-Object Net.WebClient).DownloadFile instead of Invoke-WebRequest for performance. See the issue
@rem    https://github.com/PowerShell/PowerShell/issues/16914, which is still not fixed in Windows PowerShell 5.1
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
        if ('%show_banner_on_cache_miss%' -eq 'true') { ^
            Write-Host '*** Welcome to Amper v.%amper_version%! ***'; ^
            Write-Host ''; ^
            Write-Host 'This is the first run of this version, so we need to download the actual Amper distribution.'; ^
            Write-Host 'Please give us a few seconds now, subsequent runs will be faster.'; ^
            Write-Host ''; ^
        } ^
        $temp_file = '%AMPER_BOOTSTRAP_CACHE_DIR%\' + [System.IO.Path]::GetRandomFileName(); ^
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
        Write-Host 'Downloading %moniker%...'; ^
        [void](New-Item '%AMPER_BOOTSTRAP_CACHE_DIR%' -ItemType Directory -Force); ^
        if (Get-Command curl.exe -errorAction SilentlyContinue) { ^
            curl.exe -L --silent --show-error --fail --output $temp_file '%url%'; ^
        } else { ^
            (New-Object Net.WebClient).DownloadFile('%url%', $temp_file); ^
        } ^
 ^
        $actualSha = (Get-FileHash -Algorithm SHA%sha_size% -Path $temp_file).Hash.ToString(); ^
        if ($actualSha -ne '%sha%') { ^
            $writeErr = if ($Host.Name -eq 'ConsoleHost') { [Console]::Error.WriteLine } else { $host.ui.WriteErrorLine } ^
            $writeErr.Invoke(\"ERROR: Checksum mismatch for $temp_file (downloaded from %url%): expected checksum %sha% but got $actualSha\"); ^
            exit 1; ^
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
set zulu_version=25.28.85
set java_version=25.0.0
if "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    set pkg_type=jdk
    set jre_arch=aarch64
    set jre_sha256=f5f6d8a913695649e8e2607fe0dc79c81953b2583013ac1fb977c63cb4935bfb
) else if "%PROCESSOR_ARCHITECTURE%"=="AMD64" (
    set pkg_type=jre
    set jre_arch=x64
    set jre_sha256=d3c5db7864e6412ce3971c0b065def64942d7b0f3d02581f7f0472cac21fbba9
) else (
    echo Unknown Windows architecture %PROCESSOR_ARCHITECTURE% >&2
    goto fail
)

@rem URL for the JRE (see https://api.azul.com/metadata/v1/zulu/packages?release_status=ga&include_fields=java_package_features,os,arch,hw_bitness,abi,java_package_type,sha256_hash,size,archive_type,lib_c_type&java_version=25&os=macos,linux,win)
@rem https://cdn.azul.com/zulu/bin/zulu25.28.85-ca-jre25.0.0-win_x64.zip
@rem https://cdn.azul.com/zulu/bin/zulu25.28.85-ca-jdk25.0.0-win_aarch64.zip
set jre_url=%AMPER_JRE_DOWNLOAD_ROOT%/cdn.azul.com/zulu/bin/zulu%zulu_version%-ca-%pkg_type%%java_version%-win_%jre_arch%.zip
set jre_target_dir=%AMPER_BOOTSTRAP_CACHE_DIR%\zulu%zulu_version%-ca-%pkg_type%%java_version%-win_%jre_arch%
call :download_and_extract "Amper runtime v%zulu_version%" "%jre_url%" "%jre_target_dir%" "%jre_sha256%" "256" "false"
if errorlevel 1 goto fail

set AMPER_JAVA_HOME=
for /d %%d in ("%jre_target_dir%\*") do if exist "%%d\bin\java.exe" set AMPER_JAVA_HOME=%%d
if not exist "%AMPER_JAVA_HOME%\bin\java.exe" (
  echo Unable to find java.exe under %jre_target_dir%
  goto fail
)
:jre_provisioned

REM ********** Build Amper from sources **********

pushd "%~dp0"
if errorlevel 1 goto fail

echo Building Amper distribution from sources...
call amper.bat --log-level=warn task :amper-cli:buildUnpacked@amper-distribution
if errorlevel 1 goto fail

echo Publishing Amper Android support plugin for delegated Gradle builds...
rem Amper needs a published Amper Android Gradle plugin support for the delegated Gradle builds
call amper.bat --log-level=warn publish -m amper-android-gradle-plugin mavenLocal
if errorlevel 1 goto fail

cls
popd
if errorlevel 1 goto fail

REM ********** Launch Amper from unpacked dist **********

rem TODO generate the argfile in the unpacked distribution just like in the zipped distribution and use it here
"%AMPER_JAVA_HOME%\bin\java.exe" ^
  "-ea" ^
  "-XX:+EnableDynamicAgentLoading" ^
  "-XX:+UseCompactObjectHeaders" ^
  "--enable-native-access=ALL-UNNAMED" ^
  "--sun-misc-unsafe-memory-access=allow" ^
  "-Damper.wrapper.dist.sha256=%amper_sha256%" ^
  "-Damper.dist.path=%~dp0build\tasks\_amper-cli_buildUnpacked@amper-distribution\dist" ^
  "-Damper.wrapper.path=%~f0" ^
  %AMPER_JAVA_OPTIONS% ^
  -cp "%~dp0build\tasks\_amper-cli_buildUnpacked@amper-distribution\dist\lib\*" ^
  org.jetbrains.amper.cli.MainKt ^
  --build-output="build-from-sources" ^
  %*
exit /B %ERRORLEVEL%
