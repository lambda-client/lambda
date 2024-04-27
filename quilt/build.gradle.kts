val quiltVersion = property("quilt_version").toString()
val quiltedFabricVersion = property("quilted_fabric_version").toString()
val kotlinQuiltVersion = property("kotlin_quilt_version").toString()
val discordIPCVersion = property("discord_ipc_version").toString()

base.archivesName.set("${base.archivesName.get()}-quilt")

architectury {
    platformSetupLoomIde()
    loader("quilt")
}

loom {
    accessWidenerPath = project(":common").loom.accessWidenerPath
}

repositories {
    maven("https://maven.quiltmc.org/repository/release/")
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentQuilt"].extendsFrom(this)
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
    // Quilt Loader
    modImplementation("org.quiltmc:quilt-loader:$quiltVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.28.0-GA")
    includeLib("dev.babbaj:nether-pathfinder:1.5")
    includeLib("com.github.caoimhebyrne:KDiscordIPC:$discordIPCVersion")

    // Add mods to the mod jar
    includeMod("org.quiltmc.quilted-fabric-api:quilted-fabric-api:$quiltedFabricVersion")
    includeMod("org.quiltmc.quilt-kotlin-libraries:quilt-kotlin-libraries:$kotlinQuiltVersion")

    // We'll need to include the fabric modloader in quilt for this to work
    // includeMod("baritone-api:baritone-unoptimized-fabric:1.10.2")


    // Common (Do not touch)
    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionQuilt"))

    // Finish the configuration
    setupConfigurations()
}

tasks {
    remapJar {
        injectAccessWidener = true
    }

    processResources {
        filesMatching("quilt.mod.json") {
            expand(project(":common").properties)
        }
    }
}
