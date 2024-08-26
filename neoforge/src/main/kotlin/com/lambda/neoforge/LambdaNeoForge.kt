package com.lambda.neoforge

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import com.lambda.core.registry.RegistryController
import com.lambda.core.registry.RegistryWrapper
import com.lambda.graphics.RenderMain
import net.minecraft.registry.Registry
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.neoforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS

@Mod(Lambda.MOD_ID)
@OnlyIn(Dist.CLIENT)
object LambdaNeoForge {
    @SubscribeEvent
    fun onClient(event: FMLClientSetupEvent) {
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

    // Most events here are hooked due to neoforge not caring about others
    // and directly patching the minecraft classes.
    private object ClientEvents {
        @SubscribeEvent
        fun onHudRender(event: RenderGuiEvent.Post) { RenderMain.render2D() }
    }

    init {
        MOD_BUS.register(this)
        FORGE_BUS.register(ClientEvents)
    }
}
