#!/bin/bash

# Send message with branch name 
curl -H "Content-Type: application/json" -X POST -d '{"username": "KAMI Blue Releases", "content": "**Branch:** '$BRANCH'"}' "$WEBHOOK"

# Upload the release file 
BUILD_DIR="$(readlink -f ./build/libs/)"
JAR_DIR="$(ls "$BUILD_DIR" | grep "release")"
curl -F content=@"$BUILD_DIR/$JAR_DIR" "$WEBHOOK"
