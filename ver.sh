#!/bin/sh

# Created by S-B99 on 19/02/20
# echo "Usage: ./ver.sh v2.0.0 01"

dateD=$(date +'%d')
dateM=$(date +'%m')

sed -i "s/modVersion=.*/modVersion=$1\-${dateD}\-${dateM}\-$2/" gradle.properties
sed -i "s/MODVER = \".*\";/MODVER = \"$1\-${dateD}\-${dateM}\-$2\";/" src/main/java/me/zeroeightsix/kami/KamiMod.java
sed -i "s/MODVERSMALL = \".*\";/MODVERSMALL = \"$1\-beta\";/" src/main/java/me/zeroeightsix/kami/KamiMod.java
sed -i "s/\"version\": \".*\",/\"version\": \"${1:1}\-${dateD}\-${dateM}\-$2\",/" src/main/resources/mcmod.info

git reset
git add gradle.properties src/main/java/me/zeroeightsix/kami/KamiMod.java src/main/resources/mcmod.info
git commit -m "[BOT] New beta: $1-${dateD}-${dateM}-$2"
