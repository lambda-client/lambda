#!/bin/bash
curl -H "Content-Type: application/json" -X POST -d '{"username": "Automated Builds", "content": "Build: SUCCESS"}' "$WEBHOOK"
