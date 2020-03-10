#!/bin/bash
curl -H "Content-Type: application/json" -X POST -d '{"username": "test", "content": "hello"}' "$WEBHOOK"
