val neoVersion = property("neo_version").toString()
val architecturyVersion = property("architectury_version").toString()
val kotlinForgeVersion = property("kotlin_forge_version").toString()

architectury {
    platformSetupLoomIde()
    neoForge()
}

base.archivesName.set("${base.archivesName.get()}-neoforge")

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
}

val includeLib: Configuration by configurations.creating
val includeMod: Configuration by configurations.creating

fun DependencyHandlerScope.setupConfigurations() {
    includeLib.dependencies.forEach {
        implementation(it)
        forgeRuntimeLibrary(it)
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

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionNeoForge")) { isTransitive = false }

    // Finish the configuration
    setupConfigurations()
}

tasks {
    processResources {
        inputs.property("group", project.group)
        inputs.property("version", project.version)

        filesMatching("META-INF/mods.toml") {
            expand(getProperties())
            expand(mutableMapOf(
                "group" to project.group,
                "version" to project.version,
            ))
        }
    }

    remapJar {
        atAccessWideners.add("lambda.accesswidener")
    }
}
