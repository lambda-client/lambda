#!/bin/bash
BRANCH="$(git symbolic-ref -q --short HEAD)"
curl -H "Content-Type: application/json" -X POST -d '{"username": "Github Actions", "content": "**Branch:** `'$BRANCH'`"}' "$WEBHOOK"
