#!/bin/bash

#
# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
#

### Runs amper cli from sources

set -e -u -o pipefail

#region COPIED FROM REAL WRAPPER
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

    echo "$moniker will now be provisioned because this is the first run with this version. Subsequent runs will skip this step and be faster."
    echo "Downloading $file_url"

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

    echo "Extracting to $extract_dir"
    rm -rf "$extract_dir"
    mkdir -p "$extract_dir"

    case "$file_url" in
      *".zip") unzip -q "$temp_file" -d "$extract_dir" ;;
      *) tar -x -f "$temp_file" -C "$extract_dir" ;;
    esac

    rm -f "$temp_file"

    echo "$file_url" >"$extract_dir/.flag"
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

# ********** Build Amper distribution **********

case "$(uname)" in
  # man stat on Mac OS X
  # N       The name of the file.
  # z       The size of file in bytes (st_size).
  # a, m, c, B
  #         The time file was last accessed or modified, or when the
  #         inode was last changed, or the birth time of the inode
  #         (st_atime, st_mtime, st_ctime, st_birthtime).
  Darwin*) stat_format=(-f '%N %m %z') ;;

  # man stat on Ubuntu and Windows (Git Bash)
  # %n     file name
  # %s     total size, in bytes
  # %Y     time of last data modification, seconds since Epoch
  Linux* | CYGWIN* | MSYS* | MINGW*) stat_format=(--format='%n %s %Y') ;;

  *)
    echo "Unsupported system: $(uname)"
    exit 1
    ;;
esac

# do not call gradlew if nothing changed
current_state() {
  # scan sources directory
  find "$script_dir/.." '(' -name build -o -name .git -o -name .idea ')' -prune -o -type f -print0 | \
   xargs -0 stat "${stat_format[@]}"
  stat "${stat_format[@]}" "$script_dir/../../project.yaml"
}

up_to_date_file="$script_dir/build/up-to-date-from-sources-wrapper.txt"
current="$(current_state)"
if [ ! -f "$up_to_date_file" ] || [ "$(cat "$up_to_date_file")" != "$current" ]; then
  (cd "$script_dir/../.." && ./gradlew --stacktrace --quiet :sources:cli:prepareForLocalRun)
  current_state >"$up_to_date_file"
fi

# ********** Launch Amper **********

if [ "$simpleOs" = "windows" ]; then
  # Can't cygpath the *
  classpath="$(cygpath -w "$script_dir/build/unpackedDistribution/lib")\*"
else
  classpath="$script_dir/build/unpackedDistribution/lib/*"
fi
time "$java_exe" -ea -XX:+EnableDynamicAgentLoading "-Damper.wrapper.process.name=$0" -cp "$classpath" org.jetbrains.amper.cli.MainKt "$@"
