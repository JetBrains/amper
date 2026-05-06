#!/bin/sh

#
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
#

# Possible environment variables:
#   AMPER_DOWNLOAD_ROOT        Maven repository to download Amper dist from.
#                              default: https://packages.jetbrains.team/maven/p/amper/amper
#   AMPER_JRE_DOWNLOAD_ROOT    Url prefix to download Amper JRE from.
#                              default: https:/
#   AMPER_BOOTSTRAP_CACHE_DIR  Cache directory to store extracted JRE and Amper distribution
#   AMPER_JAVA_HOME            JRE to run Amper itself (optional, does not affect compilation)
#   AMPER_JAVA_OPTIONS         JVM options to pass to the JVM running Amper (does not affect the user's application)
#   AMPER_NO_WELCOME_BANNER    Disables the first-run welcome message if set to a non-empty value

set -e -u

# The version of the Amper distribution to provision and use
amper_version=@AMPER_VERSION@
# Establish chain of trust from here by specifying exact checksum of Amper distribution to be run
amper_sha256=@AMPER_DIST_TGZ_SHA256@

AMPER_DOWNLOAD_ROOT="${AMPER_DOWNLOAD_ROOT:-https://packages.jetbrains.team/maven/p/amper/amper}"

@include:common.template.sh@

# ********** System detection **********

kernelName=$(uname -s)
case "$kernelName" in
  Darwin* )
    default_amper_cache_dir="$HOME/Library/Caches/JetBrains/Amper"
    ;;
  Linux* )
    default_amper_cache_dir="$HOME/.cache/JetBrains/Amper"
    ;;
  CYGWIN* | MSYS* | MINGW* )
    if command -v cygpath >/dev/null 2>&1; then
      default_amper_cache_dir=$(cygpath -u "$LOCALAPPDATA\JetBrains\Amper")
    else
      die "The 'cypath' command is not available, but Amper needs it. Use amper.bat instead, or try a Cygwin or MSYS environment."
    fi
    ;;
  *)
    die "Unsupported platform $kernelName"
    ;;
esac

amper_cache_dir="${AMPER_BOOTSTRAP_CACHE_DIR:-$default_amper_cache_dir}"

# ********** Provision Amper distribution **********

amper_url="$AMPER_DOWNLOAD_ROOT/org/jetbrains/amper/amper-cli/$amper_version/amper-cli-$amper_version-dist.tgz"
amper_target_dir="$amper_cache_dir/amper-cli-$amper_version"
download_and_extract "Amper distribution v$amper_version" "$amper_url" "$amper_sha256" 256 "$amper_cache_dir" "$amper_target_dir" "true"

# ********** Launch Amper **********

launcher_script="$amper_target_dir/bin/launcher.sh"

AMPER_WRAPPER_PATH="$(realpath "$0")" \
exec /bin/sh "$launcher_script" "$@"
