val neoVersion = property("neo_version").toString()
val kotlinForgeVersion = property("kotlin_forge_version").toString()
val architecturyVersion = property("architectury_version").toString()
val mixinExtrasVersion = property("mixinextras_version").toString()

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
    maven("https://impactdevelopment.github.io/maven/")
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
        include(it)
    }

    includeMod.dependencies.forEach {
        modImplementation(it)
    }
}

dependencies {
    // NeoForge API
    neoForge("net.neoforged:neoforge:$neoVersion")

    // Remove the following line if you don't want to depend on the API
    modApi("dev.architectury:architectury-neoforge:$architecturyVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("nether-pathfinder:nether-pathfinder:1.4.1")

    // Add mods to the mod jar
    includeMod("thedarkcolour:kotlinforforge-neoforge:$kotlinForgeVersion")
    includeMod("baritone-api:baritone-unoptimized-neoforge:1.10.2")

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionNeoForge")) { isTransitive = false }

    // Finish the configuration
    setupConfigurations()
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("META-INF/mods.toml") {
            expand(getProperties())
            expand(mutableMapOf("version" to project.version))
        }
    }
}
