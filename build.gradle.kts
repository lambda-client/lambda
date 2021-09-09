import net.minecraftforge.gradle.userdev.UserDevExtension
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace
import org.jetbrains.kotlin.konan.properties.loadProperties
import org.spongepowered.asm.gradle.plugins.MixinExtension

val modVersion: String by project
val apiVersion: String by project

group = "com.lambda"
version = modVersion

buildscript {
    repositories {
        mavenCentral()
        maven("https://files.minecraftforge.net/maven")
        maven("https://repo.spongepowered.org/repository/maven-public/")
    }

    dependencies {
        classpath("net.minecraftforge.gradle:ForgeGradle:4.+")
        classpath("org.spongepowered:mixingradle:0.7-SNAPSHOT")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.5.0")
    }
}

plugins {
    idea
    java
    kotlin("jvm")
    `maven-publish`
}

apply {
    plugin("org.jetbrains.dokka")
    plugin("net.minecraftforge.gradle")
    plugin("org.spongepowered.mixin")
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://impactdevelopment.github.io/maven/")
}

sourceSets {
    main {
        java {
            srcDirs("src/main/cape-api", "src/main/command", "src/main/commons", "src/main/event")
        }
    }
}

val library: Configuration by configurations.creating

val minecraftVersion: String by project
val forgeVersion: String by project
val mappingsChannel: String by project
val mappingsVersion: String by project

val kotlinxCoroutinesVersion: String by project

dependencies {
    // Jar packaging
    fun ModuleDependency.exclude(moduleName: String): ModuleDependency {
        return exclude(mapOf("module" to moduleName))
    }

    fun jarOnly(dependencyNotation: Any) {
        library(dependencyNotation)
    }

    // Forge
    "minecraft"("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    // Dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    implementation("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        exclude("commons-io")
        exclude("gson")
        exclude("guava")
        exclude("launchwrapper")
        exclude("log4j-core")
    }

    annotationProcessor("org.spongepowered:mixin:0.8.2:processor") {
        exclude("gson")
    }

    implementation("org.reflections:reflections:0.9.12") {
        exclude("gson")
        exclude("guava")
    }

    implementation("club.minnced:java-discord-rpc:2.0.2") {
        exclude("jna")
    }

    implementation("com.github.cabaletta:baritone:1.2.14")
    jarOnly("cabaletta:baritone-api:1.2")
}

configure<MixinExtension> {
    add(sourceSets.main.get(), "mixins.lambda.refmap.json")
}

configure<UserDevExtension> {
    mappings(
        mapOf(
            "channel" to mappingsChannel,
            "version" to mappingsVersion
        )
    )

    runs {
        create("client") {
            workingDirectory = project.file("run").path
            ideaModule("${project.name}.main")

            properties(
                mapOf(
                    "forge.logging.markers" to "SCAN,REGISTRIES,REGISTRYDUMP",
                    "forge.logging.console.level" to "info",
                    "fml.coreMods.load" to "com.lambda.client.LambdaCoreMod",
                    "mixin.env.disableRefMap" to "true"
                )
            )
        }
    }
}

configure<NamedDomainObjectContainer<RenameJarInPlace>> {
    create("releaseJar")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts",
                "-Xlambdas=indy"
            )
        }
    }

    val releaseJar by register<Jar>("releaseJar") {
        group = "build"

        finalizedBy("reobfReleaseJar")

        manifest {
            attributes(
                "Manifest-Version" to 1.0,
                "MixinConfigs" to "mixins.lambda.json",
                "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
                "FMLCorePluginContainsFMLMod" to true,
                "FMLCorePlugin" to "com.lambda.client.LambdaCoreMod",
                "ForceLoadAsMod" to true
            )
        }

        from(sourceSets.main.get().output)

        val regex = "baritone-1\\.2\\.\\d\\d\\.jar".toRegex()
        from(
            (configurations.runtimeClasspath.get().files - configurations["minecraft"])
                .filterNot {
                    it.name.matches(regex)
                }.map {
                    if (it.isDirectory) it else zipTree(it)
                }
        )

        from(
            library.map {
                if (it.isDirectory) it else zipTree(it)
            }
        )

        System.getenv("MOD_VERSION_OVERRIDE")?.let {
            archiveVersion.set(it)
        }
    }

    val apiSourcesJar by register<Jar>("apiSourcesJar") {
        group = "build"

        from(sourceSets.main.get().allSource)
        archiveAppendix.set("api")
        archiveClassifier.set("sources")
        archiveVersion.set(apiVersion)
    }

    jar {
        archiveAppendix.set("api")
        archiveVersion.set(apiVersion)
    }

    register<Task>("buildAll") {
        group = "build"

        dependsOn(jar)
        dependsOn(releaseJar)
        dependsOn(apiSourcesJar)
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                artifactId = "lambda-api"
                version = apiVersion

                from(project.components["kotlin"])
                artifact(apiSourcesJar)
            }
        }

        repositories {
            maven("https://maven.pkg.github.com/lambda-client/lambda-api") {
                val githubProperty = runCatching {
                    loadProperties("${projectDir.absolutePath}/github.properties")
                }.getOrNull()

                credentials {
                    username = githubProperty?.getProperty("username") ?: System.getenv("GITHUB_ACTOR")
                    password = githubProperty?.getProperty("token") ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    register<Task>("genRuns") {
        group = "ide"
        doLast {
            file(File(rootDir, ".idea/runConfigurations/${project.name}_runClient.xml")).writer().use {
                it.write(
                    """
                        <component name="ProjectRunConfigurationManager">
                          <configuration default="false" name="${project.name} runClient" type="Application" factoryName="Application">
                            <envs>
                              <env name="MCP_TO_SRG" value="${"$"}PROJECT_DIR$/build/createSrgToMcp/output.srg" />
                              <env name="MOD_CLASSES" value="${"$"}PROJECT_DIR$/build/resources/main;${"$"}PROJECT_DIR$/build/classes/java/main;${"$"}PROJECT_DIR$/build/classes/kotlin/main" />
                              <env name="mainClass" value="net.minecraft.launchwrapper.Launch" />
                              <env name="MCP_MAPPINGS" value="${mappingsChannel}_$mappingsVersion" />
                              <env name="FORGE_VERSION" value="$forgeVersion" />
                              <env name="assetIndex" value="${minecraftVersion.substringBeforeLast(".")}" />
                              <env name="assetDirectory" value="${gradle.gradleUserHomeDir.path.replace("\\", "/")}/caches/forge_gradle/assets" />
                              <env name="nativesDirectory" value="${"$"}PROJECT_DIR$/build/natives" />
                              <env name="FORGE_GROUP" value="net.minecraftforge" />
                              <env name="tweakClass" value="net.minecraftforge.fml.common.launcher.FMLTweaker" />
                              <env name="MC_VERSION" value="${"$"}{MC_VERSION}" />
                            </envs>
                            <option name="MAIN_CLASS_NAME" value="net.minecraftforge.legacydev.MainClient" />
                            <module name="${project.name}.main" />
                            <option name="PROGRAM_PARAMETERS" value="--width 1280 --height 720" />
                            <option name="VM_PARAMETERS" value="-Dforge.logging.console.level=info -Dforge.logging.markers=SCAN,REGISTRIES,REGISTRYDUMP -Dmixin.env.disableRefMap=true -Dfml.coreMods.load=com.lambda.client.LambdaCoreMod" />
                            <option name="WORKING_DIRECTORY" value="${"$"}PROJECT_DIR$/run" />
                            <method v="2">
                              <option name="Gradle.BeforeRunTask" enabled="true" tasks="prepareRunClient" externalProjectPath="${"$"}PROJECT_DIR$" />
                            </method>
                          </configuration>
                        </component>
                    """.trimIndent()
                )
            }
        }
    }
}

afterEvaluate {
    artifacts {
        archives(tasks.getByName("releaseJar"))
    }

    // This is hacky af but it prevents obfuscating the default jar
    tasks.assemble {
        tasks.assemble.get().dependsOn.removeAll { it is RenameJarInPlace }
    }
}