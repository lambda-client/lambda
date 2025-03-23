/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

val modVersion: String by project
val minecraftVersion: String by project
val modId: String by project
val fabricLoaderVersion: String by project
val kotlinxCoroutinesVersion: String by project
val discordIPCVersion: String by project
val fuelVersion: String by project
val resultVersion: String by project

base.archivesName = "${base.archivesName.get()}-api"

architectury { common("fabric", "forge") }

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
    implementation("com.github.Edouard127:KDiscordIPC:$discordIPCVersion")
    implementation("com.pngencoder:pngencoder:0.15.0")

    // Fuel HTTP library and dependencies
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-gson:$fuelVersion")
    implementation("com.github.kittinunf.result:result-jvm:$resultVersion")

    // Add Kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    // Baritone
    modImplementation("baritone-api:baritone-unoptimized-fabric:1.10.2") { isTransitive = false }
}

tasks {
    remapJar {
        archiveVersion = "$modVersion+$minecraftVersion"
    }

    test {
        useJUnitPlatform()
    }
}
