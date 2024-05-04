import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import java.util.*

val modId = property("mod_id").toString()
val modVersion = property("mod_version").toString()
val mavenGroup = property("maven_group").toString()
val minecraftVersion = property("minecraft_version").toString()
val yarnMappings = property("yarn_mappings").toString()

val libs = file("libs")
val Project.loom: LoomGradleExtensionAPI
    get() = (this as ExtensionAware).extensions.getByName("loom") as LoomGradleExtensionAPI

val targets = listOf("META-INF/*.toml", "fabric.mod.json")
val replacements = file("gradle.properties").inputStream().use { stream ->
    Properties().apply { load(stream) }
}.map { (k, v) -> k.toString() to v.toString() }.toMap()

plugins {
    kotlin("jvm") version "1.9.23"
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
        "mappings"("net.fabricmc:yarn:$yarnMappings:v2")
    }

    repositories {
        maven("https://babbaj.github.io/maven/")
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

    base.archivesName.set(modId)
    group = mavenGroup
    version = modVersion

    repositories {
        maven("https://api.modrinth.com/maven")
        maven("https://jitpack.io")
        maven("https://maven.shedaniel.me/") { name = "Architectury" }
        maven("https://maven.terraformersmc.com/releases/")

        flatDir {
            dirs(libs)
        }
    }

    java {
        // Uncomment these lines when the plugin system is ready
        // withSourcesJar()
        // withJavadocJar()

        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
