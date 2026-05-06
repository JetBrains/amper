#!/bin/sh

#
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
#

set -e -u

AMPER_JRE_DOWNLOAD_ROOT="${AMPER_JRE_DOWNLOAD_ROOT:-https:/}"

@include:common.template.sh@

# ********** System detection **********

kernelName=$(uname -s)
arch=$(uname -m)
case "$kernelName" in
  Darwin* )
    simpleOs="macos"
    jre_os="macosx"
    jre_archive_type="tar.gz"
    default_amper_cache_dir="$HOME/Library/Caches/JetBrains/Amper"
    ;;
  Linux* )
    simpleOs="linux"
    jre_os="linux"
    jre_archive_type="tar.gz"
    default_amper_cache_dir="$HOME/.cache/JetBrains/Amper"
    # If linux runs in 32-bit mode, we want the "fake" 32-bit architecture, not the real hardware,
    # because in this mode linux cannot run 64-bit binaries.
    # shellcheck disable=SC2046
    arch=$(linux$(getconf LONG_BIT) uname -m)
    ;;
  CYGWIN* | MSYS* | MINGW* )
    simpleOs="windows"
    jre_os="win"
    jre_archive_type=zip
    if command -v cygpath >/dev/null 2>&1; then
      default_amper_cache_dir=$(cygpath -u "$LOCALAPPDATA\JetBrains\Amper")
    else
      die "The 'cygpath' command is not available, but Amper needs it. Use amper.bat instead, or try a Cygwin or MSYS environment."
    fi
    ;;
  Windows_NT* ) # this is busybox-w32
    simpleOs="windows"
    jre_os="win"
    jre_archive_type=zip
    # no need to `cygpath`:
    # a) there is no such thing on busybox-w32
    # b) it's not needed: the env vars already contain converted paths
    default_amper_cache_dir="$LOCALAPPDATA/JetBrains/Amper"
    ;;
  *)
    die "Unsupported platform $kernelName"
    ;;
esac

amper_cache_dir="${AMPER_BOOTSTRAP_CACHE_DIR:-$default_amper_cache_dir}"

# ********** Provision JRE for Amper **********

if [ "x${AMPER_JAVA_HOME:-}" = "x" ]; then
  case $arch in
    x86_64 | x64)    jre_arch="x64" ;;
    aarch64 | arm64) jre_arch="aarch64" ;;
    *) die "Unsupported architecture $arch" ;;
  esac

  # Auto-updated from syncVersions.main.kts, do not modify directly here
  zulu_version=25.32.21
  java_version=25.0.2

  platform="$jre_os $jre_arch"
  case $platform in
    "macosx x64")     jre_sha256=cb23779ae726b160fdd6af58cb3a8db2dec8fc3f3c21e27dafa30cc148798346 ;;
    "macosx aarch64") jre_sha256=5c3edffe2d3b9203d838ed0548035f08be2b9f672544bab2b9c1825c309d0be7 ;;
    "linux x64")      jre_sha256=851e913928d968df05996c95f83a4a9567b463143ce9b84dd32b68035e22cf81 ;;
    "linux aarch64")  jre_sha256=cc70d2d46851192288ed7c9de1d6aab07eedeac9a369d54f299202152e2b52ec ;;
    "win x64")        jre_sha256=a4b7e3c3929d513cdc774583d375ce07fcb8671833258f468fd2fa0d8227ba48 ;;
    "win aarch64")    jre_sha256=1106eec3bd166a117ccaf20f15bbec6537e27307be328b8a9e93a053c857fe7c ;;
    *) die "Unsupported platform $platform" ;;
  esac

  # URL for the JRE (see https://api.azul.com/metadata/v1/zulu/packages?release_status=ga&include_fields=java_package_features,os,arch,hw_bitness,abi,java_package_type,sha256_hash,size,archive_type,lib_c_type&java_version=25&os=macos,linux,win)
  # https://cdn.azul.com/zulu/bin/zulu25.28.85-ca-jre25.0.0-macosx_aarch64.tar.gz
  # https://cdn.azul.com/zulu/bin/zulu25.28.85-ca-jre25.0.0-linux_x64.tar.gz
  jre_url="$AMPER_JRE_DOWNLOAD_ROOT/cdn.azul.com/zulu/bin/zulu$zulu_version-ca-jre$java_version-${jre_os}_$jre_arch.$jre_archive_type"
  jre_target_dir="$amper_cache_dir/zulu$zulu_version-ca-jre$java_version-${jre_os}_$jre_arch"

  download_and_extract "Amper runtime v$zulu_version" "$jre_url" "$jre_sha256" 256 "$amper_cache_dir" "$jre_target_dir" "false"

  if [ "$simpleOs" = "windows" ]; then
    java_binary="java.exe"
  else
    java_binary="java"
  fi

  effective_amper_java_home=
  for d in "$jre_target_dir" "$jre_target_dir"/* "$jre_target_dir"/Contents/Home "$jre_target_dir"/*/Contents/Home; do
    if [ -e "$d/bin/$java_binary" ]; then
      effective_amper_java_home="$d"
    fi
  done

  if [ "x${effective_amper_java_home:-}" = "x" ]; then
    die "Unable to find bin/$java_binary under $jre_target_dir"
  fi
else
  effective_amper_java_home="$AMPER_JAVA_HOME"
fi

if [ "$simpleOs" = "windows" ]; then
  java_exe="$effective_amper_java_home/bin/java.exe"
else
  java_exe="$effective_amper_java_home/bin/java"
fi
if [ '!' -x "$java_exe" ]; then
  die "Unable to find bin/java executable at $java_exe"
fi

# ********** Distribution path detection **********

# We might need to resolve symbolic links here
script_path=$(realpath "$0")
# $0 = "$amper_dist_dir/bin/launcher.sh", so need two levels higher to get to the root.
amper_dist_dir=$(dirname "$(dirname "$script_path")")

# ********** Launch Amper **********

# In this section we construct the command line by prepending arguments from biggest to lowest precedence:
#   1. basic main class, CLI arguments, and classpath
#   2. user JVM args (AMPER_JAVA_OPTIONS)
#   3. default JVM args (prepended last, which means they appear first, so they are overridden by user args)

# 1. Prepend basic launch arguments
case "$kernelName" in
  CYGWIN* | MSYS* | MINGW* )
    # Can't cygpath the '*' so it has to be outside
    classpath="$(cygpath -w "$amper_dist_dir")\lib\*"
    ;;
  *)
    # Works for Unix paths (/foo/bar/lib/*) and Windows paths under busybox (C:/foo/bar/lib/*)
    classpath="$amper_dist_dir/lib/*"
    ;;
esac

set -- -cp "$classpath" org.jetbrains.amper.cli.MainKt "$@"

# 2. Prepend user JVM args from AMPER_JAVA_OPTS
#
# We use "xargs" to parse quoted JVM args from inside AMPER_JAVA_OPTS.
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
if [ -n "${AMPER_JAVA_OPTIONS:-}" ]; then
  eval "set -- $(
    printf '%s\n' "$AMPER_JAVA_OPTIONS" |
    xargs -n1 |
    sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
    tr '\n' ' '
  )" '"$@"'
fi

# 3. Prepend default JVM args
set -- \
    @"$amper_dist_dir/amper.args" \
    "$@"

# Then we can launch with the overridden $@ arguments
AMPER_DISTRIBUTION_DIR="$amper_dist_dir" \
exec "$java_exe" "$@"
