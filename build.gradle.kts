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

import org.gradle.internal.jvm.*
import java.util.*

val modId: String by project
val mavenGroup: String by project
val modVersion: String by project
val minecraftVersion: String by project
val yarnMappings: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project
val kotlinFabricVersion: String by project
val pngEncoderVersion: String by project
val discordIPCVersion: String by project
val classGraphVersion: String by project
val kotlinVersion: String by project
val ktorVersion: String by project


val libs = file("libs")
val targets = listOf("fabric.mod.json")
val replacements = file("gradle.properties").inputStream().use { stream ->
    Properties().apply { load(stream) }
}.map { (k, v) -> k.toString() to v.toString() }.toMap()

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.dokka") version "2.0.0"
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("com.gradleup.shadow") version "9.0.0-rc1"
    id("maven-publish")
}

group = mavenGroup
version = modVersion

base.archivesName = modId

repositories {
    mavenLocal() // Allow the use of local repositories
    maven("https://maven.shedaniel.me/") // Architectury
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.2b2t.vc/releases") // Baritone
    maven("https://jitpack.io") // KDiscordIPC
    mavenCentral()

    // Allow the use of local libraries
    flatDir {
        dirs(libs)
    }
}

loom {
    accessWidenerPath = file("src/main/resources/$modId.accesswidener")

    // Apply access wideners transitively (other mods)
    enableTransitiveAccessWideners = true

    runs {
        all {
            property("lambda.dev", "youtu.be/RYnFIRc0k6E")
            property("org.lwjgl.util.Debug", "true")

            vmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:+CreateCoredumpOnCrash", "-XX:+UseOSErrorReporting")
            programArgs("--username", "Steve", "--uuid", "8667ba71b85a4004af54457a9734eed7", "--accessToken", "****", "--userType", "msa")
        }
    }
}

val includeLib: Configuration by configurations.creating
val includeMod: Configuration by configurations.creating
val shadowLib: Configuration by configurations.creating { isCanBeConsumed = false }
val shadowMod: Configuration by configurations.creating { isCanBeConsumed = false }

fun DependencyHandlerScope.setupConfigurations() {
    includeLib.dependencies.forEach {
        implementation(it)
        include(it)
    }

    includeMod.dependencies.forEach {
        modImplementation(it)
        include(it)
    }

    shadowLib.dependencies.forEach {
        implementation(it)
    }

    shadowMod.dependencies.forEach {
        modImplementation(it)
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$minecraftVersion+$yarnMappings:v2")

    // Fabric
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion+$minecraftVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlinFabricVersion.$kotlinVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("io.github.classgraph:classgraph:${classGraphVersion}")
    includeLib("com.github.Edouard127:KDiscordIPC:$discordIPCVersion")
    includeLib("com.pngencoder:pngencoder:$pngEncoderVersion")
    includeLib("io.github.spair:imgui-java-binding:1.87.7")
    includeLib("io.github.spair:imgui-java-lwjgl3:1.87.7")
    includeLib("io.github.spair:imgui-java-natives-windows:1.87.7")
    includeLib("io.github.spair:imgui-java-natives-linux:1.87.7")

    // Ktor
    includeLib("io.ktor:ktor-client-core:$ktorVersion")
    shadowLib("io.ktor:ktor-client-cio:$ktorVersion") { exclude(group = "org.jetbrains.kotlin"); exclude(group = "org.jetbrains.kotlinx"); exclude(group = "org.slf4j") }
    includeLib("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    includeLib("io.ktor:ktor-serialization-gson:$ktorVersion")

    // Add mods to the mod jar
    includeMod("com.github.rfresh2:baritone-fabric:$minecraftVersion")

    // Finish the configuration
    setupConfigurations()
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    }

    shadowJar {
        archiveVersion = "$modVersion+$minecraftVersion"
        configurations = listOf(shadowLib, shadowMod)
        archiveClassifier = "dev-shadow"
    }

    remapJar {
        dependsOn(shadowJar)

        archiveVersion = "$modVersion+$minecraftVersion"
        inputFile = shadowJar.get().archiveFile
    }

    processResources {
        filesMatching(targets) { expand(replacements) }

        // Forces the task to always run
        outputs.upToDateWhen { false }
    }

    register<Exec>("renderDoc") {
        val javaHome = Jvm.current().javaHome
        val gradleWrapper = rootProject.tasks.wrapper.get().jarFile.absolutePath

        commandLine = listOf(
            "renderdoccmd", "capture", "--opt-api-validation", "--opt-api-validation-unmute", "--opt-hook-children",
            "--wait-for-exit", "--working-dir", ".", "$javaHome/bin/java", "-Xmx64m", "-Xms64m",
            /*"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005",*/
            "-Dorg.gradle.appname=gradlew", "-Dorg.gradle.java.home=$javaHome", "-classpath", gradleWrapper, "org.gradle.wrapper.GradleWrapperMain",
            "$path:runClient",
        )
    }
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
