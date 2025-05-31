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

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld

/**
 * Representing an abstract context in the [MinecraftClient].
 *
 * @property mc The Minecraft client instance.
 * @property world The world in which the player is currently located, or `null` if the world is not available.
 * @property player The player entity, or `null` if the player is not available.
 * @property interaction The interaction manager for the player, or `null` if the interaction manager is not available.
 * @property connection The network handler for the player, or `null` if the network handler is not available.
 */
abstract class AbstractContext {
    val mc: MinecraftClient = MinecraftClient.getInstance()
    abstract val world: ClientWorld?
    abstract val player: ClientPlayerEntity?
    abstract val interaction: ClientPlayerInteractionManager?
    abstract val connection: ClientPlayNetworkHandler?
}
