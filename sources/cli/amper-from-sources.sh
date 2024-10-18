#!/bin/bash

#
# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
#

### Runs amper cli from sources

set -e -u -o pipefail

script_dir="$(dirname -- "$0")"
script_dir="$(cd -- "$script_dir" && pwd)"

AMPER_JRE_DOWNLOAD_ROOT="${AMPER_JRE_DOWNLOAD_ROOT:-https:/}"

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

# ********** Provision JRE for Amper **********

if [ "x${AMPER_JAVA_HOME:-}" = "x" ]; then
  case $arch in
    x86_64 | x64)    jbr_arch="x64" ;;
    aarch64 | arm64) jbr_arch="aarch64" ;;
    *) die "Unsupported architecture $arch" ;;
  esac

  # Auto-updated from syncVersions.main.kts, do not modify directly here
  jbr_version=21.0.4
  jbr_build=b509.26

  # URL for JBR (vanilla) - see https://github.com/JetBrains/JetBrainsRuntime/releases
  jbr_url="$AMPER_JRE_DOWNLOAD_ROOT/cache-redirector.jetbrains.com/intellij-jbr/jbr-$jbr_version-$jbr_os-$jbr_arch-$jbr_build.tar.gz"
  jbr_target_dir="$amper_cache_dir/jbr-$jbr_version-$jbr_os-$jbr_arch-$jbr_build"

  platform="$jbr_os $jbr_arch"
  case $platform in
    "osx x64")         jbr_sha512=04ef2e808e691451c62b557e8f151aedbffca1a9d9c93d8eddff8f47f8cd3a1ad06dabb6ea908ae8a97616fc34d96a352319efd480bcfd7d024e80b5050e1343 ;;
    "osx aarch64")     jbr_sha512=fe7f85ed23a0cd47384e80ce99e5bb746fd0303ee197a0076ea026e5d7898c7e91a7189e0d23fbb18b26edd693e13507d29b5ffdfd08d4712e96c2303cf2ed70 ;;
    "linux x64")       jbr_sha512=b631092e911d84b6d2d116e3668a05b884e7a9696d2c67b842bfabc93bad9773671605934c4262ea647bcf3bbfaa2eb535d56d458510b9bc924471dd88296912 ;;
    "linux aarch64")   jbr_sha512=9136bf6b8bc2f10d750848dceacb25baeded3a05e4bc694a63602386738a72e81c578d5146d99a68fbb2cde04852a23e843539869bbbb6fb8794038a5e4a5061 ;;
    "windows x64")     jbr_sha512=6a639d23039b83cf1b0ed57d082bb48a9bff6acae8964192a1899e8a1c0915453199b501b498e5874bc57c9996d871d49f438054b3c86f643f1c1c4f178026a3 ;;
    "windows aarch64") jbr_sha512=9fd2333f3d55f0d40649435fc27e5ab97ad44962f54c1c6513e66f89224a183cd0569b9a3994d840b253060d664630610f82a02f45697e5e6c0b4ee250dd1857 ;;
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
