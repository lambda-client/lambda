package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal class NeoProgram(private val decision: TrajectoryDecision.Neo, context: ProgramContext) : ControlProgram {
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val origin = context.body.stance
	private val pilot = run {
		val dx = decision.step.x - origin.x
		val dz = decision.step.z - origin.z
		NeoPilot(
			decision.terrain,
			landingForward = dx * decision.forwardX + dz * decision.forwardZ,
			landingSide = dx * decision.sideX + dz * decision.sideZ,
			landingUp = decision.step.y - origin.y,
			groundHeading = decision.groundHeading,
			preferredMargin = decision.margin,
			maxTurn = maxYawChange,
		)
	}
	private var launched = false

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val position = observed.position
		val velocity = observed.velocity
		val dx = position.x - origin.x - 0.5
		val dz = position.z - origin.z - 0.5
		val yaw = Math.toRadians(observed.rotation.yaw)
		val facingX = -sin(yaw)
		val facingZ = cos(yaw)
		val command = pilot.command(
			forward = dx * decision.forwardX + dz * decision.forwardZ + 0.5,
			side = dx * decision.sideX + dz * decision.sideZ + 0.5,
			up = position.y - origin.y,
			forwardSpeed = velocity.x * decision.forwardX + velocity.z * decision.forwardZ,
			sideSpeed = velocity.x * decision.sideX + velocity.z * decision.sideZ,
			upSpeed = velocity.y,
			heading = Math.toDegrees(atan2(facingX * decision.sideX + facingZ * decision.sideZ, facingX * decision.forwardX + facingZ * decision.forwardZ)),
			grounded = observed.onGround,
			sprinting = !observed.horizontalCollision || observed.collidedSoftly,
			launched = launched,
		)
		if (command.jump) launched = true
		return MovementSimulationInput(forward = 1.0, sprint = true, jump = command.jump, rotation = turned(observed, yawOf(command.heading)))
	}

	private fun yawOf(heading: Double): Double {
		val radians = Math.toRadians(heading)
		val x = cos(radians) * decision.forwardX + sin(radians) * decision.sideX
		val z = cos(radians) * decision.forwardZ + sin(radians) * decision.sideZ
		return Math.toDegrees(atan2(-x, z))
	}

	private fun turned(observed: MovementSimulationState, yaw: Double): Rotation {
		val delta = Rotation.wrap(yaw - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
		return Rotation(observed.rotation.yaw + delta, observed.rotation.pitch)
	}
}
