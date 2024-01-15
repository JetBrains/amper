#!/bin/bash

### Runs amper cli from sources

set -e -u -o pipefail

script_dir="$(dirname -- "$0")"
script_dir="$(cd -- "$script_dir" && pwd)"

# do not call gradlew if nothing changed
current_state() {
  find "$script_dir" '(' -name build -o -name .git -o -name .idea ')' -prune -o -type f -print0 | \
   xargs -0 stat -f '%N %m %z'
}

up_to_date_file="$script_dir/build/up-to-date-from-sources-wrapper.txt"
current="$(current_state)"
if [ ! -f "$up_to_date_file" ] || [ "$(cat "$up_to_date_file")" != "$current" ]; then
  (cd "$script_dir/../.." && ./gradlew --quiet :sources:cli:unpackedDistribution)
  current_state >"$up_to_date_file"
fi

time java -ea -cp "$script_dir/build/unpackedDistribution/lib/*" org.jetbrains.amper.cli.MainKt "$@"
