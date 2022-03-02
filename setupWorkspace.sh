#!/bin/sh

# Used to setup workspace and fix building on unix / Git BASH
#
# Usage: "./setupWorkspace.sh"

SECONDS=0

# To allow use from outside the lambda directory
cd "$(dirname "$0")" || exit

echo "Running gradlew classes without daemon..."
./gradlew --no-daemon classes || {
  echo "ERROR: Running gradlew build failed! Run './gradlew --no-daemon classes' manually"
  exit 1
}

# Generates InteliJ runs so the user doesn't have to do that manually
echo "Generating InteliJ runs"
./gradlew --no-daemon genIntellijRuns || {
  echo "ERROR: Failure generating InteliJ runs! try generating them manually from within IntelliJ"
  exit 1
}

cat logo_ascii.txt 2>/dev/null
echo "=========================================================================="
echo ""
# shellcheck disable=SC2039
echo "Build succeeded in $((SECONDS / 60)) minutes and $((SECONDS % 60)) seconds! All checks passed, you can build normally now! Welcome to Lambda."
echo ""
echo "=========================================================================="
