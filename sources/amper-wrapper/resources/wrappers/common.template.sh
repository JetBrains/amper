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
  show_banner_on_cache_miss="$7"

  if [ -e "$extract_dir/.flag" ] && [ "$(cat "$extract_dir/.flag")" = "${file_sha}" ]; then
    # Everything is up-to-date in $extract_dir, do nothing
    return 0;
  fi

  mkdir -p "$cache_dir"

  # Take a lock for the download of this file
  short_sha=$(echo "$file_sha" | cut -c1-32) # cannot use the ${short_sha:0:32} syntax in regular /bin/sh
  download_lock_file="$cache_dir/download-${short_sha}.lock"
  process_lock_file="$cache_dir/download-${short_sha}.$$.lock"
  echo $$ >"$process_lock_file"
  while ! ln "$process_lock_file" "$download_lock_file" 2>/dev/null; do
    lock_owner=$(cat "$download_lock_file" 2>/dev/null || true)
    # We use `kill -0` instead of `ps -p` as the first one is more portable
    if [ -n "$lock_owner" ] && kill -0 "$lock_owner" >/dev/null; then
      echo "Another Amper instance (pid $lock_owner) is downloading $moniker. Awaiting the result..."
      sleep 1
    elif [ -n "$lock_owner" ] && [ "$(cat "$download_lock_file" 2>/dev/null)" = "$lock_owner" ]; then
      rm -f "$download_lock_file"
      # We don't want to simply loop again here, because multiple concurrent processes may face this at the same time,
      # which means the 'rm' command above from another script could delete our new valid lock file. Instead, we just
      # ask the user to try again. This doesn't 100% eliminate the race, but the probability of issues is drastically
      # reduced because it would involve 4 processes with perfect timing. We can revisit this later.
      die "Another Amper instance (pid $lock_owner) locked the download of $moniker, but is no longer running. The lock file is now removed, please try again."
    fi
  done

  # shellcheck disable=SC2064
  trap "rm -f \"$download_lock_file\"" EXIT
  rm -f "$process_lock_file"

  unlock_and_cleanup() {
    rm -f "$download_lock_file"
    trap - EXIT
    return 0
  }

  if [ -e "$extract_dir/.flag" ] && [ "$(cat "$extract_dir/.flag")" = "${file_sha}" ]; then
    # Everything is up-to-date in $extract_dir, just release the lock
    unlock_and_cleanup
    return 0;
  fi

  if [ "$show_banner_on_cache_miss" = "true" ] && [ -z "${AMPER_NO_WELCOME_BANNER:-}" ]; then
      echo
      echo '        _____  Welcome to                                  '
      echo '       /:::::|  ____   ___     ____      ____    __  ___   '
      echo '      /::/|::| |::::\_|:::\   |:::::\   /::::\  |::|/:::|  '
      echo '     /::/ |::| |::|\:::|\::\  |::|\::\ /:/__\:\ |:::/      '
      echo '    /::/__|::| |::| |::| |::| |::| |::|:::::::/ |::|       '
      echo '   /:::::::::| |::| |::| |::| |::|/::/ \::\__   |::|       '
      echo '  /::/    |::| |::| |::| |::| |:::::/   \::::|  |::|       '
      echo '                              |::|                         '
      echo "                              |::|  v.$amper_version       "
      echo
      echo "This is the first run of this version, so we need to download the actual Amper distribution."
      echo "Please give us a few seconds, subsequent runs will be faster."
      echo
  fi

  echo "Downloading $moniker..."

  temp_file="$cache_dir/download-file-$$.bin"
  rm -f "$temp_file"
  if command -v curl >/dev/null 2>&1; then
    if [ -t 1 ]; then CURL_PROGRESS="--progress-bar"; else CURL_PROGRESS="--silent --show-error"; fi
    # shellcheck disable=SC2086
    curl $CURL_PROGRESS -L --fail --retry 5 --connect-timeout 30 --output "${temp_file}" "$file_url"
  elif command -v wget >/dev/null 2>&1; then
    if [ -t 1 ]; then WGET_PROGRESS=""; else WGET_PROGRESS="-nv"; fi
    wget $WGET_PROGRESS --tries=5 --connect-timeout=30 --read-timeout=120 -O "${temp_file}" "$file_url"
  else
    die "ERROR: Please install 'wget' or 'curl', as Amper needs one of them to download $moniker"
  fi

  check_sha "$file_url" "$temp_file" "$file_sha" "$sha_size"

  rm -rf "$extract_dir"
  mkdir -p "$extract_dir"

  case "$file_url" in
    *".zip")
      if command -v unzip >/dev/null 2>&1; then
        unzip -q "$temp_file" -d "$extract_dir"
      else
        die "ERROR: Please install 'unzip', as Amper needs it to extract $moniker"
      fi ;;
    *)
      if command -v tar >/dev/null 2>&1; then
        tar -x -f "$temp_file" -C "$extract_dir"
      else
        die "ERROR: Please install 'tar', as Amper needs it to extract $moniker"
      fi ;;
  esac

  rm -f "$temp_file"

  echo "$file_sha" >"$extract_dir/.flag"

  # Unlock and cleanup the lock file
  unlock_and_cleanup

  echo "Download complete."
  echo
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
      die "ERROR: Checksum mismatch for $2 (downloaded from $1): expected checksum $3 but got $(shasum --binary -a "$sha_size" "$2" | awk '{print $1}')"
    }
    return 0
  fi

  shaNsumCommand="sha${sha_size}sum"
  if command -v "$shaNsumCommand" >/dev/null 2>&1; then
    # discard the output as sha*sum may print redundant warnings in some versions
    echo "$3 *$2" | $shaNsumCommand -w -c >/dev/null 2>&1 || {
      die "ERROR: Checksum mismatch for $2 (downloaded from $1): expected checksum $3 but got $($shaNsumCommand "$2" | awk '{print $1}')"
    }
    return 0
  fi

  echo "Both 'shasum' and 'sha${sha_size}sum' utilities are missing. Please install one of them"
  return 1
}
