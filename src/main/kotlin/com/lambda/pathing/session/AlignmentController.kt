package com.lambda.pathing.session

import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.player.MovementUtils.moveYaw
import kotlin.math.abs

/**
 * Brings the body to the state a tape may launch from: settled to rest before capture,
 * and facing the tape's frame-0 yaw before replay. See docs/decisions/yaw-epsilon.md and
 * the alignment-timeout record in docs/decisions/execution-tolerance.md.
 */
internal class AlignmentController(private val walk: PathingSession) {

	/** Settling: hold still until the body is at rest on the ground, then capture and plan. */
	fun SafeContext.settle() {
		walk.tickInput = ALIGNMENT_INPUT
		if (player.velocity.horizontalLength() <= SETTLED_SPEED && player.isOnGround) {
			return with(walk.launcher) { capturePlan() }
		}
		if (++walk.settleTicks > MAX_SETTLE_TICKS) {
			with(walk) {
				fail(
					"could not settle to a stable start (%.3f b/t after %d ticks)".format(
						player.velocity.horizontalLength(), settleTicks,
					)
				)
			}
		}
	}

	/** While planning, keep the movement yaw the worker captured so the tape launches from it. */
	fun holdPlanningYaw() {
		val yaw = walk.planningYaw ?: return
		walk.request.runSafeAutomated { rotationRequest { yaw(yaw) }.submit() }
	}

	/** Aligning: turn to the pending tape's launch yaw, then install it. */
	fun SafeContext.align() {
		val path = walk.pendingPath ?: return with(walk) { fail("lost the certified plan while aligning") }
		val positionDrift = player.pos.distanceTo(path.plan.initialState.position)
		if (positionDrift > START_DRIFT_TOLERANCE) {
			return with(walk) { fail("moved %.2f blocks while aligning to the plan".format(positionDrift)) }
		}

		val targetYaw = path.plan.initialState.rotation.yaw
		walk.request.runSafeAutomated { rotationRequest { yaw(targetYaw) }.submit() }
		val yawDrift = abs(Rotation.wrap(player.moveYaw - targetYaw))

		val settled = player.velocity.horizontalLength() <= SETTLED_SPEED && player.isOnGround
		if (yawDrift <= FIRST_FRAME_YAW_EPSILON && settled) {
			with(walk.admission) { install(path) }
			return
		}

		walk.state = State.Aligning(walk.leg + 1, yawDrift)
		if (++walk.alignmentTicks > MAX_ALIGNMENT_TICKS) {
			// Install regardless rather than fail; see docs/decisions/execution-tolerance.md.
			warn(
				"Installing with %.2f° of launch yaw error; the rotation could not settle exactly."
					.format(yawDrift),
				PATHING_SOURCE,
			)
			with(walk.admission) { install(path) }
		}
	}

	/**
	 * The frame-0 re-alignment of an installed tape: true when this tick was spent turning
	 * toward the launch yaw and the frame must not execute yet.
	 */
	fun SafeContext.realignBeforeLaunch(current: PublishedPath): Boolean {
		val targetYaw = current.plan.initialState.rotation.yaw
		val yawError = abs(Rotation.wrap(player.moveYaw - targetYaw))
		if (yawError > FIRST_FRAME_YAW_EPSILON && walk.alignmentTicks <= MAX_ALIGNMENT_TICKS) {
			walk.request.runSafeAutomated { rotationRequest { yaw(targetYaw) }.submit() }
			walk.tickInput = ALIGNMENT_INPUT
			if (++walk.alignmentTicks > MAX_ALIGNMENT_TICKS) {
				warn(
					"Replaying with %.2f° of launch yaw error; the rotation could not settle exactly."
						.format(yawError),
					PATHING_SOURCE,
				)
			}
			return true
		}
		return false
	}

	companion object {
		const val START_DRIFT_TOLERANCE = 0.35

		/** Yaw agreement required before frame 0 may execute, in degrees. See docs/decisions/yaw-epsilon.md. */
		const val FIRST_FRAME_YAW_EPSILON = 2.5e-4

		const val MAX_ALIGNMENT_TICKS = 40

		const val SETTLED_SPEED = 1e-6

		const val MAX_SETTLE_TICKS = 40
	}
}
