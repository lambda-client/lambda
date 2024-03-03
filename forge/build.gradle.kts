val forgeVersion = project.properties["forge_version"].toString()
val kotlinForgeVersion = project.properties["kotlin_forge_version"].toString()
val mixinExtrasVersion = project.properties["mixinextras_version"].toString()

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
}

repositories {
    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        name = "KotlinForForge"
    }
    maven("https://cursemaven.com") {
        name = "Curse"
    }
}

val common: Configuration by configurations.creating {
    configurations.compileClasspath.get().extendsFrom(this)
    configurations.runtimeClasspath.get().extendsFrom(this)
    configurations["developmentForge"].extendsFrom(this)
}

dependencies {
    forge("net.minecraftforge:forge:$forgeVersion")
    implementation("thedarkcolour:kotlinforforge:$kotlinForgeVersion")
    common(project(":common", configuration = "namedElements")) {
        isTransitive = false
    }
    shadowCommon(project(path = ":common", configuration = "transformProductionForge")) {
        isTransitive = false
    }
    implementation(annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")!!)
    implementation(include("io.github.llamalad7:mixinextras-forge:$mixinExtrasVersion")!!)
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
