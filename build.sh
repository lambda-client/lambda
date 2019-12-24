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

#echo "BUILD: copied modules and tools"
#cp kamiblue/sdashb.modules kamiblue-buildtools/
#cp kamiblue/build*.sh kamiblue-buildtools/

#echo "BUILD: copied gradle"
#cp kamiblue/build.gradle kamiblue-buildtools/
#cp kamiblue/gradle.properties kamiblue-buildtools/

#echo "BUILD: committing to git"
#cd kamiblue-buildtools/
#git add .
#git commit -m "Auto commit from building"
#git push
#cd ../

echo "BUILD: cd in"
cd KAMI-Blue-beta/

#echo "BUILD: removing donator modules"
#rm -rf src/main/java/me/zeroeightsix/kami/module/modules/sdashb/capes/
#rm -rf src/main/java/me/zeroeightsix/kami/module/modules/sdashb/render/CapeGUI.java
#rm -rf src/main/java/me/zeroeightsix/kami/mixin/client/MixinAbstractClientPlayer.java
#curl https://raw.githubusercontent.com/S-B99/KAMI/features-master/src/main/java/me/zeroeightsix/kami/mixin/client/MixinMinecraft.java -o src/main/java/me/zeroeightsix/kami/mixin/client/MixinMinecraft.java
#curl https://raw.githubusercontent.com/S-B99/KAMI/features-master/src/main/resources/mixins.kami.json -o src/main/resources/mixins.kami.json

echo "BUILD: removing keygen"
rm -rf src/main/java/me/zeroeightsix/kami/module/modules/sdashb/experimental/Gen.java

echo "BUILD: removing net minecraft"
rm -rf src/main/java/net/minecraft

#echo "BUILD: removing org lwjgl"
#rm -rf src/main/java/org/lwjgl

#echo "BUILD: removing com google"
#rm -rf src/main/java/org/google

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
cd kamiblue/

echo "BUILD: finished"

END_TIME="$(date +%s)"
echo "BUILD: took" $(($(date +%s)-$START_TIME)) "seconds"
echo "BUILD: finished at" $(date -d "@$END_TIME")
