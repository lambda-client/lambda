val fabricLoaderVersion = property("fabric_loader_version").toString()
val mixinExtrasVersion = property("mixinextras_version").toString()
val kotlinVersion = property("kotlin_version").toString()
val kotlinxCoroutinesVersion = property("kotlinx_coroutines_version").toString()
val architecturyVersion = property("architectury_version").toString()
val discordIPCVersion = property("discord_ipc_version").toString()

architectury { common("fabric", "forge", "neoforge") }

loom {
    silentMojangMappingsLicense()
    accessWidenerPath.set(File("src/main/resources/lambda.accesswidener"))
}

repositories {
    maven("https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Add dependencies on the required Kotlin modules.
    implementation("org.reflections:reflections:0.10.2")

    // Add Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    // Add Discord IPC
    implementation("com.github.caoimhebyrne:KDiscordIPC:$discordIPCVersion")
}

tasks {
    remapJar {
        enabled = false
    }
}
