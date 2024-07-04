val neoVersion: String by project
val kotlinForgeVersion: String by project

base.archivesName = "${base.archivesName.get()}-neoforge"

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
}

repositories {
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

    shadowBundle.dependencies.forEach {
        shadowCommon(it)
        shadow(it)
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

    // Disable reflections logging
    include("org.slf4j:slf4j-nop:2.0.13")

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionNeoForge"))

    // Finish the configuration
    setupConfigurations()
}

tasks {
    processResources {
        from(project(":common").file("src/main/resources/lambda.accesswidener")) {
            into("/assets/") // Copy the access wideners because the API was not included for NeoForge
        }
    }

    remapJar {
        dependsOn(processResources)
        atAccessWideners.add("lambda.accesswidener") // Add the access widener to the remapper
    }
}
