
package com.minato.context

import com.minato.Minato
import com.minato.Minato.mc
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld

/**
 * A class extending the [AbstractContext] in the [MinecraftClient].
 * This is considered the "safe" variant of the contexts as the properties are non-`null`.
 * Methods in this context will not need to perform `null` checks for type safety.
 *
 * The [SafeContext] is used as a receiver in extension functions to bring the type-safe properties into scope.
 * This allows methods to operate on the [MinecraftClient] without the need for type checks on the properties.
 *
 * Example usage:
 * ```kotlin
 * fun SafeContext.exampleFunction() {
 *     // Here, we can directly access the properties without null checks
 *     val playerName = player.name.asString()
 *     val worldName = world.registryKey.value.path
 *     // ...
 * }
 * ```
 *
 * @property world The world in which the player is currently located.
 * @property player The player entity.
 * @property interaction The interaction manager for the player.
 * @property connection The network handler for the player.
 **/
interface SafeContext {
	val mc: MinecraftClient
	val world: ClientWorld
	val player: ClientPlayerEntity
	val interaction: ClientPlayerInteractionManager
	val connection: ClientPlayNetworkHandler

	companion object {
		fun create(): SafeContext? {
			val world = mc.world ?: return null
			val player = mc.player ?: return null
			val interaction = mc.interactionManager ?: return null
			val connection = mc.networkHandler ?: return null
			return object : SafeContext {
				override val mc = Minato.mc
				override val world = world
				override val player = player
				override val interaction = interaction
				override val connection = connection
			}
		}
	}
}
