val quiltVersion = property("quilt_version").toString()
val quiltedFabricVersion = property("quilted_fabric_version").toString()
val kotlinQuiltVersion = property("kotlin_quilt_version").toString()
val architecturyVersion = /*property("architectury_version").toString()*/ "10.1.19"
val kotlinxCoroutineVersion = property("kotlinx_coroutines_version").toString()

architectury {
    platformSetupLoomIde()
    loader("quilt")
}

base.archivesName.set("${base.archivesName.get()}-quilt")

loom {
    accessWidenerPath.set(project(":common").loom.accessWidenerPath)
    enableTransitiveAccessWideners.set(true)

    mods {
        register("quilt") {
            sourceSet("main", project(":quilt"))
        }
    }
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

fun DependencyHandlerScope.setupConfigurations() {
    includeLib.dependencies.forEach {
        implementation(it)
        include(it)
    }

    includeMod.dependencies.forEach {
        modImplementation(it)
        include(it)
    }
}

dependencies {
    // Quilt Loader
    modImplementation("org.quiltmc:quilt-loader:$quiltVersion")

    // Quilted Fabric API
    modApi("org.quiltmc.quilted-fabric-api:quilted-fabric-api:$quiltedFabricVersion")

    // Remove the following line if you don't want to depend on the API
    modApi("dev.architectury:architectury-fabric:$architecturyVersion") {
        exclude("net.fabricmc")
        exclude("net.fabricmc.fabric-api")
    }

    // Kotlin for Quilt
    modImplementation("org.quiltmc.quilt-kotlin-libraries:quilt-kotlin-libraries:$kotlinQuiltVersion")

    // Add dependencies on the required Kotlin modules.
    includeLib("org.reflections:reflections:0.10.2")
    includeLib("org.javassist:javassist:3.27.0-GA")

    // Add mods to the mod jar
    // includeMod(...)


    // Common (Do not touch)
    common(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(":common", configuration = "transformProductionQuilt")) { isTransitive = false }

    // Finish the configuration
    setupConfigurations()
}

tasks {
    processResources {
        inputs.property("group", project.group)
        inputs.property("version", project.version)

        filesMatching("quilt.mod.json") {
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
