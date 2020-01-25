#!/bin/bash

START_TIME="$(date +%s)"
echo "BUILD: started at $(date -d "@$START_TIME")"

cd ../

mkdir KAMI-Blue-beta/
cp -r kamiblue/gradle* KAMI-Blue-beta/ 
cp -r kamiblue/gradlew* KAMI-Blue-beta/
cp -r kamiblue/gradle/ KAMI-Blue-beta/
cp -r kamiblue/build.sh KAMI-Blue-beta/
cp -r kamiblue/build.gradle KAMI-Blue-beta/
cp -r kamiblue/src/ KAMI-Blue-beta/
echo "BUILD: copied"

cd KAMI-Blue-beta/ || exit

echo "BUILD: cleaning"
./gradlew clean

echo "BUILD: building"
./gradlew build rmOld copy

cd ../

rm -rf KAMI-Blue-beta/
echo "BUILD: deleted"

cd kamiblue/ || exit

echo "BUILD: SUCCESFUL"

END_TIME="$(date +%s)"
echo "BUILD: took" $(($(date +%s)-$START_TIME)) "seconds"
echo "BUILD: finished at" "$(date -d "@$END_TIME")"
