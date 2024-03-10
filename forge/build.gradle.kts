val forgeVersion = property("forge_version").toString()
val kotlinForgeVersion = property("kotlin_forge_version").toString()
val mixinExtrasVersion = property("mixinextras_version").toString()

architectury {
    platformSetupLoomIde()
    forge()
}

base.archivesName.set("${base.archivesName.get()}-forge")

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)

    forge {
        convertAccessWideners = true
        extraAccessWideners.add(loom.accessWidenerPath.get().asFile.name)
        mixinConfig("lambda.mixins.common.json")
    }

    mods {
        register("forge") {
            sourceSet("main", project(":forge"))
        }
    }
}

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://cursemaven.com")
}

val includeLib: Configuration by configurations.creating
val includeMod: Configuration by configurations.creating

fun DependencyHandlerScope.setupConfigurations() {
    includeLib.dependencies.forEach {
        implementation(it)
        include(it)
    }

    // Please look at this before yelling at me
    // https://docs.architectury.dev/loom/using_libraries/
    includeMod.dependencies.forEach {
        implementation(it)
        forgeRuntimeLibrary(it) // Avoid mods not being found in dev environment
    }
}

dependencies {
    // Forge API
    forge("net.minecraftforge:forge:$forgeVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.27.0-GA")

    // Add mods to the mod jar
    includeMod("thedarkcolour:kotlinforforge:$kotlinForgeVersion")
    includeMod("io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")!!)

    // Bugfixes
    compileOnly(kotlin("stdlib")) // Hack https://github.com/thedarkcolour/KotlinForForge/issues/93

    // Common (Do not touch)
    implementation(project(":common", configuration = "namedElements")) { isTransitive = false } // We cannot common here because it is treated as a different mod and forge will panic
    shadowCommon(project(path = ":common", configuration = "transformProductionForge")) { isTransitive = false }

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
