package com.lambda.fabric

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import com.lambda.core.registry.AgnosticRegistries
import net.fabricmc.api.ClientModInitializer
import net.minecraft.registry.Registries

object LambdaFabric : ClientModInitializer {
    override fun onInitializeClient() {
        Lambda.initialize {
            Registries.REGISTRIES.forEach(AgnosticRegistries::dump)
            LOG.info("$MOD_NAME Fabric $VERSION initialized.")
        }
    }
}
