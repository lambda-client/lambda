#!/bin/bash

CUR_VER="$(tail -c +2 ./scripts/curVer)"
COMMIT_TRIM="$(git log --format=%h -1)"
COMMIT_FULL="$(git log --format=%H -1)"
COMMIT_MSG="$(git log --format=%s -1)"

# Find the release file and rename it to kamiblue-version-commit-release.jar
BUILD_DIR=$HOME/kamiblue/build/libs/
JAR_DIR="$(ls "$BUILD_DIR" | grep $COMMIT_TRIM-"release")"
mv ${BUILD_DIR}/${JAR_DIR} ${BUILD_DIR}/kamiblue-${CUR_VER}-${COMMIT_TRIM}-release.jar
JAR_DIR="$(ls "$BUILD_DIR" | grep "release")"
# Upload the release file
#curl -F content=@"$BUILD_DIR/$JAR_DIR" "$WEBHOOK"

# Upload the release
cd ~/
source ~/.profile
./github-release-linux-amd64 $CUR_VER-$COMMIT_TRIM $BUILD_DIR/$JAR_DIR --github-access-token $GITHUB_RELEASE_ACCESS_TOKEN --github-repository $GITHUB_RELEASE_REPOSITORY

# Send message with commit information
curl -H "Content-Type: application/json" -X POST -d '{"embeds": [{"title": "","color": 10195199,"description": "**Changelog:** '"$COMMIT_MSG"'\n**Download:** ['${CURVER}-${COMMIT_TRIM}'](https://github.com/kami-blue/nightly-releases/releases/download/'${COMMIT_TRIM}'/'${JAR_DIR}'
)\nCommit: ['${COMMIT_TRIM}'](https://github.com/kami-blue/client/commits/'${COMMIT_TRIM}') Direct: ['${COMMIT_TRIM}'](https://github.com/kami-blue/client/commit/'${COMMIT_FULL}') "}]}' "$WEBHOOK"
