import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.*

val targets = listOf("META-INF/*.toml", "fabric.mod.json")
val replacements = file("gradle.properties").inputStream().use { stream ->
    Properties().apply { load(stream) }
}.map { (k, v) -> k.toString() to v.toString() }.toMap()

val modId: String by project
val modVersion: String by project
val mavenGroup: String by project
val minecraftVersion: String by project
val yarnMappings: String by project

val libs = file("libs")

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.dokka") version "1.9.20"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.6-SNAPSHOT" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

architectury {
    minecraft = minecraftVersion
}

subprojects {
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "org.jetbrains.dokka")

    dependencies {
        "minecraft"("com.mojang:minecraft:$minecraftVersion")
        "mappings"("net.fabricmc:yarn:$minecraftVersion+$yarnMappings:v2")
    }

    if (path == ":common") return@subprojects

    apply(plugin = "com.github.johnrengelman.shadow")

    val versionWithMCVersion = "$modVersion+$minecraftVersion"

    tasks {
        val shadowCommon by configurations.creating {
            isCanBeConsumed = false
            isCanBeResolved = true
        }

        val shadow = named<ShadowJar>("shadowJar") {
            archiveVersion = versionWithMCVersion
            archiveClassifier.set("shadow")
            configurations = listOf(shadowCommon)
        }

        named<RemapJarTask>("remapJar") {
            dependsOn(shadow)
            inputFile = shadow.flatMap { it.archiveFile }
            archiveVersion = versionWithMCVersion
            archiveClassifier = ""
        }

        jar {
            enabled = false
        }

        processResources {
            // Replaces placeholders in the mod info files
            filesMatching(targets) {
                expand(replacements)
            }
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
        maven("https://api.modrinth.com/maven")
        maven("https://jitpack.io")
        maven("https://maven.shedaniel.me/") { name = "Architectury" }
        maven("https://maven.terraformersmc.com/releases/")
        maven("https://babbaj.github.io/maven/")

        // Allow the use of local libraries
        flatDir {
            dirs(libs)
        }
    }

    java {
        withSourcesJar()

        sourceCompatibility = JavaVersion.VERSION_22
        targetCompatibility = JavaVersion.VERSION_22
    }

    tasks {
        compileKotlin {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_22)
            }
        }
    }
}
