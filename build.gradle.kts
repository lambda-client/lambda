
import java.util.*

val modId: String by project
val mavenGroup: String by project
val modVersion: String by project
val minecraftVersion: String by project
val yarnMappings: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project
val kotlinFabricVersion: String by project
val pngEncoderVersion: String by project
val discordIPCVersion: String by project
val classGraphVersion: String by project
val kotlinVersion: String by project
val ktorVersion: String by project
val jacksonVersion: String by project
val mockkVersion: String by project
val spairVersion: String by project
val lwjglVersion: String by project
val sodiumVersion: String by project
val litematicaVersion: String by project
val maLiLibVersion: String by project

val libs = file("libs")
val targets = listOf("fabric.mod.json")
val replacements = file("gradle.properties").inputStream().use { stream ->
    Properties().apply { load(stream) }
}.map { (k, v) -> k.toString() to v.toString() }.toMap()

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.dokka") version "2.1.0"
    id("fabric-loom") version "1.16-SNAPSHOT"
    id("com.gradleup.shadow") version "9.3.0"
    id("maven-publish")
}

group = mavenGroup
version = modVersion

base.archivesName = modId

// We need to force it using lwjgl 3.3.3 because of 3.3.4 poor support for Wayland protocol
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.lwjgl") {
            useVersion(lwjglVersion)
        }
    }
}

repositories {
    mavenLocal() // Allow the use of local repositories
    maven("https://maven.lambda-client.org/releases")
    maven("https://jitpack.io") // KDiscordIPC
    maven("https://api.modrinth.com/maven")
    mavenCentral()

    // Allow the use of local libraries
    flatDir {
        dirs(libs)
    }
}

fabricApi {
    @Suppress("UnstableApiUsage")
    configureTests {
        modId = "${base.archivesName}-tests"
        eula = true
        createSourceSet = true

        enableGameTests = false
        enableClientGameTests = true
        clearRunDirectory = false
    }
}

loom {
    accessWidenerPath = file("src/main/resources/$modId.accesswidener")

    // Apply access wideners transitively (other mods)
    enableTransitiveAccessWideners = true

    runs {
        all {
            property("minato.dev", "youtu.be/RYnFIRc0k6E")

            property("org.lwjgl.util.Debug", "true")
            property("org.lwjgl.util.DebugLoader", "true")
            //property("org.lwjgl.util.DebugAllocator", "true")
            //property("org.lwjgl.util.DebugAllocator.fast", "true")
            property("org.lwjgl.util.DebugStack", "true")
            property("org.lwjgl.util.DebugFunctions", "true")
            property("mixin.debug.export", "true")

            vmArgs("-XX:+HeapDumpOnOutOfMemoryError", "-XX:+CreateCoredumpOnCrash")
            programArgs("--username", "Steve", "--uuid", "8667ba71b85a4004af54457a9734eed7", "--accessToken", "****")
        }
    }
}

val includeLib: Configuration by configurations.creating
val includeMod: Configuration by configurations.creating
val shadowLib: Configuration by configurations.creating { isCanBeConsumed = false }
val shadowMod: Configuration by configurations.creating { isCanBeConsumed = false }

fun DependencyHandlerScope.setupConfigurations() {
    includeLib.dependencies.forEach {
        implementation(it)
        include(it)
    }

    includeMod.dependencies.forEach {
        modImplementation(it)
        include(it)
    }

    shadowLib.dependencies.forEach {
        implementation(it)
    }

    shadowMod.dependencies.forEach {
        modImplementation(it)
    }
}

dependencies {
    // Read this if you'd like to understand the gradle dependency hell
    // https://medium.com/@nagendra.raja/understanding-configurations-and-dependencies-in-gradle-ad0827619501

    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$minecraftVersion+$yarnMappings:v2")

    // Fabric
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion+$minecraftVersion")
    // Explicit dependency on fabric-renderer-indigo for mixin access to internal classes
    modCompileOnly("net.fabricmc.fabric-api:fabric-renderer-indigo:4.1.4+1af5c5a75f")
    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlinFabricVersion.$kotlinVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("io.github.classgraph:classgraph:${classGraphVersion}")
    includeLib("com.github.emyfops:KDiscordIPC:$discordIPCVersion")
    includeLib("com.pngencoder:pngencoder:$pngEncoderVersion")

    includeLib("com.lambda:lambda-imgui-java-binding:$spairVersion")
    includeLib("com.lambda:lambda-imgui-java-lwjgl3:$spairVersion")
    includeLib("com.lambda:lambda-imgui-java-natives-windows:$spairVersion")

    // Ktor
    includeLib("io.ktor:ktor-client-core:$ktorVersion")
    shadowLib("io.ktor:ktor-client-cio:$ktorVersion") {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }
    includeLib("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    includeLib("io.ktor:ktor-serialization-jackson:$ktorVersion")
    includeLib("com.fasterxml.jackson.core:jackson-annotations:2.21")
    // Jackson 2 core + databind needed by io.ktor:ktor-serialization-jackson (provides
    // JsonEncoding / ObjectMapper). Coexists with Jackson 3 (tools.jackson.*) via distinct
    // package namespaces and distinct META-INF/services keys.
    // Note: jackson-annotations is published with bare minor (`2.21`) while core/databind use
    // patch versions (`2.21.0`).
    includeLib("com.fasterxml.jackson.core:jackson-core:2.21.0")
    includeLib("com.fasterxml.jackson.core:jackson-databind:2.21.0")
    // Jackson 2 kotlin module required by io.ktor:ktor-serialization-jackson (JacksonConverter).
    // Without it, ktor crashes with NoClassDefFoundError: com.fasterxml.jackson.module.kotlin.ExtensionsKt.
    includeLib("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.0")
    includeLib("tools.jackson.core:jackson-core:$jacksonVersion")
    includeLib("tools.jackson.core:jackson-databind:$jacksonVersion")
    includeLib("tools.jackson.module:jackson-module-kotlin:$jacksonVersion")

    // Add mods
    modCompileOnly("maven.modrinth:sodium:$sodiumVersion")
    modCompileOnly("maven.modrinth:malilib:$maLiLibVersion")
    modCompileOnly("maven.modrinth:litematica:$litematicaVersion")

	// DevLogin
	modRuntimeOnly("com.ptsmods:devlogin:3.5")

    // Test implementations
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:${mockkVersion}")

    // Finish the configuration
    setupConfigurations()
}

tasks {
    test {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    }

    shadowJar {
        archiveClassifier = "dev-shadow"
        archiveVersion = "$modVersion+$minecraftVersion"
        configurations = listOf(shadowLib, shadowMod)
    }

    remapJar {
        dependsOn(shadowJar)

        inputFile = shadowJar.get().archiveFile
        archiveVersion = "$modVersion+$minecraftVersion"
    }

    processResources {
        filesMatching(targets) { expand(replacements) }

        // Forces the task to always run
        outputs.upToDateWhen { false }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xcontext-parameters", "-Xconsistent-data-class-copy-visibility", "-Xannotation-default-target=param-property")
    }

    jvmToolchain(21)
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

publishing {
    val publishType = project.findProperty("mavenType").toString()
    val isSnapshots = publishType == "snapshots"
    val mavenUrl = if (isSnapshots) "https://maven.lambda-client.org/snapshots" else "https://maven.lambda-client.org/releases"
    val mavenVersion =
        if (isSnapshots) "$modVersion+$minecraftVersion-SNAPSHOT"
        else "$modVersion+$minecraftVersion"

	publications {
        create<MavenPublication>("maven") {
            groupId = mavenGroup
            artifactId = modId
            version = mavenVersion

            from(components["java"])
        }
    }

    repositories {
        maven(mavenUrl) {
            name = "minato-reposilite"

            credentials {
                username = project.findProperty("mavenUsername").toString()
                password = project.findProperty("mavenPassword").toString()
            }


            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}