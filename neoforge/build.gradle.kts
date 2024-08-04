val modVersion: String by project
val minecraftVersion: String by project
val neoVersion: String by project
val kotlinForgeVersion: String by project

base.archivesName = "${base.archivesName.get()}-neoforge"

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

architectury {
    platformSetupLoomIde()
    neoForge {
        platformPackage = "forge"
    }
}

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
}

repositories {
    // You can add more repositories here if you plan
    // on using environment-specific dependencies.
    // If you simply want to add a global plugin repository,
    // you can add it to the `settings.gradle.kts` file
    // in the base of the project and gradle will do the
    // rest for you.
    maven("https://maven.neoforged.net/releases/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentNeoForge"].extendsFrom(this)
    isCanBeResolved = true
    isCanBeConsumed = false
}

val includeLib: Configuration by configurations.creating
val includeMod: Configuration by configurations.creating
val shadowBundle: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

fun DependencyHandlerScope.setupConfigurations() {
    includeLib.dependencies.forEach {
        implementation(it)
        include(it)
    }

    includeMod.dependencies.forEach {
        implementation(it)
        include(it)
    }
}

dependencies {
    // NeoForge API
    neoForge("net.neoforged:neoforge:$neoVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.28.0-GA")

    // Add mods to the mod jar
    includeMod("thedarkcolour:kotlinforforge-neoforge:$kotlinForgeVersion")
    includeMod("baritone-api:baritone-unoptimized-neoforge:1.10.2")

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionNeoForge")) { isTransitive = false }

    // Finish the configuration
    setupConfigurations()
}

tasks {
    shadowJar {
        archiveVersion = "$modVersion+$minecraftVersion"
        configurations = listOf(shadowBundle)
        archiveClassifier = "dev-shadow"
    }

    remapJar {
        dependsOn(shadowJar)

        archiveVersion = "$modVersion+$minecraftVersion"
        inputFile = shadowJar.get().archiveFile

        atAccessWideners.add("lambda.accesswidener")
    }
}
