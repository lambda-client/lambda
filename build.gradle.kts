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

import org.gradle.internal.jvm.*
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileNotFoundException
import java.util.*

val modId: String by project
val modVersion: String by project
val mavenGroup: String by project
val minecraftVersion: String by project
val yarnMappings: String by project

val libs = file("libs")
val targets = listOf("META-INF/*.toml", "fabric.mod.json")
val replacements = file("gradle.properties").inputStream().use { stream ->
    Properties().apply { load(stream) }
}.map { (k, v) -> k.toString() to v.toString() }.toMap()

val Project.loom: LoomGradleExtensionAPI
    get() = (this as ExtensionAware).extensions.getByName("loom") as LoomGradleExtensionAPI

plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.dokka") version "2.0.0"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.9-SNAPSHOT" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("maven-publish")
}

architectury {
    minecraft = minecraftVersion
}

subprojects {
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")

    dependencies {
        "minecraft"("com.mojang:minecraft:$minecraftVersion")
        "mappings"("net.fabricmc:yarn:$minecraftVersion+$yarnMappings:v2")
    }

    publishing {
        publications {
            register<MavenPublication>("maven") {
                groupId = mavenGroup
                artifactId = if (project.name == "common") modId else "$modId-${project.name}"
                version = "$modVersion+$minecraftVersion"

                from(components["java"])
            }
        }

        repositories {
            maven {
                name = "reposilite"
                url = uri("https://maven.lambda-client.org/lambda")
                credentials(PasswordCredentials::class)
                authentication {
                    create<BasicAuthentication>("basic")
                }
            }
        }
    }

    if (path == ":common") return@subprojects

    loom.runs {
        all {
            property("lambda.dev", "youtu.be/RYnFIRc0k6E")
        }
    }

    tasks {
        register<Exec>("renderDoc") {
            val javaHome = Jvm.current().javaHome
            val gradleWrapper = rootProject.tasks.wrapper.get().jarFile.absolutePath

            commandLine = listOf(
                findExecutable("renderdoccmd")
                    ?: throw FileNotFoundException("Could not find the renderdoccmd executable"),
                "capture", /* Remove the following 2 lines if you don't want api validation */ "--opt-api-validation", "--opt-api-validation-unmute", "--opt-hook-children", "--wait-for-exit", "--working-dir", ".", "$javaHome/bin/java", "-Xmx64m", "-Xms64m", /*"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005",*/ "-Dorg.gradle.appname=gradlew", "-Dorg.gradle.java.home=$javaHome", "-classpath", gradleWrapper, "org.gradle.wrapper.GradleWrapperMain", "${this@subprojects.path}:runClient",
            )
        }

        processResources {
            // Replaces placeholders in the mod info files
            filesMatching(targets) {
                expand(replacements)
            }

            // Forces the task to always run
            outputs.upToDateWhen { false }
        }
    }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    group = mavenGroup
    version = modVersion

    base.archivesName = modId

    repositories {
        mavenLocal() // Allow the use of local repositories
        maven("https://maven.shedaniel.me/") // Architectury
        maven("https://maven.terraformersmc.com/releases/")
        maven("https://babbaj.github.io/maven/") // Baritone
        maven("https://jitpack.io") // KDiscordIPC
        mavenCentral()

        // Allow the use of local libraries
        flatDir {
            dirs(libs)
        }
    }

    java {
        withSourcesJar()

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks {
        compileKotlin {
            compilerOptions {
                jvmTarget = JvmTarget.JVM_17
            }
        }
    }
}

private fun findExecutable(executable: String): String? {
    val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
    val cmd = if (isWindows) "where" else "which"

    return ProcessBuilder(cmd, executable).start().inputStream.bufferedReader().readText().trim().takeIf { it.isNotBlank() }
}
