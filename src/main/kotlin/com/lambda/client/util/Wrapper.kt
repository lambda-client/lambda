package com.lambda.client.util

import com.lambda.client.LambdaMod
import com.lambda.client.event.events.ShutdownEvent
import com.lambda.client.util.ConfigUtils.saveAll
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.multiplayer.WorldClient

object Wrapper {
    @JvmStatic
    val minecraft: Minecraft
        get() = Minecraft.getMinecraft()

    @JvmStatic
    val player: EntityPlayerSP?
        get() = minecraft.player

    @JvmStatic
    val world: WorldClient?
        get() = minecraft.world

    @JvmStatic
    fun saveAndShutdown() {
        if (!LambdaMod.ready) return

        ShutdownEvent.post()
        saveAll()
    }
}