<img src="https://github.com/S-B99/kamiblue/blob/assets/assets/icons/kami.svg" align="left" width="120"/>

# KAMI Blue 

### A minecraft utility mod for anarchy servers.

# 
<a href="https://github.com/S-B99/kamiblue/releases/download/v1.1.2/KAMI-Blue-v1.1.2-release.jar">
<img src="https://github.com/S-B99/kamiblue/blob/assets/assets/icons/download.svg" width="200"/>
</a>

|              | S-B99 (feature/master)| 086 (upstream) |
|:------------:|:-------------:|:--------------:|
| Build Status | [![Build Status features-master](https://img.shields.io/travis/com/S-B99/kamiblue/feature/master?logo=gradle&label=build)](https://travis-ci.com/S-B99/kamiblue/) | [![Build Status 086](https://travis-ci.com/zeroeightysix/KAMI.svg?logo=gradle&branch=master)](https://travis-ci.com/zeroeightysix/KAMI) |
| Media        | [![Discord Mine](https://img.shields.io/discord/573954110454366214?label=chat&logo=discord&logoColor=white)](https://discord.gg/KfpqwZB) | [![Discord 086](https://img.shields.io/discord/496724196542513174)](http://discord.gg/9hvwgeg) |
| Version      | [![Version master](https://img.shields.io/github/v/release/S-B99/kamiblue?color=dark-green&label=latest&logo=java)](https://github.com/S-B99/kamiblue/releases) | [![Version 086](https://img.shields.io/github/v/tag/zeroeightysix/KAMI?color=red&label=outdated)](https://github.com/zeroeightysix/KAMI/releases) |
| Downloads    | ![Dl discord](https://img.shields.io/badge/discord-22k-brightgreen?logo=discord&logoColor=white) ![Dl Github](https://img.shields.io/github/downloads/S-B99/kamiblue/total?label=github&logo=github) | ![Dl github 086](https://img.shields.io/github/downloads/zeroeightysix/KAMI/total?label=github&logo=github) |

[![Paypal](https://img.shields.io/badge/paypal-donate-red?color=169bd7&logo=paypal)](https://paypal.me/bellawhotwo) 
[![BTC](https://img.shields.io/badge/btc-clickme-red?color=f08b16&logo=bitcoin)](https://www.blockchain.com/btc/address/19pH4aNZZMPJkqQ2826BauRokyBs1NYon7)
[![BCH](https://img.shields.io/badge/bch-clickme-red?color=2db300&logo=cash-app)](https://www.blockchain.com/bch/address/19pH4aNZZMPJkqQ2826BauRokyBs1NYon7) 

Please consider donating to help continue this project and get a unique cape in game. 

Also note Baritone is no longer included. Download the standalone jar [from here](https://github.com/cabaletta/baritone/releases/tag/v1.2.10).

<details>
	<summary>Click to view disclaimers</summary>

***

This is by no means a finished project, nor is it a "cheat" or "hack" for anything, it is a *utility* mod.

See [forgehax](https://github.com/fr1kin/forgehax) for an equivalent. Some features in KAMI may be based on those of forgehax, and KAMI / KAMI Blue have some features it doesn't. KAMI Blue won't be based off of other mods unless said otherwise.

***

</details>

## Installing

To install drag the `VERSION-release.jar` to your `mods/1.12.2` folder

<details>
	<summary>Click to see more detailed installing instructions</summary>

KAMI Blue is a forge mod. Start by downloading the latest version of [1.12.2 forge](https://files.minecraftforge.net/maven/net/minecraftforge/forge/index_1.12.2.html).
1. Install forge
2. Go to your `.minecraft` directory.
   * **Linux**: `~/.minecraft`
   * **Windows**: `%appdata%/.minecraft`
   * **OS X**: `~/Library/Application Support/minecraft`
3. Navigate to the `mods` directory. If it doesn't exist, create it.
4. Get the KAMI Blue `.jar` file.
   * By **downloading** it: see [releases](../../releases)
   * By **building** it: see [building](#building).
5. Drag the `-release.jar` file into your mods directory.

</details>

## FAQ

<details>
	<summary>Click to see the frequently asked questions or basic stuff</summary>

***

##### Open the GUI
Press Y.

##### Use commands
The default prefix is `.`. Commands are used through chat, use `.commands` for a list of commands.

##### Bind modules
Run `.bind <module> <key>`.

You can also use `.bind modifiers on` to allow modules to be bound to keybinds with modifiers, e.g `ctrl + shift + w` or `ctrl + c`.

##### Change command prefix
By using the command `prefix <prefix>` or after having ran KAMI Blue (make sure it's closed), editing your configuration file (find it using `config path` in-game) and changing the value of `commandPrefix` to change the prefix.

***

</details>

## Preview

***

### Click on images to expand

Capes in game

<img src="https://raw.githubusercontent.com/S-B99/kamiblue/assets/assets/readme/capes.png" width="500"/>

Rich presence on discord

<img src="https://raw.githubusercontent.com/S-B99/kamiblue/assets/assets/readme/rpc.png" width="500"/>

Shulker preview being used in chat

<img src="https://raw.githubusercontent.com/S-B99/kamiblue/assets/assets/readme/shulkerChat.png" width="500"/>

CrystalAura targeting

<img src="https://raw.githubusercontent.com/S-B99/kamiblue/assets/assets/readme/crystalAura.png" width="500"/>

***

## Status

<details>
	<summary>Click to view current development status</summary>

***

Please see the [issues](https://github.com/S-B99/kamiblue/issues/) page for planned features and bugs to fix.

This is currently in active development. When issues are being closed is by milestone, and bugs are higher priority, though there aren't any that break anything completely.

***

</details>

## Building and Contributing

<details>
	<summary>Click to see instructions</summary>

***

If you can't figure these instructions out, please see [this](https://youtu.be/PfmlNiHonV0) before asking for help.

Please make sure to restart your IDE in after running all setup commands

***

***

### Contributing 

You are free to clone, modify KAMI and KAMI Blue and make pull requests as you wish. To set up your development environment, make use of the following commands:

On GNU/Linux, run `chmod +x gradlew` beforehand

On Windows, for the following commands use `./gradlew` instead of `gradlew.bat`

Of-course you can also use a Gradle installation if you for some reason want another version of gradle
```
git clone https://github.com/S-B99/kamiblue/
cd KAMI
```
Import KAMI Blue into your IDE of choice. 
```
./gradlew setupDecompWorkspace
./gradlew genIntellijRuns #for intellij
./gradlew eclipse #for eclipse
```
If you use IntelliJ, import `build.gradle`

If you use Eclipse, import a new gradle project and select the KAMI folder. 

If you have gradle related issues with either of these force your gradle version to `4.8.1`

If you do not wish to run from an IDE, use `./gradlew runClient` to run KAMI Blue.

Note: I don't recommend using runClient as sometimes it's wonky. If you have issues then do `./gradlew clean`

*** 

If you get build errors see this: [troubleshooting page](docs/TROUBLESHOOTING.md)

***

***

### Building

***

#### Linux
You can build by running these commands (without the <>) in a terminal.
```
git clone https://github.com/S-B99/kamiblue/
cd KAMI

chmod +x gradlew
./gradlew <args>
```
Possible arguments:
```
build
mkdir
rmOld
copy
```
If you use more than one then it must be in that order. 

Build is required, `mkdir` makes the `mods/1.12.2` directory, `rmOld` removes old versions of KAMI and KAMI Blue\* in that directory, and `copy` copies the build release to the `mods/1.12.2` directory. 

\*`rmOld` removes any jars ending in `-release.jar`, which is the format KAMI used and that KAMI Blue uses. If you use any other mod that uses that naming scheme please remove old versions manually.

If you prefer copying it manually, find a file in `build/libs` called `KAMI-<kamiVersion>-**release**.jar` which you can copy to the `mods/1.12.2` folder of a minecraft instance that has forge installed.

Note: This assumes your minecraft folder is in the default location under your home folder.

Note: Any argument other then `build` assumes you downloaded KAMI Blue to a nested folder inside your home folder. For example `~/Downloads/KAMI` or `~/Documents/KAMI`. This will be fixed as per [issue #15](https://github.com/S-B99/kamiblue/issues/15)

***

#### Windows
You can build by running these commands in a terminal with the current directory being KAMI. (EG. `cd C:\Users\Username\Downloads\KAMI`)
```
gradlew.bat build
```

To copy on windows find a file in `build/libs` called `KAMI-<kamiVersion>-**release**.jar` which you can copy to the `mods\1.12.2` folder of a minecraft instance that has forge installed.

Note: This assumes your minecraft folder is in the default location under your %appdata% folder.

***

If you get build errors see this: [troubleshooting page](docs/TROUBLESHOOTING.md)

***

</details>

## Thank you

[zeroeightysix](https://github.com/zeroeightysix) for the original [KAMI](https://github.com/zeroeightysix/KAMI)

[ZeroMemes](https://github.com/ZeroMemes) for [Alpine](https://github.com/ZeroMemes/Alpine)

[ronmamo](https://github.com/ronmamo/) for [Reflections](https://github.com/ronmamo/reflections)

The [Minecraft Forge team](https://github.com/MinecraftForge) for [forge](https://files.minecraftforge.net/)

All the [contributors](https://github.com/S-B99/kamiblue/graphs/contributors), including the ones who will be remembered in comments and in our hearts. This has been a huge community effort and I couldn't have done it without them.
