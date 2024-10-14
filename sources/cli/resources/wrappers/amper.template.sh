#!/bin/sh

#
# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
#

# TODO gradlew also tries to set ulimit -n (max files), probably we should too
# TODO Script could be run in parallel for the first time, so download/extract code should not fail in that case

# Possible environment variables:
#   AMPER_DOWNLOAD_ROOT        Maven repository to download Amper dist from.
#                              default: https://packages.jetbrains.team/maven/p/amper/amper
#   AMPER_JRE_DOWNLOAD_ROOT    Url prefix to download Amper JRE from.
#                              default: https:/
#   AMPER_BOOTSTRAP_CACHE_DIR  Cache directory to store extracted JRE and Amper distribution
#   AMPER_JAVA_HOME            JRE to run Amper itself (optional, does not affect compilation)

set -e -u

# The version of the Amper distribution to provision and use
amper_version=@AMPER_VERSION@
# Establish chain of trust from here by specifying exact checksum of Amper distribution to be run
amper_sha256=@AMPER_DIST_SHA256@

AMPER_DOWNLOAD_ROOT="${AMPER_DOWNLOAD_ROOT:-https://packages.jetbrains.team/maven/p/amper/amper}"
AMPER_JRE_DOWNLOAD_ROOT="${AMPER_JRE_DOWNLOAD_ROOT:-https:/}"

script_dir="$(dirname -- "$0")"
script_dir="$(cd -- "$script_dir" && pwd)"

die() {
  echo >&2
  echo "$@" >&2
  echo >&2
  exit 1
}

download_and_extract() {
  moniker="$1"
  file_url="$2"
  file_sha="$3"
  sha_size="$4"
  cache_dir="$5"
  extract_dir="$6"

  if [ -e "$extract_dir/.flag" ] && [ -n "$(ls "$extract_dir")" ] && [ "x$(cat "$extract_dir/.flag")" = "x${file_url}" ]; then
    # Everything is up-to-date in $extract_dir, do nothing
    true
  else
    mkdir -p "$cache_dir"
    temp_file="$cache_dir/download-file-$$.bin"

    echo "Downloading $moniker... (only happens on the first run of this version)"

    rm -f "$temp_file"
    if command -v curl >/dev/null 2>&1; then
      if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS="--silent --show-error"; fi
      # shellcheck disable=SC2086
      curl $CURL_PROGRESS -L --fail --output "${temp_file}" "$file_url" 2>&1
    elif command -v wget >/dev/null 2>&1; then
      if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
      wget $WGET_PROGRESS -O "${temp_file}" "$file_url" 2>&1
    else
      die "ERROR: Please install wget or curl"
    fi

    check_sha "$file_url" "$temp_file" "$file_sha" "$sha_size"

    rm -rf "$extract_dir"
    mkdir -p "$extract_dir"

    case "$file_url" in
      *".zip") unzip -q "$temp_file" -d "$extract_dir" ;;
      *) tar -x -f "$temp_file" -C "$extract_dir" ;;
    esac

    rm -f "$temp_file"

    echo "$file_url" >"$extract_dir/.flag"
    echo "Downloaded to $extract_dir"
    echo
  fi
}

# usage: check_sha SOURCE_MONIKER FILE SHA_CHECKSUM SHA_SIZE
# $1 SOURCE_MONIKER (e.g. url)
# $2 FILE
# $3 SHA hex string
# $4 SHA size in bits (256, 512, ...)
check_sha() {
  sha_size=$4
  if command -v shasum >/dev/null 2>&1; then
    echo "$3 *$2" | shasum -a "$sha_size" --status -c || {
      echo "$2 (downloaded from $1):" >&2
      echo "expected checksum $3 but got: $(shasum --binary -a "$sha_size" "$2" | awk '{print $1}')" >&2

      die "ERROR: Checksum mismatch for $1"
    }
    return 0
  fi

  shaNsumCommand="sha${sha_size}sum"
  if command -v "$shaNsumCommand" >/dev/null 2>&1; then
    echo "$3 *$2" | $shaNsumCommand -w -c || {
      echo "$2 (downloaded from $1):" >&2
      echo "expected checksum $3 but got: $($shaNsumCommand "$2" | awk '{print $1}')" >&2

      die "ERROR: Checksum mismatch for $1"
    }
    return 0
  fi

  echo "Both 'shasum' and 'sha${sha_size}sum' utilities are missing. Please install one of them"
  return 1
}

# ********** System detection **********

kernelName=$(uname -s)
arch=$(uname -m)
case "$kernelName" in
  Darwin* )
    simpleOs="macos"
    jbr_os="osx"
    default_amper_cache_dir="$HOME/Library/Caches/Amper"
    ;;
  Linux* )
    simpleOs="linux"
    jbr_os="linux"
    default_amper_cache_dir="$HOME/.cache/Amper"
    # If linux runs in 32-bit mode, we want the "fake" 32-bit architecture, not the real hardware,
    # because in this mode linux cannot run 64-bit binaries.
    # shellcheck disable=SC2046
    arch=$(linux$(getconf LONG_BIT) uname -m)
    ;;
  CYGWIN* | MSYS* | MINGW* )
    simpleOs="windows"
    jbr_os="windows"
    if command -v cygpath >/dev/null 2>&1; then
      default_amper_cache_dir=$(cygpath -u "$LOCALAPPDATA\Amper")
    else
      die "The 'cypath' command is not available, but Amper needs it. Use amper.bat instead, or try a Cygwin or MSYS environment."
    fi
    ;;
  *)
    die "Unsupported platform $kernelName"
    ;;
esac

# TODO should we respect --shared-caches-root instead of (or in addition to) this env var?
amper_cache_dir="${AMPER_BOOTSTRAP_CACHE_DIR:-$default_amper_cache_dir}"

# ********** Provision Amper distribution **********

amper_url="$AMPER_DOWNLOAD_ROOT/org/jetbrains/amper/cli/$amper_version/cli-$amper_version-dist.zip"
amper_target_dir="$amper_cache_dir/amper-cli-$amper_version"
download_and_extract "Amper distribution v$amper_version" "$amper_url" "$amper_sha256" 256 "$amper_cache_dir" "$amper_target_dir"

# ********** Provision JRE for Amper **********

if [ "x${AMPER_JAVA_HOME:-}" = "x" ]; then
  case $arch in
    x86_64 | x64)    jbr_arch="x64" ;;
    aarch64 | arm64) jbr_arch="aarch64" ;;
    *) die "Unsupported architecture $arch" ;;
  esac

  # Auto-updated from syncVersions.main.kts, do not modify directly here
  jbr_version=21.0.4
  jbr_build=b620.4

  # URL for JBR (vanilla) - see https://github.com/JetBrains/JetBrainsRuntime/releases
  jbr_url="$AMPER_JRE_DOWNLOAD_ROOT/cache-redirector.jetbrains.com/intellij-jbr/jbr-$jbr_version-$jbr_os-$jbr_arch-$jbr_build.tar.gz"
  jbr_target_dir="$amper_cache_dir/jbr-$jbr_version-$jbr_os-$jbr_arch-$jbr_build"

  platform="$jbr_os $jbr_arch"
  case $platform in
    "osx x64")         jbr_sha512=639f1aea921c81562f3b6ed719bb28c801efba6c3c62058d3fa5bea8f598cb5ad829f76e67d3a7dda03da4dd96fb60e8f288c0035a707326e7c3c6028cda3278 ;;
    "osx aarch64")     jbr_sha512=60594a6ca2560f8fd396343ca21cbba0bd5d207cbc082d2720b3ccee8928f1115a19c4bf737390e224be4b3613e0aca6cd8ab15ccb1f493a9d15cfae13934934 ;;
    "linux x64")       jbr_sha512=a633ad234d51390b147884ba86d58f00cddf4fdd23fc398021c3f3298355f2203dd64415d29bb6b83566e962b0cc8755ea8d397554e56e6ed0fc36d070541b7c ;;
    "linux aarch64")   jbr_sha512=759aebca9d32ab02c6fa2fee60fbc233db3aa365e29ef0d8f199ca72b598ecff18fb6ae115d2f14d2fcb15a9e0ced23705fd917501a2787940cf263eab6167ae ;;
    "windows x64")     jbr_sha512=efd631caad56b0c8c5439f4b275b564e827e40638194eb2e8754d849559edc363f13a9ea5f4a13e7dbd2b7f980524ed7efdb674c2f04e90045df988beb2527ee ;;
    "windows aarch64") jbr_sha512=cbad6f07fd8392ad96745e9cd37712485bc8cc876d101b3a2551e53c005e5e06449695c184e5670e1d9bcbc58a659219893930c082ca800666f3264c7b982032 ;;
    *) die "Unsupported platform $platform" ;;
  esac

  download_and_extract "JetBrains Runtime v$jbr_version$jbr_build" "$jbr_url" "$jbr_sha512" 512 "$amper_cache_dir" "$jbr_target_dir"

  AMPER_JAVA_HOME=
  for d in "$jbr_target_dir" "$jbr_target_dir"/* "$jbr_target_dir"/Contents/Home "$jbr_target_dir"/*/Contents/Home; do
    if [ -e "$d/bin/java" ]; then
      AMPER_JAVA_HOME="$d"
    fi
  done

  if [ "x${AMPER_JAVA_HOME:-}" = "x" ]; then
    die "Unable to find bin/java under $jbr_target_dir"
  fi
fi

java_exe="$AMPER_JAVA_HOME/bin/java"
if [ '!' -x "$java_exe" ]; then
  die "Unable to find bin/java executable at $java_exe"
fi

# ********** Launch Amper **********

if [ "$simpleOs" = "windows" ]; then
  # Can't cygpath the '*' so it has to be outside
  classpath="$(cygpath -w "$amper_target_dir")\lib\*"
else
  classpath="$amper_target_dir/lib/*"
fi
exec "$java_exe" -ea -XX:+EnableDynamicAgentLoading "-Damper.wrapper.dist.sha256=$amper_sha256" "-Damper.wrapper.process.name=$0" -cp "$classpath" org.jetbrains.amper.cli.MainKt "$@"
