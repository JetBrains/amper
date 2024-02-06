#!/bin/bash

#
# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
#

### Runs amper cli from sources

set -e -u -o pipefail

script_dir="$(dirname -- "$0")"
script_dir="$(cd -- "$script_dir" && pwd)"

case "$(uname)" in
  # man stat on Mac OS X
  # N       The name of the file.
  # z       The size of file in bytes (st_size).
  # a, m, c, B
  #         The time file was last accessed or modified, or when the
  #         inode was last changed, or the birth time of the inode
  #         (st_atime, st_mtime, st_ctime, st_birthtime).
  Darwin*) stat_format=(-f '%N %m %z') ;;

  # man stat on Ubuntu
  # %n     file name
  # %s     total size, in bytes
  # %Y     time of last data modification, seconds since Epoch
  Linux*) stat_format=(--format='%n %s %Y') ;;

  *)
    echo "Unsupported system: $(uname)"
    exit 1
    ;;
esac

# do not call gradlew if nothing changed
current_state() {
  find "$script_dir" '(' -name build -o -name .git -o -name .idea ')' -prune -o -type f -print0 | \
   xargs -0 stat "${stat_format[@]}"
}

up_to_date_file="$script_dir/build/up-to-date-from-sources-wrapper.txt"
current="$(current_state)"
if [ ! -f "$up_to_date_file" ] || [ "$(cat "$up_to_date_file")" != "$current" ]; then
  (cd "$script_dir/../.." && ./gradlew --quiet :sources:cli:unpackedDistribution)
  current_state >"$up_to_date_file"
fi

time java -ea -cp "$script_dir/build/unpackedDistribution/lib/*" org.jetbrains.amper.cli.MainKt "$@"
