#!/bin/bash

CUR_VER="$(cat ./scripts/curVer)"
sed -i "s/modVersion=.*/modVersion=${CUR_VER:1}-$TRAVIS_COMMIT/" gradle.properties
sed -i "s/\"version\": \".*\",/\"version\": \"${CUR_VER:1}-$TRAVIS_COMMIT\",/" src/main/resources/mcmod.info
