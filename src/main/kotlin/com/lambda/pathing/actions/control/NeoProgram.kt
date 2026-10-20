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
	private val turning = context.constraints.turning
	private val hand = context.constraints.hand
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
			maxTurn = hand?.speed ?: context.constraints.maxYawDegreesPerFrame,
			turnAcceleration = hand?.acceleration ?: Double.POSITIVE_INFINITY,
			turnStep = context.constraints.look?.takeIf { hand != null }?.degreesPerCount ?: 0.0,
		)
	}
	private var launched = false

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		val position = observed.position
		val velocity = observed.velocity
		val dx = position.x - origin.x - 0.5
		val dz = position.z - origin.z - 0.5
		val command = pilot.command(
			forward = dx * decision.forwardX + dz * decision.forwardZ + 0.5,
			side = dx * decision.sideX + dz * decision.sideZ + 0.5,
			up = position.y - origin.y,
			forwardSpeed = velocity.x * decision.forwardX + velocity.z * decision.forwardZ,
			sideSpeed = velocity.x * decision.sideX + velocity.z * decision.sideZ,
			upSpeed = velocity.y,
			heading = headingOf(observed.rotation.yaw),
			turnRate = Rotation.wrap(headingOf(observed.rotation.yaw) - headingOf(observed.rotation.yaw - observed.turn.yaw)),
			grounded = observed.onGround,
			sprinting = !observed.horizontalCollision || observed.collidedSoftly,
			launched = launched,
		)
		if (command.jump) launched = true
		return MovementSimulationInput(forward = 1.0, sprint = true, jump = command.jump, rotation = turning.yawTo(observed, yawOf(command.heading)))
	}

	private fun headingOf(yaw: Double): Double {
		val radians = Math.toRadians(yaw)
		val facingX = -sin(radians)
		val facingZ = cos(radians)
		return Math.toDegrees(atan2(facingX * decision.sideX + facingZ * decision.sideZ, facingX * decision.forwardX + facingZ * decision.forwardZ))
	}

	private fun yawOf(heading: Double): Double {
		val radians = Math.toRadians(heading)
		val x = cos(radians) * decision.forwardX + sin(radians) * decision.sideX
		val z = cos(radians) * decision.forwardZ + sin(radians) * decision.sideZ
		return Math.toDegrees(atan2(-x, z))
	}
}
