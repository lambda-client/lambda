package com.lambda.context

import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld

open class ClientContext : AbstractContext() {
    final override val world: ClientWorld? = mc.world
    final override val player: ClientPlayerEntity? = mc.player
    final override val interaction: ClientPlayerInteractionManager? = mc.interactionManager
    final override val connection: ClientPlayNetworkHandler? = mc.networkHandler

    fun toSafe(): SafeContext? {
        if (world == null || player == null || interaction == null || connection == null) {
            return null
        }
        return SafeContext(world, player, interaction, connection)
    }
}