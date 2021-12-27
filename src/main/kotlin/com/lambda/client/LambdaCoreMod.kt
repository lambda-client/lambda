package com.lambda.client

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin.MCVersion
import org.apache.logging.log4j.LogManager
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.mixin.MixinEnvironment
import org.spongepowered.asm.mixin.Mixins

@IFMLLoadingPlugin.Name("LambdaCoreMod")
@MCVersion("1.12.2")
class LambdaCoreMod : IFMLLoadingPlugin {
    override fun getASMTransformerClass(): Array<String> {
        return emptyArray()
    }

    override fun getModContainerClass(): String? {
        return null
    }

    override fun getSetupClass(): String? {
        return null
    }


    override fun injectData(data: Map<String, Any>) {}

    override fun getAccessTransformerClass(): String? {
        return null
    }

    init {
        val logger = LogManager.getLogger("Lambda")

        MixinBootstrap.init()
        Mixins.addConfigurations("mixins.lambda.json", "mixins.baritone.json")
        MixinEnvironment.getDefaultEnvironment().obfuscationContext = "searge"
        logger.info("Lambda and Baritone mixins initialised.")
    }
}