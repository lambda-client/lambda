package com.lambda.neoforge

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import com.lambda.core.registry.RegistryController
import com.lambda.core.registry.RegistryWrapper
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.common.Mod.EventBusSubscriber
import net.neoforged.neoforge.registries.RegisterEvent

@Mod(Lambda.MOD_ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object LambdaNeoForge {
    init {
        Lambda.initialize()
        LOG.info("$MOD_NAME Forge $VERSION initialized.")
    }

    @SubscribeEvent
    fun onRegistrySetup(event: RegisterEvent) {
        RegistryController.register(event.registryKey, object : RegistryWrapper<Any> {
            override fun <T> registerForHolder(id: Identifier?, value: T): RegistryEntry<T> {
                return Registry.registerReference(event.registry as Registry<T>, id, value)
            }
        })
    }
}
