<p align="center">
    <img src="https://raw.githubusercontent.com/lambda-client/assets/main/lambda%20logo%20text.svg" style="width: 69%" alt="logo">
</p>

![Minecraft](https://img.shields.io/badge/minecraft-1.21.5-green?link=https%3A%2F%2Fwww.minecraft.net%2F)
![GitHub Downloads](https://img.shields.io/github/downloads/lambda-client/lambda/total)
![Discord](https://img.shields.io/discord/834570721070022687?logo=Discord&logoColor=white&link=https%3A%2F%2Fdiscord.gg%2FMBAEzyFn)
![CodeFactor Grade](https://img.shields.io/codefactor/grade/github/lambda-client/lambda?color=royalblue)
![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/lambda-client/lambda/build.yml?branch=1.21.5&logo=gradle)
![GitHub Contributors](https://img.shields.io/github/contributors/lambda-client/lambda)
![GitHub Repo Stars](https://img.shields.io/github/stars/lambda-client/lambda)
![GitHub License](https://img.shields.io/github/license/lambda-client/lambda?logo=gplv3&link=https%3A%2F%2Fwww.gnu.org%2Flicenses%2Fgpl-3.0.en.html)

Lambda is a free, open-source Minecraft Fabric utility mod focused on advanced automation to execute complex, repeatable tasks with minimal micromanagement. This is a complete, ground-up rewrite of the original client that you can find here: [Lambda Legacy](https://github.com/lambda-client/lambda).

<p align="center">
    <a href="https://github.com/lambda-client/lambda/releases/download/0.0.1/lambda-0.0.1.jar"><img alt="lambda-0.0.1.jar" src="https://raw.githubusercontent.com/lambda-client/assets/main/download_button_0.0.1.png" width="70%" height="70%"></a>
</p>

<p align="center">
  <a href="https://discord.gg/MBAEzyFn"><img src="https://invidget.switchblade.xyz/MBAEzyFn" alt="Link to the lambda discord server https://discord.gg/MBAEzyFn"></a>
</p>

Find our backup matrix space at [\#lambda-client:matrix.org](https://app.element.io/#/room/#lambda-client:matrix.org).

> [\!WARNING]
> **Alpha Status:** This version of Lambda is a complete rewrite (2+ years in the making) and is currently in an **Alpha** state. While highly capable, please expect bugs and incomplete features as we progress toward Beta. Old addons are not compatible with this version.

-----

## Features

### Automation Engine
* **Build Engine:** Full integration with **Litematica** and schematic files for seamless automated building.
* **Block State Handling:** The build engine natively supports special block states including rotations, attachments (doors, signs, bells), half-slabs, stairs, repeater delay, and even edge cases like flower pots with plants.
* **Conflict-Free Orchestration:** A centralized manager system handles all core interactions (placing, breaking, rotating, inventory) to ensure zero conflicts between concurrently running modules.

### Unmatched Performance
* **High-Speed ESP:** Rendering is optimized to handle extreme scenarios—capable of visualizing all obsidian at the 2b2t spawn without dropping frames.
* **Modern Framework:** Built on modern modding frameworks with efficient rendering pipelines that outperform legacy clients.

### Sophisticated User Experience
* **Fine-Grained Control:** Access over **1000+ settings**, allowing you to tune every aspect of the client to your exact needs.
* **Advanced GUI:** The **Dear ImGui** interface is designed for clarity and depth, featuring quick search, context menus, and easy keybind editing.
* **Automation Profiles:** Use linkable presets to apply complex configurations across multiple modules instantly.

### Stability & Safety
* **Anticheat Ready:** Built with **Grim** and other modern anticheats in mind.
* **Type-Safe Commands:** Uses Minecraft's statically typed command system to ensure input accuracy and reliability.

### Specialized Tools
* **HighwayTools:** Completely rewritten for efficient infrastructure maintenance.

-----

## Installation
<a href="https://fabricmc.net/wiki/install"><img src="https://cdn.jonasjones.dev/mod-badges/support-fabric.png" width="150px" alt="Fabric Supported"></a>
1. Install Minecraft 1.21.5 [(download)](https://www.minecraft.net/)
2. Install Fabric [(download)](https://fabricmc.net/use/installer/)
3. Get the latest Lambda version here [(download)](https://github.com/lambda-client/lambda/releases/download/1.0.0/lambda-1.0.0.jar)
4. Put the file in your `.minecraft/mods` folder

## Getting Started

How do I...

<details>
<summary><strong>... open the ClickGUI?</strong></summary>

> Press `Y`.

</details>

<details>
<summary><strong>... I execute a command?</strong></summary>

> Use the ingame chat with the prefix `;`.

</details>

<p align="center">
    <img alt="" src="https://raw.githubusercontent.com/lambda-client/assets/main/footer.png">
</p>

## Thanks to...

[![GitHub contributors](https://contrib.rocks/image?repo=lambda-client/lambda)](https://github.com/lambda-client/lambda/graphs/contributors)

We need the help of the community to support the growth of this project. Whether that be developers contributing to the codebase, creating addons, or users giving feedback.

---

If you have any questions, concerns, or suggestions,
you can visit our [official Discord server](https://discord.gg/MBAEzyFn).

> ### Disclaimer
> Lambda is not affiliated with Mojang Studios. Minecraft is a registered trademark of Mojang Studios.
Use of the Lambda software is subject to the terms outlined in the license agreement [GNU General Public License v3.0](https://github.com/lambda-client/lambda/blob/master/LICENSE.md).