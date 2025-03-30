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
val baritoneVersion: String by project
val fuelVersion: String by project
val resultVersion: String by project

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
val shadowBundle: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

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
}

dependencies {
    // Forge API
    forge("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.28.0-GA")
    includeLib("com.github.Edouard127:KDiscordIPC:$discordIPCVersion")
    includeLib("com.pngencoder:pngencoder:0.15.0")

    // Fuel HTTP library and dependencies
    includeLib("com.github.kittinunf.fuel:fuel:$fuelVersion")
    includeLib("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")
    includeLib("com.github.kittinunf.result:result-jvm:$resultVersion")

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
    // Merge the resources and classes into the same directory.
    // This is done because java expects modules to be in a single directory.
    // And if we have it in multiple we have to do performance intensive hacks like having the UnionFileSystem
    // This will eventually be migrated to ForgeGradle so modders don't need to manually do it. But that is later.
    sourceSets.forEach {
        val dir = layout.buildDirectory.dir("sourcesSets/${it.name}")
        it.output.setResourcesDir(dir)
        it.java.destinationDirectory.set(dir)
    }

    shadowJar {
        archiveVersion = "$modVersion+$minecraftVersion"
        configurations = listOf(shadowBundle)
        archiveClassifier = "dev-shadow"
    }

    remapJar {
        dependsOn(processResources, shadowJar)

        archiveVersion = "$modVersion+$minecraftVersion"
        inputFile = shadowJar.get().archiveFile
    }
}
