val fabricLoaderVersion = property("fabric_loader_version").toString()
val fabricKotlinVersion = property("fabric_kotlin_version").toString()
val mixinExtrasVersion = property("mixinextras_version").toString()
val architecturyVersion = property("architectury_version").toString()

architectury { common("fabric", "forge", "neoforge", "quilt") }

loom {
    silentMojangMappingsLicense()
    accessWidenerPath.set(File("src/main/resources/lambda.accesswidener"))
}

repositories {
    maven("https://impactdevelopment.github.io/maven/")
    maven("https://maven.fabricmc.net/")
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Remove the following line if you don't want to depend on the API
    modApi("dev.architectury:architectury:$architecturyVersion")

    // Add dependencies on the required Kotlin modules.
    implementation("org.reflections:reflections:0.10.2")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")
    modImplementation("baritone-api:baritone-api:1.10.2")
}

// Avoid nested jars
tasks.named("remapJar") {
    enabled = false
}

