import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.task.RemapJarTask

val modId = project.properties["mod_id"].toString()
val modVersion = project.properties["mod_version"].toString()
val mavenGroup = project.properties["maven_group"].toString()
val minecraftVersion = project.properties["minecraft_version"].toString()
val yarnMappings = project.properties["yarn_mappings"].toString()

plugins {
    kotlin("jvm") version ("1.9.22")
    id("org.jetbrains.dokka") version "1.9.20"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.5-SNAPSHOT" apply false
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
        "mappings"("net.fabricmc:yarn:$yarnMappings:v2")
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
    }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    base.archivesName.set(modId)
    group = mavenGroup
    version = modVersion

    repositories {
        maven("https://api.modrinth.com/maven")
        maven("https://jitpack.io")
        maven("https://maven.shedaniel.me/") {
            name = "Architectury"
        }
        maven("https://maven.terraformersmc.com/releases/")
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.release = 17
        }
        compileKotlin {
            kotlinOptions.jvmTarget = "17"
        }
    }
}
