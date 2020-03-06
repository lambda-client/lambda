#!/bin/sh

# Created by S-B99 on 19/02/20
# echo "Usage: ./ver.sh v2.0.0 01"

dateM=$(date +'%m')
dateD=$(date +'%d')

sed -i "s/modVersion=.*/modVersion=$1\-${dateM}\-${dateD}\-$2/" gradle.properties
sed -i "s/MODVER = \".*\";/MODVER = \"$1\-${dateM}\-${dateD}\-$2\";/" src/main/java/me/zeroeightsix/kami/KamiMod.java
sed -i "s/\"version\": \".*\",/\"version\": \"${1:1}\-${dateM}\-${dateD}\-$2\",/" src/main/resources/mcmod.info
sed -i "s/MODVERSMALL = \".*\";/MODVERSMALL = \"$1\-beta\";/" src/main/java/me/zeroeightsix/kami/KamiMod.java

git reset
git add gradle.properties src/main/java/me/zeroeightsix/kami/KamiMod.java src/main/resources/mcmod.info
git commit -m "[BOT] New beta: $1-${dateD}-${dateM}-$2"
