#!/usr/bin/env bash

# A bit of complex regex.
# Note: \w is handled differently within different sed implementations,
#       so [a-zA-Z0-9] is introduced.
make_sed() {
echo "s/(.*)($1)([a-zA-Z0-9\.\-]*)(.*)/\1\2$2\4/g"
}

# Replacement rules.
DEFT_VERSION="178-NIGHTLY"
DEFT_PATTERN="org\.jetbrains\.deft\.proto\.settings\.plugin:gradle-integration:"
DEFT_SED=$(make_sed $DEFT_PATTERN $DEFT_VERSION)

KMPP_VERSION="1.9.20-Beta"
KMPP_PATTERN="org\.jetbrains\.kotlin\.multiplatform:org\.jetbrains\.kotlin\.multiplatform\.gradle\.plugin:"
KMPP_SED=$(make_sed $KMPP_PATTERN $KMPP_VERSION)

COMPOSE_VERSION="1.5.10-beta01"
COMPOSE_PATTERN="org\.jetbrains\.compose:compose-gradle-plugin:"
COMPOSE_SED=$(make_sed $COMPOSE_PATTERN $COMPOSE_VERSION)

GRADLE_VERSION="8.1.1-bin.zip"
GRADLE_PATTERN="https\\\\\:\/\/services\.gradle\.org\/distributions\/gradle-"
GRADLE_SED=$(make_sed $GRADLE_PATTERN $GRADLE_VERSION)

# Meaningful suffix for sed old files.
OLD_FILES_POSTFIX="_OLD_FILE"

# Create temp files to store found files.
FOUND_FILES_FILE=$(mktemp /tmp/sync_versions_found_files.XXXXXX)
EDITED_FILES_FILE=$(mktemp /tmp/sync_versions_edited_files.XXXXXX)

# Find matching files..
find -E . -regex ".*(settings\.gradle\.kts|\.md|\.properties)$" | \
  grep -v "/build/" 1> $FOUND_FILES_FILE

# Do some logging.
echo "Found matching files:"
cat $FOUND_FILES_FILE
echo

# Perform replacement.
cat $FOUND_FILES_FILE | \
  xargs sed -i "$OLD_FILES_POSTFIX" -r "$DEFT_SED;$COMPOSE_SED;$KMPP_SED;$GRADLE_SED"

find -E . -regex ".*(settings\.gradle\.kts|\.md|\.properties)$OLD_FILES_POSTFIX$" | \
  grep -v "/build/" 1> $EDITED_FILES_FILE

# Remove temp sed files.
echo "Removing temp sed files"
cat $FOUND_FILES_FILE | \
  xargs -I{} rm -f "{}$OLD_FILES_POSTFIX"

# Remove temp files.
echo "Removing temp files"
rm $FOUND_FILES_FILE
rm $EDITED_FILES_FILE
