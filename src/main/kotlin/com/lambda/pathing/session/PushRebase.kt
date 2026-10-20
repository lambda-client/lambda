package com.lambda.pathing.session

import com.lambda.context.SafeContext
import com.lambda.pathing.actions.InputTape
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.rollout.TrajectoryRolloutEngine
import com.lambda.pathing.search.PlanSegment
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.math.abs
import kotlin.math.hypot

internal fun SafeContext.pushedByEntity(): Boolean =
	world.getOtherEntities(player, player.boundingBox.expand(PUSH_CONTACT_MARGIN)) {
		it.isAlive && it.isPushable && !it.isSpectator && !it.isConnectedThroughVehicle(player)
	}.isNotEmpty()

internal fun rebased(path: PublishedPath, from: Int, observed: MovementSimulationState, terrain: SnapshotSimulationEnvironment, revision: Long): PublishedPath? {
	val plan = path.plan
	if (from >= plan.tape.frameCount) return null
	val start = observed.copy(isJumping = false)
	val inputs = plan.tape.asList().subList(from, plan.tape.frameCount)
	val tracked = terrain.trackingView()
	val dependencies = ArrayList<Set<VoxelPos>>(inputs.size)
	val rollout = TrajectoryRolloutEngine.rollout(
		initialState = start,
		profile = plan.physicsProfile,
		environment = tracked,
		program = InputTape(inputs),
		frameCount = inputs.size,
		observer = { _ ->
			dependencies += tracked.takeFrameDependencies()
			false
		},
	)
	if (!rollout.completed || rollout.frames.size != inputs.size) return null
	for (index in rollout.frames.indices) {
		val actual = rollout.frames[index].state.position
		val planned = plan.frames[from + index].state.position
		if (hypot(actual.x - planned.x, actual.z - planned.z) > MAX_PUSH_DRIFT_BLOCKS || abs(actual.y - planned.y) > MAX_PUSH_RISE_BLOCKS) return null
	}
	val bridge = runCatching {
		TrajectoryPlan(
			snapshotRevision = revision,
			physicsProfile = plan.physicsProfile,
			initialState = start,
			frameDependencies = dependencies,
			tape = InputTape(rollout.frames.map { it.input }),
			frames = rollout.frames,
			segments = listOf(PlanSegment.Terminal(start, rollout.frames.last().state, rollout.frames.map { it.input }, startFrame = 0)),
		)
	}.getOrNull() ?: return null
	return path.copy(
		plan = bridge,
		partial = true,
		safeAnchorFrame = maxOf(0, path.safeAnchorFrame - from),
		legTouches = path.legTouches.mapNotNull { touch -> touch.copy(frame = touch.frame - from).takeIf { it.frame >= 0 } },
	)
}

private const val PUSH_CONTACT_MARGIN = 0.1

private const val MAX_PUSH_DRIFT_BLOCKS = 0.5

private const val MAX_PUSH_RISE_BLOCKS = 0.25
