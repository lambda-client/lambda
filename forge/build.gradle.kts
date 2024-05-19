val minecraftVersion: String by project
val forgeVersion: String by project
val mixinExtrasVersion: String by project
val kotlinForgeVersion: String by project
val discordIPCVersion: String by project

base.archivesName = "${base.archivesName.get()}-forge"

architectury {
    platformSetupLoomIde()
    forge()
}

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
    forge {
        // This is required to convert the access wideners to the forge
        // format, access transformers.
        convertAccessWideners = true

        // Add the mod's mixins to the list of mixins to be applied.
        // In the extraordinary case that you need to add mixins for
        // different mod loaders, you can add them using the
        // `extraAccessWideners` property.
        // And then add them to the `mixinConfig` function.
        mixinConfig("lambda.mixins.common.json")
    }
}

repositories {
    // You can add more repositories here if you plan
    // on using environment-specific dependencies.
    // If you simply want to add a global repository,
    // you can add it to the `settings.gradle.kts` file
    // in the base of the project and gradle will do the
    // rest for you.
    maven("https://thedarkcolour.github.io/KotlinForForge/")
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentForge"].extendsFrom(this)
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
        forgeRuntimeLibrary(it)
        implementation(it)
        include(it)
    }

    includeMod.dependencies.forEach {
        forgeRuntimeLibrary(it)
        implementation(it)
        include(it)
    }

    shadowBundle.dependencies.forEach {
        shadowCommon(it)
        shadow(it)
    }
}

dependencies {
    // Forge API
    forge("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.28.0-GA")

    // Temporary, only works for production
    // See https://github.com/MinecraftForge/MinecraftForge/issues/8878
    shadowBundle("com.github.caoimhebyrne:KDiscordIPC:$discordIPCVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }

    // Add mods to the mod jar
    includeMod("thedarkcolour:kotlinforforge:$kotlinForgeVersion")
    includeMod("baritone-api:baritone-unoptimized-forge:1.10.2")

    // MixinExtras
    implementation("io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion")
    compileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")!!)

    // Fix KFF
    compileOnly(kotlin("stdlib"))

    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(path = ":common", configuration = "transformProductionForge"))

    // Finish the configuration
    setupConfigurations()
}

tasks {
    sourceSets.forEach {
        val dir = layout.buildDirectory.dir("sourcesSets/${it.name}")
        it.output.setResourcesDir(dir)
        it.java.destinationDirectory.set(dir)
    }
}
