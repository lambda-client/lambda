#!/bin/bash
BRANCH="$(git branch 2>/dev/null | grep '^*' | colrm 1 2)"
curl -H "Content-Type: application/json" -X POST -d '{"username": "Github Actions", "content": "**Branch:** `'$BRANCH'`"}' "$WEBHOOK"
