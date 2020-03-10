#!/bin/bash
BRANCH="$(git branch | grep '^\*')"
BRANCH="${BRANCH#??}"
curl -H "Content-Type: application/json" -X POST -d '{"username": "Github Actions", "content": "**Branch:** `'$BRANCH'`"}' "$WEBHOOK"
