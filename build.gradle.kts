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
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.dokka") version "1.9.20"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.7-SNAPSHOT" apply false
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

    tasks {
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
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}
