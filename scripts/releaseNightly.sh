#!/bin/bash
  
cd ~/kamiblue/
export COMMIT_LAST="$(git log --format=%h -1)"
git fetch kamiblue
git reset --hard kamiblue/master

export COMMIT_TRIM="$(git log --format=%h -1)"

if [ "$COMMIT_LAST" == "$COMMIT_TRIM" ]; then
    exit 0
fi

rm -rf build/libs

./scripts/preHook.sh
./gradlew build
./scripts/hook.sh
