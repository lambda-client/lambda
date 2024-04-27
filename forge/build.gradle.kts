val forgeVersion = property("forge_version").toString()
val mixinExtrasVersion = property("mixinextras_version").toString()
val kotlinForgeVersion = property("kotlin_forge_version").toString()
val discordIPCVersion = property("discord_ipc_version").toString()

base.archivesName.set("${base.archivesName.get()}-forge")

architectury {
    platformSetupLoomIde()
    forge()
}

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
    forge {
        convertAccessWideners = true
        extraAccessWideners.add(loom.accessWidenerPath.get().asFile.name)
        mixinConfig("lambda.mixins.common.json")
    }
}

repositories {
    maven("https://cursemaven.com")
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
    forge("net.minecraftforge:forge:$forgeVersion")

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
    remapJar {
        injectAccessWidener = true
    }

    processResources {
        filesMatching("META-INF/mods.toml") {
            expand(project(":common").properties)
        }
    }

    sourceSets.forEach {
        val dir = layout.buildDirectory.dir("sourcesSets/${it.name}")
        it.output.setResourcesDir(dir)
        it.java.destinationDirectory.set(dir)
    }
}
