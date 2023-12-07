#!/usr/bin/env bash

# --- Utils ---
SQUOTE=\`

# Create temp file to store sed commands.
SED_COMMANDS_FILE=$(mktemp /tmp/used_sed_commands.XXXXXX)

append_to_sed_file() {
  echo "$1" >> "$SED_COMMANDS_FILE"
}

# A bit of complex regex.
# Note: \w is handled differently within different sed implementations,
#       so [a-zA-Z0-9] is introduced.
add_update_rule() {
  append_to_sed_file "s/(.*)($2)([a-zA-Z0-9\.\-]*)(.*)/\1\2$1\4/g"
}

# --- Used versions ---
BOOTSTRAP_AMPER_VERSION="0.2.0-dev-329"
KOTLIN_VERSION="1.9.20"
COMPOSE_VERSION="1.5.10"
GRADLE_VERSION="8.1.1-bin.zip"
ANDROID_VERSION="8.1.0"

# --- Replacement rules ---
# Note: To add new rule with [add_update_rule] - add regex, that matches string inclusively right
# before the version (even quotes). Yet, you can add arbitrary sed rule, by using [append_to_sed_file].

# Amper
add_update_rule $BOOTSTRAP_AMPER_VERSION "org\.jetbrains\.amper\.settings\.plugin:gradle-integration:"
add_update_rule $BOOTSTRAP_AMPER_VERSION 'id\(\"org\.jetbrains\.amper\.settings\.plugin\"\)\.version\(\"'

# Kotlin
add_update_rule $KOTLIN_VERSION "org\.jetbrains\.kotlin\.multiplatform:org\.jetbrains\.kotlin\.multiplatform\.gradle\.plugin:"
add_update_rule $KOTLIN_VERSION "org\.jetbrains\.kotlin\.android:org\.jetbrains\.kotlin\.android\.gradle\.plugin:"
add_update_rule $KOTLIN_VERSION "\/\*magic_replacement\*\/ val kotlinVersion = \""
add_update_rule $KOTLIN_VERSION "\/\*kotlin_magic_replacement\*\/ strictly\(\""
add_update_rule $KOTLIN_VERSION "org\.jetbrains\.kotlin\.multiplatform$SQUOTE *\| "
add_update_rule $KOTLIN_VERSION "org\.jetbrains\.kotlin\.plugin\.serialization$SQUOTE *\| "
add_update_rule $KOTLIN_VERSION "org\.jetbrains\.kotlin\.android$SQUOTE *\| "
add_update_rule $KOTLIN_VERSION "org\.jetbrains\.kotlin:kotlin-reflect:"

# Compose
add_update_rule $COMPOSE_VERSION "org\.jetbrains\.compose:compose-gradle-plugin:"
add_update_rule $COMPOSE_VERSION "org\.jetbrains\.compose$SQUOTE *\| "
add_update_rule $COMPOSE_VERSION "\/\*magic_replacement\*\/ val composeVersion = \""

#Android
add_update_rule $ANDROID_VERSION "com\.android\.library:com\.android\.library\.gradle\.plugin:"
add_update_rule $ANDROID_VERSION "com\.android\.library$SQUOTE *\| "
add_update_rule $ANDROID_VERSION "com\.android\.application$SQUOTE *\| "
add_update_rule $ANDROID_VERSION "\/\*magic_replacement\*\/ val androidVersion = \""

# Gradle
add_update_rule $GRADLE_VERSION "https\\\\\:\/\/services\.gradle\.org\/distributions\/gradle-"

# --- Actual logic ---
# Meaningful suffix for sed old files.
OLD_FILES_POSTFIX="_OLD_FILE"

# Create temp files to store found files.
FOUND_FILES_FILE=$(mktemp /tmp/sync_versions_found_files.XXXXXX)
EDITED_FILES_FILE=$(mktemp /tmp/sync_versions_edited_files.XXXXXX)

# Find matching files..
AFFECTED_FILES_REGEX=".*(settings\.gradle\.kts|build\.gradle\.kts|UsedVersions\.kt|\.md|\.properties|module\.yaml)"

echo "Searching for matching files."
echo "  Files regex is \"$AFFECTED_FILES_REGEX\""

find -E . -regex "$AFFECTED_FILES_REGEX$" | \
  grep -v "/build/" 1> "$FOUND_FILES_FILE"

FILES_COUNT=$(wc -l < "$FOUND_FILES_FILE" | tr -d ' ')
echo "  $FILES_COUNT matched files found."
echo "Performing replacement."

# Perform replacement.
cat "$FOUND_FILES_FILE" | \
  xargs sed -f "$SED_COMMANDS_FILE" -i "$OLD_FILES_POSTFIX" -r

find -E . -regex "$AFFECTED_FILES_REGEX$OLD_FILES_POSTFIX$" | \
  grep -v "/build/" 1> "$EDITED_FILES_FILE"

echo "  Done."

# Remove temp sed files.
echo "  Removing temp sed files."
cat "$FOUND_FILES_FILE" | \
  xargs -I{} rm -f "{}$OLD_FILES_POSTFIX"

# Remove temp files.
echo "  Removing temp files."
rm "$FOUND_FILES_FILE"
rm "$EDITED_FILES_FILE"
rm "$SED_COMMANDS_FILE"
