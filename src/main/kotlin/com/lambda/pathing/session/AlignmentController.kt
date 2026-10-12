package com.lambda.pathing.session

import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.PathingSession.Companion.ALIGNMENT_INPUT
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.player.MovementUtils.moveYaw
import kotlin.math.abs

internal class AlignmentController(private val walk: PathingSession) {
	var pendingPath: PublishedPath? = null
		private set

	var planningYaw: Double? = null

	private var settleTicks = 0
	private var alignmentTicks = 0

	fun beginSettling() {
		settleTicks = 0
	}

	fun beginAligning(path: PublishedPath) {
		pendingPath = path
		alignmentTicks = 0
	}

	fun installed() {
		planningYaw = null
		pendingPath = null
		alignmentTicks = 0
	}

	fun reset() {
		installed()
		settleTicks = 0
	}

	fun SafeContext.settle() {
		if (with(walk) { afloat() }) return with(walk.launcher) { capturePlan() }
		if (settled()) {
			return with(walk.launcher) { capturePlan() }
		}
		if (++settleTicks > MAX_SETTLE_TICKS) {
			with(walk) {
				fail(
					"could not settle to a stable start (%.3f b/t, ground %s, water %s after %d ticks)".format(
						player.velocity.horizontalLength(), player.isOnGround, player.isTouchingWater, settleTicks,
					)
				)
			}
		}
	}

	fun holdPlanningYaw() {
		walk.face(planningYaw ?: return)
	}

	fun SafeContext.align() {
		val path = pendingPath ?: return with(walk) { fail("lost the certified plan while aligning") }
		val positionDrift = startDrift(path)
		if (positionDrift > START_DRIFT_TOLERANCE) {
			return with(walk) { recoverOrFail("moved %.2f blocks while aligning to the plan".format(positionDrift)) }
		}

		walk.face(path.plan.initialState.rotation.yaw)
		val yawDrift = launchYawError(path)
		if (yawDrift <= FIRST_FRAME_YAW_EPSILON && settled()) {
			with(walk.admission) { install(path) }
			return
		}

		walk.state = State.Aligning(walk.leg + 1, yawDrift)
		if (++alignmentTicks > MAX_ALIGNMENT_TICKS) {
			warn(
				"Installing with %.2f° of launch yaw error; the rotation could not settle exactly."
					.format(yawDrift),
				PATHING_SOURCE,
			)
			with(walk.admission) { install(path) }
		}
	}

	fun SafeContext.realignBeforeLaunch(current: PublishedPath): Boolean {
		val yawError = launchYawError(current)
		if (yawError > FIRST_FRAME_YAW_EPSILON && alignmentTicks <= MAX_ALIGNMENT_TICKS) {
			walk.face(current.plan.initialState.rotation.yaw)
			walk.tickInput = ALIGNMENT_INPUT
			if (++alignmentTicks > MAX_ALIGNMENT_TICKS) {
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

	fun SafeContext.startDrift(path: PublishedPath): Double = player.pos.distanceTo(path.plan.initialState.position)

	fun SafeContext.launchYawError(path: PublishedPath): Double =
		abs(Rotation.wrap(player.moveYaw - path.plan.initialState.rotation.yaw))

	private fun SafeContext.settled(): Boolean = player.velocity.horizontalLength() <= SETTLED_SPEED && player.isOnGround

	companion object {
		const val START_DRIFT_TOLERANCE = 0.35

		const val FIRST_FRAME_YAW_EPSILON = 2.5e-4

		const val SETTLED_SPEED = 1e-6

		private const val MAX_ALIGNMENT_TICKS = 40

		private const val MAX_SETTLE_TICKS = 40
	}
}
