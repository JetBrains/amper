#!/usr/bin/env bash

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
BOOTSTRAP_AMPER_VERSION="0.5.0-dev-1117"
KOTLIN_VERSION="2.0.20"
KSP_VERSION="2.0.20-1.0.24"
COMPOSE_VERSION="1.6.10"
GRADLE_VERSION="8.6-bin.zip"
ANDROID_VERSION="8.2.0"

DIST_SHA256=$(curl -L -s "https://packages.jetbrains.team/maven/p/amper/amper/org/jetbrains/amper/cli/$BOOTSTRAP_AMPER_VERSION/cli-$BOOTSTRAP_AMPER_VERSION-dist.zip.sha256")

# --- Replacement rules ---
# Note: To add new rule with [add_update_rule] - add regex, that matches string inclusively right
# before the version (even quotes). Yet, you can add arbitrary sed rule, by using [append_to_sed_file].

# Amper

# For settings.gradle.kts in the Amper project itself, examples-gradle projects, and migrated-projects
# For Documentation.md, Usage.md, GradleMigration.md (instructions for setting up Gradle-based Amper)
# /!\ Should not be used on sources/gradle-integration/test/org/jetbrains/amper/gradle/util/otherUtil.kt
add_update_rule $BOOTSTRAP_AMPER_VERSION 'id\(\"org\.jetbrains\.amper\.settings\.plugin\"\)\.version\(\"'

# For Usage.md CLI download commands (URL part)
append_to_sed_file "s#(\/cli\/).*(\/.*)(-wrapper)#\\1$BOOTSTRAP_AMPER_VERSION\\2\\3#g"
# For Usage.md CLI download commands (filename)
# FIXME this could be merged with the previous one
append_to_sed_file "s#(cli-).*(-wrapper)#\\1$BOOTSTRAP_AMPER_VERSION\\2#g"
# For amper (sh) wrappers in examples-standalone and some test projects in amper-backend-test
# /!\ Should not be used on sources/cli/resources/wrappers/amper.template.sh with @AMPER_VERSION@
append_to_sed_file "s#^amper_version=.*#amper_version=$BOOTSTRAP_AMPER_VERSION#g"
# For amper.bat wrappers in examples-standalone and some test projects in amper-backend-test
# /!\ Should not be used on sources/cli/resources/wrappers/amper.template.bat with @AMPER_VERSION@
append_to_sed_file "s#^set amper_version=.*#set amper_version=$BOOTSTRAP_AMPER_VERSION#g"

# Amper dist sha256

# For amper (sh) wrappers in examples-standalone and some test projects in amper-backend-test
# /!\ Should not be used on sources/cli/resources/wrappers/amper.template.sh with @AMPER_VERSION@
append_to_sed_file "s#^amper_sha256=.*#amper_sha256=$DIST_SHA256#g"
# For amper.bat wrappers in examples-standalone and some test projects in amper-backend-test
# /!\ Should not be used on sources/cli/resources/wrappers/amper.template.bat with @AMPER_VERSION@
append_to_sed_file "s#^set amper_sha256=.*#set amper_sha256=$DIST_SHA256#g"

# Kotlin

# For UsedVersions.kt
add_update_rule $KOTLIN_VERSION "\/\*magic_replacement\*\/ val kotlinVersion = \""
# For Documentation.md and GradleMigration.md files
add_update_rule $KOTLIN_VERSION "org\.jetbrains\.kotlin\.multiplatform$SQUOTE *\| "
# For Documentation.md and GradleMigration.md files
add_update_rule $KOTLIN_VERSION "org\.jetbrains\.kotlin\.plugin\.serialization$SQUOTE *\| "
# For Documentation.md and GradleMigration.md files
add_update_rule $KOTLIN_VERSION "org\.jetbrains\.kotlin\.android$SQUOTE *\| "

# Compose

# For Documentation.md and GradleMigration.md files
add_update_rule $COMPOSE_VERSION "org\.jetbrains\.compose$SQUOTE *\| "
# For UsedVersions.kt
add_update_rule $COMPOSE_VERSION "\/\*magic_replacement\*\/ val composeVersion = \""

# Android

# For sources/android-integration/gradle-plugin/module.yaml
# For sources/gradle-integration/module.yaml
add_update_rule $ANDROID_VERSION "com\.android\.library:com\.android\.library\.gradle\.plugin:"
# For Documentation.md and GradleMigration.md files
add_update_rule $ANDROID_VERSION "com\.android\.library$SQUOTE *\| "
# For Documentation.md and GradleMigration.md files
add_update_rule $ANDROID_VERSION "com\.android\.application$SQUOTE *\| "

# Gradle

# For gradle-wrapper.properties in the Amper project itself, examples-gradle projects, and migrated-projects
add_update_rule $GRADLE_VERSION "https\\\\\:\/\/services\.gradle\.org\/distributions\/gradle-"

# KSP

# For UsedVersions.kt
add_update_rule $KSP_VERSION "\/\*magic_replacement\*\/ val kspVersion = \""

# --- Actual logic ---
# Meaningful suffix for sed old files.
OLD_FILES_POSTFIX="_OLD_FILE"

# Create temp files to store found files.
FOUND_FILES_FILE=$(mktemp /tmp/sync_versions_found_files.XXXXXX)
EDITED_FILES_FILE=$(mktemp /tmp/sync_versions_edited_files.XXXXXX)

# Find matching files..
AFFECTED_FILES_REGEX=".*(settings\.gradle\.kts|build\.gradle\.kts|UsedVersions\.kt|\.md|\.properties|module\.yaml|amper|amper\.bat)"

echo "Searching for matching files."
echo "  Files regex is \"$AFFECTED_FILES_REGEX\""

OSTYPE=$(uname -o | tr '[:upper:]' '[:lower:]')

if [[ "$OSTYPE" == "darwin"* ]]; then
  find -E . -regex "$AFFECTED_FILES_REGEX$" | \
      grep -v "/build/" 1> "$FOUND_FILES_FILE"
else
  find . -regextype posix-extended -regex "$AFFECTED_FILES_REGEX$" | \
    grep -v "/build/" 1> "$FOUND_FILES_FILE"
fi

FILES_COUNT=$(wc -l < "$FOUND_FILES_FILE" | tr -d ' ')
echo "  $FILES_COUNT matched files found."
echo "Performing replacement."

# Perform replacement.
cat "$FOUND_FILES_FILE" | \
  xargs sed -r -E -f "$SED_COMMANDS_FILE" -i "$OLD_FILES_POSTFIX" -r

if [[ "$OSTYPE" == "darwin"* ]]; then
  find -E . -regex "$AFFECTED_FILES_REGEX$OLD_FILES_POSTFIX$" | \
  grep -v "/build/" 1> "$EDITED_FILES_FILE"
else
  find . -regextype posix-extended -regex "$AFFECTED_FILES_REGEX$OLD_FILES_POSTFIX$" | \
    grep -v "/build/" 1> "$EDITED_FILES_FILE"
fi

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
