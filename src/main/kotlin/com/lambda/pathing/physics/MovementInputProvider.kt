package com.lambda.pathing.physics

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.MovementUtils.moveYaw
import net.minecraft.client.network.ClientPlayerEntity

fun interface MovementInputProvider {
	fun nextInput(simulator: MovementSimulator): MovementSimulationInput

	companion object {
		fun live(player: ClientPlayerEntity) = MovementInputProvider { _ ->
			MovementSimulationInput.from(
				input = player.input,
				rotation = Rotation(player.moveYaw, player.pitch),
				useItemSlowdown = player.isUsingItem,
			).copy(sprint = player.isSprinting)
		}

		fun none() = MovementInputProvider { _ -> MovementSimulationInput() }
	}
}
