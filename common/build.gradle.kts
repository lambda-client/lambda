val modId: String by project
val fabricLoaderVersion: String by project
val kotlinVersion: String by project
val kotlinxCoroutinesVersion: String by project

architectury { common("fabric", "forge", "neoforge") }

loom {
    silentMojangMappingsLicense()
    accessWidenerPath = File("src/main/resources/$modId.accesswidener")
}

repositories {
    maven("https://maven.fabricmc.net/")
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Add dependencies on the required Kotlin modules.
    implementation("org.reflections:reflections:0.10.2")

    // Add Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    // Baritone
    // modImplementation("baritone-api:baritone-api:1.10.2")
    modImplementation("baritone-api:baritone-unoptimized-fabric:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}
