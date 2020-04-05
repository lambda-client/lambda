#!/bin/bash

if [[ "$TRAVIS_PULL_REQUEST" == "true" ]]; then exit 0; else echo "">/dev/null; fi

COMMIT_TRIM="${TRAVIS_COMMIT::7}"
COMMIT_MSG="$TRAVIS_COMMIT_MESSAGE"

# TODO: Rename jar to from client to kamiblue.
if [[ "$BRANCH" == "master" ]]; then
    # Send message with branch name
    curl -H "Content-Type: application/json" -X POST -d '{"embeds": [{"title": "","color": 10195199,"description": "**Changelog:** '"$COMMIT_MSG"'\nBranch: `'"$BRANCH"'`\nCommit: ['${COMMIT_TRIM}'](https://github.com/kami-blue/client/commits/'${COMMIT_TRIM}') Direct: ['${COMMIT_TRIM}'](https://github.com/kami-blue/client/commit/'${TRAVIS_COMMIT}') "}]}' "$WEBHOOK"

    # Upload the release file
    BUILD_DIR="$(readlink -f ./build/libs/)"
    JAR_DIR="$(ls "$BUILD_DIR" | grep "release")"
    curl -F content=@"$BUILD_DIR/$JAR_DIR" "$WEBHOOK"
else
    exit 0
fi