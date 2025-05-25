/*
 * Copyright 2025 Lambda
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
val pngEncoderVersion: String by project
val discordIPCVersion: String by project
val classGraphVersion: String by project
val ktorVersion: String by project

base.archivesName = "${base.archivesName.get()}-forge"

plugins {
    id("com.gradleup.shadow") version "9.0.0-beta13"
}

architectury {
    platformSetupLoomIde()
    forge()
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath

    forge {
        convertAccessWideners = true
        mixinConfig("$modId.mixins.common.json")
    }
}

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentForge"].extendsFrom(this)
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
    }

    includeMod.dependencies.forEach {
        implementation(it)
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
    includeLib("io.github.classgraph:classgraph:${classGraphVersion}")
    includeLib("com.github.Edouard127:KDiscordIPC:$discordIPCVersion")
    includeLib("com.pngencoder:pngencoder:$pngEncoderVersion")

    // Ktor
    includeLib("io.ktor:ktor-client-core:$ktorVersion")
    shadowLib("io.ktor:ktor-client-cio:$ktorVersion") { exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx"); exclude(group = "org.slf4j") }
    includeLib("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    includeLib("io.ktor:ktor-serialization-gson:$ktorVersion")

    // Add mods to the mod jar
    includeMod("thedarkcolour:kotlinforforge:$kotlinForgeVersion")
    includeMod("com.github.rfresh2:baritone-forge:$minecraftVersion") { isTransitive = true }

    // MixinExtras
    implementation("io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion") { isTransitive = false }
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")!!)

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionForge"))

    // Finish the configuration
    setupConfigurations()
}

// Merge the resources and classes into the same directory.
// This is done because java expects modules to be in a single directory.
// And if we have it in multiple we have to do performance intensive hacks like having the UnionFileSystem
// This will eventually be migrated to ForgeGradle so modders don't need to manually do it. But that is later.
sourceSets.forEach {
    val dir = layout.buildDirectory.dir("sourcesSets/${it.name}")
    it.output.setResourcesDir(dir)
    it.java.destinationDirectory = dir
}

tasks {
    sourcesJar {
        val commonSources = project(":common").tasks.sourcesJar
        dependsOn(commonSources)
        duplicatesStrategy = DuplicatesStrategy.FAIL
        from(commonSources.get().archiveFile.map { zipTree(it) })
    }

    shadowJar {
        archiveVersion = "$modVersion+$minecraftVersion"
        configurations = listOf(shadowLib, shadowMod, shadowBundle)
        archiveClassifier = "dev-shadow"
    }

    remapJar {
        dependsOn(processResources, shadowJar)

        atAccessWideners.add("src/main/resources/$modId.accesswidener")

        archiveVersion = "$modVersion+$minecraftVersion"
        inputFile = shadowJar.get().archiveFile
    }
}
