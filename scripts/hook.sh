#!/bin/bash
CURDIR="$(readlink -f ./build/libs/)"
JARDIR="$(ls "$CURDIR" | grep "release")"
BRANCH="$(git branch | grep "*")"
BRANCH="${BRANCH:2}"
curl -H "Content-Type: application/json" -X POST -d '{"username": "Github Actions", "content": "Branch: '$BRANCH'"}' "$WEBHOOK"
curl -F content=@""$DIR"/"$JARDIR"" "$WEBHOOK"
