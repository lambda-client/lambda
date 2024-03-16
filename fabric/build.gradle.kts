val fabricLoaderVersion = property("fabric_loader_version").toString()
val fabricApiVersion = property("fabric_api_version").toString()
val fabricKotlinVersion = property("fabric_kotlin_version").toString()
val architecturyVersion = property("architectury_version").toString()

architectury {
    platformSetupLoomIde()
    fabric()
}

base.archivesName.set("${base.archivesName.get()}-fabric")

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
    enableTransitiveAccessWideners.set(true)
}

repositories {
    maven("https://impactdevelopment.github.io/maven/")
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentFabric"].extendsFrom(this)
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
    // Fabric API (Do not touch)
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modApi("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Remove the following line if you don't want to depend on the API
    modApi("dev.architectury:architectury-fabric:$architecturyVersion")

    // Kotlin for Fabric
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.27.0-GA")
    includeLib("nether-pathfinder:nether-pathfinder:1.4.1")

    // Add mods to the mod jar
    includeMod("baritone-api:baritone-unoptimized-fabric:1.10.2")

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(":common", configuration = "transformProductionFabric")) { isTransitive = false }

    // Finish the configuration
    setupConfigurations()
}

tasks {
    processResources {
        inputs.property("group", project.group)
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(getProperties())
            expand(mutableMapOf(
                "group" to project.group,
                "version" to project.version
            ))
        }
    }

    remapJar {
        injectAccessWidener.set(true)
    }
}
