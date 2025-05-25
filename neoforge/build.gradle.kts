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

val modVersion: String by project
val minecraftVersion: String by project
val neoVersion: String by project
val kotlinForgeVersion: String by project
val pngEncoderVersion: String by project
val discordIPCVersion: String by project
val classGraphVersion: String by project
val ktorVersion: String by project

base.archivesName = "${base.archivesName.get()}-neoforge"

plugins {
    id("com.gradleup.shadow") version "9.0.0-beta13"
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath
}

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://maven.neoforged.net/releases")
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentNeoForge"].extendsFrom(this)
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
    // NeoForge API
    neoForge("net.neoforged:neoforge:${neoVersion}")

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
    includeMod("thedarkcolour:kotlinforforge-neoforge:$kotlinForgeVersion")
    includeMod("com.github.rfresh2:baritone-neoforge:$minecraftVersion") { isTransitive = true }

    // Common (Do not touch)
    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionNeoForge"))

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
