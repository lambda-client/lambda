import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.dokka") version "1.9.20"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.7-SNAPSHOT" apply false
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
        "mappings"(loom.layered {
            mappings("net.fabricmc:yarn:$minecraftVersion+$yarnMappings:v2")
            mappings("dev.architectury:yarn-mappings-patch-neoforge:$minecraftVersion+build.4") // Fix the mappings interaction between Yarn and NeoForge
        })
    }

    if (path == ":common") return@subprojects

    tasks {
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

        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks {
        compileKotlin {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
    }
}
