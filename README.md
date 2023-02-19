<p align="center">
    <img src="https://raw.githubusercontent.com/lambda-client/assets/main/lambda%20logo%20text.svg" style="width: 69%">
</p>

![GitHub all releases](https://img.shields.io/github/downloads/lambda-client/lambda/total?color=seagreen)
![CodeFactor grade](https://img.shields.io/codefactor/grade/github/lambda-client/lambda?color=royalblue)
![GitHub workflow status](https://img.shields.io/github/actions/workflow/status/lambda-client/lambda/nightly_build.yml?branch=master&logo=gradle)
[![Discord](https://img.shields.io/discord/834570721070022687?color=skyblue&logo=discord&logoColor=white)](https://discord.gg/QjfBxJzE5x)
![GitHub repo size](https://img.shields.io/github/repo-size/lambda-client/lambda)
![Lines of code](https://img.shields.io/tokei/lines/github/lambda-client/lambda?color=lightcoral&label=lines%20of%20code)

Lambda is a free, open-source, Minecraft 1.12.2 utility mod made for the anarchy experience.
A visionary plugin system that allows additional modules to be added, without the need to create a fork!
Customize your experience, and improve your efficiency!

Find our plugins [here](https://github.com/lambda-plugins).

<p align="center">
    <a href="https://github.com/lambda-client/lambda/releases/download/3.3.0/lambda-3.3.0.jar"><img alt="lambda-3.3.0.jar" src="https://raw.githubusercontent.com/lambda-client/assets/main/download_button_3.3.0.png" width="70%" height="70%"></a>
</p>

<div align="center">
  <a href="https://discord.gg/QjfBxJzE5x"><img src="https://invidget.switchblade.xyz/QjfBxJzE5x" alt="Link to the lambda discord server https://discord.gg/QjfBxJzE5x"></a>
</div>

## Installation
1. Install Minecraft 1.12.2
2. Install the latest Forge for 1.12.2 [(download)](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html)
3. Get the latest Lambda version here [(download)](https://github.com/lambda-client/lambda/releases/download/3.2.1/lambda-3.2.1.jar)
4. Put the file in your `.minecraft/mods` folder

## FAQ

How do I...

<details>
  <summary>... open the ClickGUI?</summary>

> Press `Y`.

</details>

<details>
  <summary>... execute a command?</summary>

> Use the ingame chat with the prefix `;`.

</details>

<details>
  <summary>... install plugins?</summary>

> Open the ClickGUI by pressing `y`. The window called `Plugins` contains all installed plugins. Either you get an official plugin by opening the `Remote Plugins` window which are hosted on [GitHub](https://github.com/lambda-plugins/). Or if you want to load a third party plugin click the `Import` button and put the jar into the folder.

> CAUTION: Third party plugins can contain dangerous code! Only use plugins from trusted sources!

</details>

<details>
  <summary>... export KAMI blue config to Lambda?</summary>

> Rename `.minecraft/kamiblue` to `.minecraft/lambda`.
> Please note that this might cause stability issues.

</details>

<details>
  <summary>... fix most crashes on startup?</summary>

> You may have multiple mods loaded. Forge loads mods in alphabetical order, so you can change the name of the Mod jar to make it load earlier or later. Add for example an exclamation mark to lambda jar to make it load first.
> If you got `Error: java.lang.IllegalAccessError: tried to access field net.minecraft.util.math.Vec3i.field_177962_a from class baritone.k` remove the `-noverify` tag from your arguments.

</details>

<details>
  <summary>... fix problems with Gradle?</summary>

> Make sure you have a Java 8 JDK installed and in your PATH.
We advise using the [Temurin](https://adoptium.net/?variant=openjdk8&jvmVariant=hotspot/) distribution of OpenJDK.

</details>

<details>
  <summary>... reset the ClickGUI scale?</summary>

> Run the command `;set clickgui scale 100`.

</details>

<details>
  <summary>... crashing with liteloader?</summary>

> Use liteloader as a forge mod, it is available [here](https://jenkins.liteloader.com/view/1.12.2/job/LiteLoader%201.12.2/lastSuccessfulBuild/artifact/build/libs/liteloader-1.12.2-SNAPSHOT-release.jar).
</details>

<p align="center">
    <img alt="" src="https://raw.githubusercontent.com/lambda-client/assets/main/footer.png">
</p>

## Contributing

### Clone Repository

Clone the repository to your local machine. Use the link of either your fork or the main repository.
```
git clone https://github.com/lambda-client/lambda
```

Run `setupWorkspace.sh` to initialize the environment.
Use your terminal on Linux or [Git Bash](https://gitforwindows.org/) for Windows.
```
./setupWorkspace.sh
```

### Setup IDE

In this guide we will use [IntelliJ IDEA](https://www.jetbrains.com/idea/) as our IDE.
1. Open the project from `File > Open...`
2. Let the IDE collect dependencies and index the code.
3. Goto `File > Project Structure... > SDKs` and make sure an SDK for Java 8 is installed and selected, if not download
   one [here](https://adoptium.net/?variant=openjdk8&jvmVariant=hotspot/).

### Gradle build

Test if the environment is set up correctly by building the client and running it inside the IDE using the Gradle tab on the right side of the IDE.
1. Go to `lambda > Tasks > forgegradle runs > runClient` in the Gradle tab and run the client.
2. To build the client as a jar run `lambda > Tasks > build > build`. Gradle will create a new directory called `build`. The final built jar will be in `build/libs`.

### Stargazers
[![Stargazers](https://starchart.cc/lambda-client/lambda.svg)](https://starchart.cc/lambda-client/lambda)

## Thanks to...

[![GitHub contributors](https://contrib.rocks/image?repo=lambda-client/lambda)](https://github.com/lambda-client/lambda/graphs/contributors)

[zeroeightysix](https://github.com/zeroeightysix) for the original [KAMI](https://github.com/zeroeightysix/KAMI)

[KAMI Blue](https://github.com/kami-blue) for the continuation of [KAMI](https://github.com/zeroeightysix/KAMI)

[ronmamo](https://github.com/ronmamo) for [Reflections](https://github.com/ronmamo/reflections)

[MinecraftForge](https://github.com/MinecraftForge) for [Forge](https://github.com/MinecraftForge/MinecraftForge)

> ### Disclaimer
> This software does not contain any copyrighted Minecraft code. This is a Forge utility mod, only meant for use in anarchy environments. Do not use without permission of server administration.
