package com.lambda.common.context

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld

abstract class AbstractContext {
    val mc: MinecraftClient = MinecraftClient.getInstance()
    abstract val world: ClientWorld?
    abstract val player: ClientPlayerEntity?
    abstract val interaction: ClientPlayerInteractionManager?
    abstract val connection: ClientPlayNetworkHandler?
}
