package com.lambda.fabric

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import com.lambda.core.registry.RegistryController
import com.lambda.core.registry.RegistryWrapper
import net.fabricmc.api.ClientModInitializer
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier

object LambdaFabric : ClientModInitializer {
    override fun onInitializeClient() {
        Lambda.initialize()
        LOG.info("$MOD_NAME Fabric $VERSION initialized.")

        Registries.REGISTRIES.forEach { registry ->
            RegistryController.register(registry.key, object : RegistryWrapper<Any> {
                override fun <T> registerForHolder(id: Identifier?, value: T): RegistryEntry<T> {
                    return Registry.registerReference(registry as Registry<T>, id, value)
                }
            })
        }
    }
}
