#!/bin/sh

# Created by S-B99 on 19/02/20
# echo "Usage: ./ver.sh"

CUR_VER="$(cat ./scripts/curVer)"
CUR_BETA="$(cat ./scripts/curBeta)"

sed -i "s/modVersion=.*/modVersion=${CUR_VER:1}$CUR_BETA/" gradle.properties
sed -i "s/MODVER = \".*\";/MODVER = \"$CUR_VER\";/" src/main/java/me/zeroeightsix/kami/KamiMod.java
sed -i "s/MODVERSMALL = \".*\";/MODVERSMALL = \"$CUR_VER$CUR_BETA\";/" src/main/java/me/zeroeightsix/kami/KamiMod.java
sed -i "s/\"version\": \".*\",/\"version\": \"${CUR_VER:1}\",/" src/main/resources/mcmod.info

git reset
git add gradle.properties src/main/java/me/zeroeightsix/kami/KamiMod.java src/main/resources/mcmod.info
git commit -m "[BOT] New release: $CUR_VER$CUR_BETA"
