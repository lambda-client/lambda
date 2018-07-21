# Troubleshooting

This page is meant to explain how to solve common issues when installing or using KAMI. If you experience an issue and it's not listed here, please [open a new issue](https://github.com/zeroeightysix/KAMI/issues/new) and a contributor will help you further.

## Setup
###### Could not find tools.jar
If you encounter this error when building, you most likely don't have the Java Development Kit (JDK) installed.
Head over to [here](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) to download the oracle JDK. Install it and try again.

###### Minecraft not loading at all
What `.jar` are you using in your `mods` folder? Make sure to use the one that ends with `-full` (`KAMI-MCVER-KAMIVER-full.jar`)

## Crashes in-game

**Please make sure you're on the latest version of forge before proceeding!**

###### java.lang.NoSuchMethodError: net.minecraft.client.multiplayer.WorldClient.isPlayerUpdate()Z
Are you using MultiMC? If so, move minecraft above LWJGL 2 to solve this issue.
Start on the main screen of MultiMC.
1. Select your KAMI instance.
2. Press `Edit Instance`
3. Press `Version` (Top left)
4. Select `Minecraft`
5. Press `Move up` until it is the first entry in the list.