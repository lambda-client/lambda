package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.families.NeoMovement
import com.lambda.pathing.core.planarLength
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal class NeoProgram(private val decision: TrajectoryDecision.Neo, context: ProgramContext) : ControlProgram {
	private val maxYawChange = context.constraints.maxYawDegreesPerFrame
	private val centerX = context.body.stance.x + 0.5
	private val centerZ = context.body.stance.z + 0.5
	private var previous: Vec3d? = null
	private var positionedFrame = -1
	private var airborneTicks = 0

	override fun input(frame: Int, observed: MovementSimulationState): MovementSimulationInput {
		if (positionedFrame < 0) {
			val last = previous
			previous = observed.position
			if (last == null || planarLength(observed.position.x - last.x, observed.position.z - last.z) >= STILL_BLOCKS) {
				val forward = (observed.position.x - centerX) * decision.forwardX + (observed.position.z - centerZ) * decision.forwardZ + 0.5
				val side = (observed.position.x - centerX) * decision.sideX + (observed.position.z - centerZ) * decision.sideZ + 0.5
				val (aimForward, aimSide) = when {
					!decision.diagonal -> STRAIGHT_CORNER_FORWARD to STRAIGHT_CORNER_SIDE
					forward < DIAGONAL_CORNER_FORWARD - BODY_WIDTH -> DIAGONAL_CORNER_FORWARD to minOf(side, DIAGONAL_CORNER_SIDE - DIAGONAL_SLIDE_MARGIN)
					else -> DIAGONAL_CORNER_FORWARD + DIAGONAL_PRESS to DIAGONAL_CORNER_SIDE + DIAGONAL_PRESS
				}
				val aimX = centerX + decision.forwardX * (aimForward - 0.5) + decision.sideX * (aimSide - 0.5)
				val aimZ = centerZ + decision.forwardZ * (aimForward - 0.5) + decision.sideZ * (aimSide - 0.5)
				val yaw = Math.toDegrees(atan2(-(aimX - observed.position.x), aimZ - observed.position.z))
				return MovementSimulationInput(forward = 1.0, sneak = true, rotation = turned(observed, yaw))
			}
			positionedFrame = frame
		}
		val shuffle = if (decision.diagonal) NeoMovement.DIAGONAL_SHUFFLE_TICKS else 0
		if (frame - positionedFrame < shuffle) {
			return MovementSimulationInput(forward = -1.0, sneak = true, rotation = turned(observed, yawOf(SHUFFLE_FACING)))
		}
		val tick = frame - positionedFrame - shuffle
		val steer = decision.steer
		if (tick > TAKEOFF_TICKS && !observed.onGround) airborneTicks++
		val heading = when {
			tick < TAKEOFF_TICKS -> steer.takeoffHeading
			airborneTicks < steer.launchTicks -> steer.launchHeading
			else -> steer.landingHeading
		}
		return MovementSimulationInput(forward = 1.0, sprint = true, jump = tick == TAKEOFF_TICKS, rotation = turned(observed, yawOf(heading)))
	}

	private fun yawOf(heading: Int): Double {
		val radians = Math.toRadians(heading.toDouble())
		val x = cos(radians) * decision.forwardX - sin(radians) * decision.sideX
		val z = cos(radians) * decision.forwardZ - sin(radians) * decision.sideZ
		return Math.toDegrees(atan2(-x, z))
	}

	private fun turned(observed: MovementSimulationState, yaw: Double): Rotation {
		val delta = Rotation.wrap(yaw - observed.rotation.yaw).coerceIn(-maxYawChange, maxYawChange)
		return Rotation(observed.rotation.yaw + delta, observed.rotation.pitch)
	}

	private companion object {
		const val STRAIGHT_CORNER_FORWARD = 0.7

		const val STRAIGHT_CORNER_SIDE = 1.3

		const val DIAGONAL_CORNER_FORWARD = 1.3

		const val DIAGONAL_CORNER_SIDE = 0.7

		const val BODY_WIDTH = 0.6

		const val DIAGONAL_SLIDE_MARGIN = 0.05

		const val DIAGONAL_PRESS = 0.3

		const val SHUFFLE_FACING = -90

		const val STILL_BLOCKS = 1.0E-4

		const val TAKEOFF_TICKS = 1
	}
}
