val forgeVersion = property("forge_version").toString()
val kotlinVersion = property("kotlin_version").toString()
val kotlinxCoroutinesVersion = property("kotlinx_coroutines_version").toString()
val architecturyVersion = property("architectury_version").toString()
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

            sourceSets.forEach {
                val dir = layout.buildDirectory.dir("sourcesSets/${it.name}")
                it.output.setResourcesDir(dir)
                it.java.destinationDirectory.set(dir)
            }
        }
    }
}

repositories {
    maven("https://cursemaven.com")
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentForge"].extendsFrom(this)
}

val includeLib: Configuration by configurations.creating
val includeMod: Configuration by configurations.creating
val shadowInclude: Configuration by configurations.creating

fun DependencyHandlerScope.setupConfigurations() {
    includeLib.dependencies.forEach {
        implementation(it)
        include(it)
    }

    // Please look at this before yelling at me
    // https://docs.architectury.dev/loom/using_libraries/
    includeMod.dependencies.forEach {
        implementation(it)
        forgeRuntimeLibrary(it)
    }

    shadowInclude.dependencies.forEach {
        implementation(it)
        shadowCommon(it)
    }
}

dependencies {
    // Forge API
    forge("net.minecraftforge:forge:$forgeVersion")

    // Remove the following line if you don't want to depend on the API
    modApi("dev.architectury:architectury-forge:$architecturyVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.30.0-GA")

    // Add mods to the mod jar
    // includeMod(...)

    // Add Kotlin
    shadowInclude("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    shadowInclude("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    // MixinExtras
    implementation("io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")!!)

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionForge")) { isTransitive = false }

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
}
