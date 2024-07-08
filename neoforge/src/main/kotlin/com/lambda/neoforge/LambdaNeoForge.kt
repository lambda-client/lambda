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
import net.neoforged.fml.common.Mod
import net.neoforged.fml.common.Mod.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent
import net.neoforged.neoforge.registries.RegisterEvent

@Mod(Lambda.MOD_ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object LambdaNeoForge {
    init {
        Lambda.initialize()
        LOG.info("$MOD_NAME NeoForge $VERSION initialized.")
    }

    private fun onConstructMod(event: FMLConstructModEvent) {
        LOG.info("Construct mod event.")
    }

    private fun onRegistrySetup(event: RegisterEvent) {
        LOG.info("Register event for ${event.registryKey}.")

        RegistryController.register(event.registryKey, object : RegistryWrapper<Any> {
            override fun <T> registerForHolder(id: Identifier?, value: T): RegistryEntry<T> {
                return Registry.registerReference(event.registry as Registry<T>, id, value)
            }
        })
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOG.info("Client setup event.")
    }

    private fun onSidedSetup(event: FMLClientSetupEvent) {
        LOG.info("Sided setup event.")
    }

    private fun onInterModComms(event: FMLClientSetupEvent) {
        LOG.info("Inter-mod comms event.")
    }

    private fun onComplete(event: FMLClientSetupEvent) {
        LOG.info("Complete event.")
    }
}
