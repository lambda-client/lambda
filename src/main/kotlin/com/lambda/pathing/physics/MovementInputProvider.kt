package com.lambda.pathing.physics

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.MovementUtils.forward
import com.lambda.util.player.MovementUtils.jumping
import com.lambda.util.player.MovementUtils.moveYaw
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.MovementUtils.strafe
import net.minecraft.client.network.ClientPlayerEntity

fun interface MovementInputProvider {
	fun nextInput(simulator: MovementSimulator): MovementSimulationInput

	companion object {
		fun live(player: ClientPlayerEntity) = MovementInputProvider { _ ->
			MovementSimulationInput(
				forward = player.input.forward.toDouble(),
				strafe = player.input.strafe.toDouble(),
				jump = player.input.jumping,
				sneak = player.input.sneaking,
				sprint = player.isSprinting,
				useItemSlowdown = player.isUsingItem,
				rotation = Rotation(player.moveYaw, player.pitch),
			)
		}
	}
}
