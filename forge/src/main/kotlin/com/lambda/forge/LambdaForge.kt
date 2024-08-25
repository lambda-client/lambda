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
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.registries.RegisterEvent


@Mod(Lambda.MOD_ID)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
object LambdaForge {
    @SubscribeEvent
    fun onClient(event: FMLClientSetupEvent) {
        Lambda.initialize()
        LOG.info("$MOD_NAME Forge $VERSION initialized.")
    }

    @SubscribeEvent
    fun onRegistrySetup(event: RegisterEvent) {
        RegistryController.register(event.registryKey, object : RegistryWrapper<Any> {
            override fun <T> registerForHolder(id: Identifier?, value: T): RegistryEntry<T> {
                return Registry.registerReference(event.getVanillaRegistry(), id, value)
            }
        })
    }
}
