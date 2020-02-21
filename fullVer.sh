#!/bin/sh

# Created by S-B99 on 19/02/20
# echo "Usage: ./fullVer.sh v2.0.0"

sed -i "s/modVersion=.*/modVersion=$1/" gradle.properties
sed -i "s/MODVER = \".*\";/MODVER = \"$1\";/" src/main/java/me/zeroeightsix/kami/KamiMod.java
sed -i "s/MODVERSMALL = \".*\";/MODVERSMALL = \"$1\";/" src/main/java/me/zeroeightsix/kami/KamiMod.java
sed -i "s/\"version\": \".*\",/\"version\": \"${1:1}\",/" src/main/resources/mcmod.info

git reset
git add gradle.properties src/main/java/me/zeroeightsix/kami/KamiMod.java src/main/resources/mcmod.info
git commit -m "[BOT] BumpVer: $1"
