rootProject.name = "Lambda"
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

include("common")
include("fabric")
include("forge")
