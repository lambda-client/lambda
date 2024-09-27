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
    kotlin("jvm") version "2.0.20"
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
        "mappings"("net.fabricmc:yarn:$minecraftVersion+$yarnMappings:v2")
    }

    if (path == ":common") return@subprojects

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
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }
}

private fun findExecutable(executable: String): String? {
    val isWindows = Os.isFamily(Os.FAMILY_WINDOWS)
    val cmd = if (isWindows) "where" else "which"

    return ProcessBuilder(cmd, executable).start().inputStream.bufferedReader().readText().trim().takeIf { it.isNotBlank() }
}
