#!/bin/bash

START_TIME="$(date +%s)"
echo "BUILD: started at $(date -d "@$START_TIME")"

echo "BUILD: cd back"
cd ../

echo "BUILD: copied"
mkdir KAMI-Blue-beta/
cp -r kamiblue/gradle* KAMI-Blue-beta/ 
cp -r kamiblue/gradlew* KAMI-Blue-beta/
cp -r kamiblue/gradle/ KAMI-Blue-beta/
cp -r kamiblue/build.sh KAMI-Blue-beta/
cp -r kamiblue/build.gradle KAMI-Blue-beta/
cp -r kamiblue/src/ KAMI-Blue-beta/

echo "BUILD: cd in"
cd KAMI-Blue-beta/ || exit

echo "BUILD: removing keygen"
rm -rf src/main/java/me/zeroeightsix/kami/module/modules/sdashb/experimental/Gen.java

echo "BUILD: cleaning"
./gradlew clean

#echo "BUILD: setup workspace"
#./gradlew setupDecompWorkspace

echo "BUILD: build command"
./gradlew build rmOld copy

echo "BUILD: cd back"
cd ../

echo "BUILD: deleting"
rm -rf KAMI-Blue-beta/

echo "BUILD: cd in"
cd kamiblue/ || exit

echo "BUILD: finished"

END_TIME="$(date +%s)"
echo "BUILD: took" $(($(date +%s)-$START_TIME)) "seconds"
echo "BUILD: finished at" "$(date -d "@$END_TIME")"
