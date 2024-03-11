package com.lambda.context

import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld
import net.minecraft.client.MinecraftClient

/**
 * A class extending the [AbstractContext] in the [MinecraftClient].
 * This is considered the "unsafe" variant of the contexts as the properties can be null.
 * Methods in this context will always need to perform null checks for type safety.
 *
 * @property world The world in which the player is currently located, or `null` if the world is not available.
 * @property player The player entity, or `null` if the player is not available.
 * @property interaction The interaction manager for the player, or `null` if the interaction manager is not available.
 * @property connection The network handler for the player, or `null` if the network handler is not available.
 *
 * @function toSafe Converts the `ClientContext` to a `SafeContext` if all properties are not `null`, or returns `null` otherwise.
 */
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