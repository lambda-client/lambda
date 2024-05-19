val minecraftVersion: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project
val kotlinFabricVersion: String by project
val discordIPCVersion: String by project

base.archivesName = "${base.archivesName.get()}-fabric"

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath
    enableTransitiveAccessWideners = true
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentFabric"].extendsFrom(this)
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
        modImplementation(it)
        include(it)
    }

    shadowBundle.dependencies.forEach {
        shadowCommon(it)
        shadow(it)
    }
}

dependencies {
    // Fabric API (Do not touch)
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.28.0-GA")
    includeLib("dev.babbaj:nether-pathfinder:1.5")
    includeLib("com.github.caoimhebyrne:KDiscordIPC:$discordIPCVersion")

    // Add mods to the mod jar
    includeMod("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion+$minecraftVersion")
    includeMod("net.fabricmc:fabric-language-kotlin:$kotlinFabricVersion")
    includeMod("baritone-api:baritone-unoptimized-fabric:1.10.2")

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(":common", configuration = "transformProductionFabric"))

    // Finish the configuration
    setupConfigurations()
}

tasks {
    // Access wideners are the successor of the mixins accessor
    // that were used in the past to access private fields and methods.
    // They allow you to make field, method, and class access public.
    remapJar {
        injectAccessWidener = true
    }
}
