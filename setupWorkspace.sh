#!/bin/sh

# Used to setup workspace and fix building on unix / Git BASH
#
# Usage: "./setupWorkspace.sh"

#

# To allow use from outside the lambda directory
cd "$(dirname "$0")" || exit

echo "[$(date +"%H:%M:%S")] Checking if git is installed..."
if [ -z "$(which git)" ]; then
  echo "[$(date +"%H:%M:%S")] ERROR: Git is not installed, please make sure you install the CLI version of git, not some desktop wrapper for it" >&2
  exit 1
fi
echo "[$(date +"%H:%M:%S")] Git is installed!"

#

echo "[$(date +"%H:%M:%S")] Checking for .git dir..."
if [ ! -d ".git" ]; then
  echo "[$(date +"%H:%M:%S")] ERROR: Could not detect git repository, exiting" >&2
  exit 1
fi
echo "[$(date +"%H:%M:%S")] Found git repository!"

#

echo "[$(date +"%H:%M:%S")] Downloading git submodules..."
git submodule update --init --recursive || {
  echo "[$(date +"%H:%M:%S")] ERROR: Failed to init git submodules"
  exit 1
}
echo "[$(date +"%H:%M:%S")] Downloaded git submodules!"

#

echo "[$(date +"%H:%M:%S")] Running gradlew classes without daemon..."
./gradlew --no-daemon classes || {
  echo "[$(date +"%H:%M:%S")] ERROR: Running gradlew build failed! Run './gradlew --no-daemon classes' manually"
  exit 1
}

#

cat logo_ascii.txt 2>/dev/null
echo "=========================================================================="
echo ""
echo "[$(date +"%H:%M:%S")] Build succeeded! All checks passed, you can build normally now!"
echo ""
echo "=========================================================================="
