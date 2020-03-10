#!/bin/bash
DIR="$(readlink -f ./build/libs/)"
JARDIR="$(ls "$DIR" | grep "release")"
curl -F content=@""$DIR"/"$JARDIR"" "$WEBHOOK"
#echo ""$DIR"/"$UWUOWO""
