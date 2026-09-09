package com.lambda.pathing.physics

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.util.player.MovementUtils.forward
import com.lambda.util.player.MovementUtils.jumping
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.MovementUtils.sprinting
import com.lambda.util.player.MovementUtils.strafe
import net.minecraft.client.input.Input

data class MovementSimulationInput(
	val forward: Double = 0.0,
	val strafe: Double = 0.0,
	val jump: Boolean = false,
	val sneak: Boolean = false,
	val sprint: Boolean = false,
	val useItemSlowdown: Boolean = false,
	val rotation: Rotation? = null,

	val interaction: Interaction? = null,
) {
	companion object {
		fun from(
			input: Input,
			rotation: Rotation? = null,
			useItemSlowdown: Boolean = false,
		) = MovementSimulationInput(
			forward = input.forward.toDouble(),
			strafe = input.strafe.toDouble(),
			jump = input.jumping,
			sneak = input.sneaking,
			sprint = input.sprinting,
			useItemSlowdown = useItemSlowdown,
			rotation = rotation,
		)
	}
}
