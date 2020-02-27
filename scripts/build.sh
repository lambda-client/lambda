#!/bin/bash

START_TIME="$(date +%s)"
echo "BUILD: started at $(date -d "@$START_TIME")"

cd ../

mkdir kblue-beta/
cp -r kamiblue/gradle* kblue-beta/ 
cp -r kamiblue/gradlew* kblue-beta/
cp -r kamiblue/gradle/ kblue-beta/
cp -r kamiblue/build.sh kblue-beta/
cp -r kamiblue/build.gradle kblue-beta/
cp -r kamiblue/src/ kblue-beta/
echo "BUILD: copied"

cd kblue-beta/ || exit

echo "BUILD: cleaning"
./gradlew clean

echo "BUILD: building"
./gradlew build rmOld copy

cd ../

rm -rf kblue-beta/
echo "BUILD: deleted"

cd kamiblue/ || exit

echo "BUILD: SUCCESFUL"

END_TIME="$(date +%s)"
echo "BUILD: took" $(($(date +%s)-$START_TIME)) "seconds"
echo "BUILD: finished at" "$(date -d "@$END_TIME")"
