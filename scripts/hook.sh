#!/bin/bash

# Send message with branch name 
curl -H "Content-Type: application/json" -X POST -d '{"username": "Github Actions", "content": "**Branch:** '$BRANCH'"}' "$WEBHOOK"

# Upload the release file 
CURDIR="$(readlink -f ./build/libs/)"
JARDIR="$(ls "$CURDIR" | grep "release")"
curl -F content=@""$CURDIR"/"$JARDIR"" "$WEBHOOK"
