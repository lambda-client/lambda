/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.context

import com.lambda.module.modules.client.TaskFlowModule
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld

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
class ClientContext(val configured: Configured = TaskFlowModule) {
    val mc: MinecraftClient = MinecraftClient.getInstance()
    val world: ClientWorld? = mc.world
    val player: ClientPlayerEntity? = mc.player
    val interaction: ClientPlayerInteractionManager? = mc.interactionManager
    val connection: ClientPlayNetworkHandler? = mc.networkHandler

    fun toSafe(): SafeContext? {
        if (world == null || player == null || interaction == null || connection == null)
            return null
        return object : SafeContext, Configured by configured {
            override val mc = this@ClientContext.mc
            override val world: ClientWorld = this@ClientContext.world
            override val player: ClientPlayerEntity = this@ClientContext.player
            override val interaction: ClientPlayerInteractionManager = this@ClientContext.interaction
            override val connection: ClientPlayNetworkHandler = this@ClientContext.connection
        }
    }
}
