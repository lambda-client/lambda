#!/bin/bash

if [[ "$BRANCH" == "feature/master" ]]; then
    # Send message with branch name
    curl -H "Content-Type: application/json" -X POST -d '{"username": "KAMI Blue Releases", "content": "**Branch:** `'$BRANCH'`"}' "$WEBHOOK"

    # Upload the release file
    BUILD_DIR="$(readlink -f ./build/libs/)"
    JAR_DIR="$(ls "$BUILD_DIR" | grep "release")"
    curl -F content=@"$BUILD_DIR/$JAR_DIR" "$WEBHOOK"
elif [[ "$BRANCH" == "feature/safecrystal#480" ]]; then
    # Send message with branch name
    curl -H "Content-Type: application/json" -X POST -d '{"username": "KAMI Blue Releases", "content": "**Branch:** `'$BRANCH'`"}' "$WEBHOOK"

    # Upload the release file
    BUILD_DIR="$(readlink -f ./build/libs/)"
    JAR_DIR="$(ls "$BUILD_DIR" | grep "release")"
    curl -F content=@"$BUILD_DIR/$JAR_DIR" "$WEBHOOK"
else
    exit 0
fi
