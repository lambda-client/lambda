val fabricLoaderVersion = property("fabric_loader_version").toString()
val fabricKotlinVersion = property("fabric_kotlin_version").toString()
val mixinExtrasVersion = property("mixinextras_version").toString()
val kotlinXCoroutineVersion = property("kotlinx_coroutines_version").toString()

architectury { common("fabric", "forge", "neoforge") }

loom {
    accessWidenerPath.set(File("src/main/resources/lambda.accesswidener"))
}

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    maven("https://jitpack.io")

    mavenCentral()
    mavenLocal()
}

dependencies {
    // We depend on fabric loader here to use the fabric @Environment annotations and get the mixin dependencies
    // Do NOT use other classes from fabric loader
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    // Add dependencies on the required Kotlin modules.
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")
    implementation(annotationProcessor("io.github.llamalad7:mixinextras-common:$mixinExtrasVersion")!!)

    // Baritone
}

// Avoid nested jars
tasks.named("remapJar") {
    enabled = false
}

