package com.lambda.forge

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import com.lambda.core.registry.RegistryController
import com.lambda.core.registry.RegistryWrapper
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent
import net.minecraftforge.registries.RegisterEvent


@Mod(Lambda.MOD_ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object LambdaForge {
    init {
        Lambda.initialize()
        LOG.info("$MOD_NAME Forge $VERSION initialized.")
    }

    private fun onConstructMod(event: FMLConstructModEvent) {
        LOG.info("Construct mod event.")
    }

    private fun onRegistrySetup(event: RegisterEvent) {
        LOG.info("Register event for ${event.registryKey}.")

        RegistryController.register(event.registryKey, object : RegistryWrapper<Any> {
            override fun <T> registerForHolder(id: Identifier?, value: T): RegistryEntry<T> {
                return Registry.registerReference(event.getVanillaRegistry(), id, value)
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
