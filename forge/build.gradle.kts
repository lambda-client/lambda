/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

val modId: String by project
val modVersion: String by project
val minecraftVersion: String by project
val forgeVersion: String by project
val mixinExtrasVersion: String by project
val kotlinForgeVersion: String by project
val discordIPCVersion: String by project
val ktorVersion: String by project
val baritoneVersion: String by project

base.archivesName = "${base.archivesName.get()}-forge"

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

architectury {
    platformSetupLoomIde()
    forge()
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath
    forge {
        // This is required to convert the access wideners to the forge
        // format, access transformers.
        convertAccessWideners = true

        // Add the mod's mixins to the list of mixins to be applied.
        // In the extraordinary case that you need to add mixins for
        // different mod loaders, you can add them using the
        // `extraAccessWideners` property.
        // And then add them to the `mixinConfig` function.
        mixinConfig("$modId.mixins.common.json")
    }
}

repositories {
    // You can add more repositories here if you plan
    // on using environment-specific dependencies.
    // If you simply want to add a global plugin repository,
    // you can add it to the `settings.gradle.kts` file
    // in the base of the project and gradle will do the
    // rest for you.
    // If you want to add more global repositories, you can
    // add them to the root build.gradle.kts file.
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentForge"].extendsFrom(this)
    isCanBeResolved = true
    isCanBeConsumed = false
}

val includeLib: Configuration by configurations.creating
val includeMod: Configuration by configurations.creating
val shadowLib: Configuration by configurations.creating { isCanBeConsumed = false }
val shadowMod: Configuration by configurations.creating { isCanBeConsumed = false }
val shadowBundle: Configuration by configurations.creating { isCanBeConsumed = false }

fun DependencyHandlerScope.setupConfigurations() {
    includeLib.dependencies.forEach {
        implementation(it)
        include(it)
        // shadowBundle(it)
    }

    includeMod.dependencies.forEach {
        implementation(it)
        // include(it)
    }

    shadowLib.dependencies.forEach {
        implementation(it)
    }

    shadowMod.dependencies.forEach {
        implementation(it)
    }
}

dependencies {
    // Forge API
    forge("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.28.0-GA")
    includeLib("com.github.Edouard127:KDiscordIPC:$discordIPCVersion")
    includeLib("com.pngencoder:pngencoder:0.15.0")

    // Ktor
    includeLib("io.ktor:ktor-client-core:$ktorVersion")
    shadowLib("io.ktor:ktor-client-cio:$ktorVersion") { exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx"); exclude(group = "org.slf4j") }
    includeLib("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    includeLib("io.ktor:ktor-serialization-gson:$ktorVersion")

    // Add mods to the mod jar
    includeMod("thedarkcolour:kotlinforforge:$kotlinForgeVersion")
    includeMod("baritone-api:baritone-unoptimized-forge:$baritoneVersion")

    // MixinExtras
    implementation("io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")!!)

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionForge")) { isTransitive = false }

    // Finish the configuration
    setupConfigurations()
}

tasks {
    shadowJar {
        archiveVersion = "$modVersion+$minecraftVersion"
        configurations = listOf(shadowLib, shadowMod, shadowBundle)
        archiveClassifier = "dev-shadow"
    }

    remapJar {
        dependsOn(processResources, shadowJar)

        archiveVersion = "$modVersion+$minecraftVersion"
        inputFile = shadowJar.get().archiveFile
    }
}
