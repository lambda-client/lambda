package com.lambda.fabric

import com.lambda.Lambda.LOG
import com.lambda.plugin.PluginRegistry
import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.mixin.Mixins
import kotlin.system.measureTimeMillis

object LambdaBootstrap : PreLaunchEntrypoint {
    // This function ensure that plugins have their
    // mixins and access widener configurations loaded
    // before the game starts
    override fun onPreLaunch() {
        MixinBootstrap.init() // Important to initialize Mixins

        val runDirectory = FabricLoader.getInstance().gameDir.resolve("lambda/plugins").toFile()
        if (!runDirectory.exists()) runDirectory.mkdirs()

        var validPlugins = 0

        val time = measureTimeMillis {
            PluginRegistry.preLoad(runDirectory)
                .forEach { loader ->
                    Mixins.addConfiguration(loader.mixinFileName)
                    // Mixins.addConfiguration(accessWidener.absolutePath) // TODO: Add access widener support
                    validPlugins++
                }
        }

        LOG.info("Pre-loaded $validPlugins plugins in $time ms.")
    }
}
