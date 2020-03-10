#!/bin/bash
CURDIR="$(readlink -f ./build/libs/)"
JARDIR="$(ls "$CURDIR" | grep "release")"
curl -F content=@""$CURDIR"/"$JARDIR"" "$WEBHOOK"
