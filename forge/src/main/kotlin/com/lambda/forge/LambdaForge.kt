package com.lambda.forge

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import com.lambda.core.registry.AgnosticRegistries
import com.lambda.graphics.RenderMain
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.registries.RegisterEvent
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import thedarkcolour.kotlinforforge.forge.MOD_BUS


@Mod(Lambda.MOD_ID)
@OnlyIn(Dist.CLIENT)
object LambdaForge {
    @SubscribeEvent
    fun onClient(event: FMLClientSetupEvent) {
        Lambda.initialize()
        LOG.info("$MOD_NAME Forge $VERSION initialized.")
    }

    // Forge forces the user to user their event in order to interact with registries.
    @SubscribeEvent
    fun onRegistrySetup(event: RegisterEvent) = AgnosticRegistries.dump(event.getVanillaRegistry<Any>())

    // Most events here are hooked due to forge not caring about others
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
