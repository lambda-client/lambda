#!/bin/sh

# Used to setup workspace and fix building on unix / Git BASH
#
# Usage: "./setupWorkspace.sh"

#

# To allow use from outside the lambda directory
cd "$(dirname "$0")" || exit

echo "[$(date +"%H:%M:%S")] Running gradlew classes without daemon..."
./gradlew --no-daemon classes || {
  echo "[$(date +"%H:%M:%S")] ERROR: Running gradlew build failed! Run './gradlew --no-daemon classes' manually"
  exit 1
}

cat logo_ascii.txt 2>/dev/null
echo "=========================================================================="
echo ""
echo "[$(date +"%H:%M:%S")] Build succeeded! All checks passed, you can build normally now! Welcome to Lambda."
echo ""
echo "=========================================================================="
