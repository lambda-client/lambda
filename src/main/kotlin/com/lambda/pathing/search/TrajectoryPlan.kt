package com.lambda.pathing.search

import com.lambda.pathing.actions.InputTape
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.SimulationEnvironment
import com.lambda.pathing.rollout.FrameEdit
import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.TrajectoryRolloutEngine
import com.lambda.pathing.rollout.TrajectoryRolloutTermination
import com.lambda.pathing.world.streaming.WorldMutation
import java.util.*

class TrajectoryPlan internal constructor(
	val snapshotRevision: Long,
	val physicsProfile: PlayerPhysicsProfile,
	val initialState: MovementSimulationState,
	internal val frameDependencies: List<Set<VoxelPos>>,
	val tape: InputTape,
	frames: List<SimulatedTrajectoryFrame>,
	val segments: List<PlanSegment>,
	val edits: List<FrameEdit> = emptyList(),
) {
	val frames: List<SimulatedTrajectoryFrame> = Collections.unmodifiableList(ArrayList(frames))
	val usesBlocks: Boolean by lazy { tape.asList().any { it.use != null } }

	private val cellReads by lazy { FrameDependencyIndex(frameDependencies) { it } }
	private val chunkReads by lazy { FrameDependencyIndex(frameDependencies, PathingChunk::containing) }

	init {
		require(frames.size == tape.frameCount) { "Every published input must have one expected frame" }
		require(frameDependencies.size == tape.frameCount) {
			"Every published input must have one world-dependency set"
		}
		require(frames.indices.all { frames[it].index == it }) { "Published frames must be contiguous from zero" }
		require(frames.isNotEmpty()) { "A trajectory plan must certify at least one frame" }
		frames.forEach { frame ->
			val state = frame.state
			require(state.position.x.isFinite() && state.position.y.isFinite() && state.position.z.isFinite()) {
				"Published trajectory contains a non-finite position at frame ${frame.index}"
			}
			require(state.velocity.x.isFinite() && state.velocity.y.isFinite() && state.velocity.z.isFinite()) {
				"Published trajectory contains a non-finite velocity at frame ${frame.index}"
			}
		}

		val stopFrames = frames.takeLast(REQUIRED_STABLE_STOP_FRAMES)
		val slow = stopFrames.size == REQUIRED_STABLE_STOP_FRAMES && stopFrames.all { frame ->
			frame.state.velocity.horizontalLength() <= TERMINAL_STOP_SPEED
		}
		val grounded = stopFrames.all { it.state.onGround }
		val cycleFrames = frames.takeLast(REQUIRED_STABLE_STOP_FRAMES + 1)
		val cycleClosed = cycleFrames.size == REQUIRED_STABLE_STOP_FRAMES + 1 &&
				frames.last().state.onGround &&
				(0 until cycleFrames.size - 2).all { i ->
					val a = cycleFrames[i].state
					val b = cycleFrames[i + 2].state
					a.position == b.position && a.velocity == b.velocity && a.onGround == b.onGround
				}
		val afloat = frames.last().state.touchingWater
		require(slow && (grounded || cycleClosed || afloat)) {
			"Published trajectory does not end in a stable grounded or floating stop: " +
					stopFrames.joinToString(" | ") { frame ->
						"f=${frame.index} ground=${frame.state.onGround} " +
								"speed=%.4f vy=%.4f pos=%s".format(
									frame.state.velocity.horizontalLength(),
									frame.state.velocity.y,
									frame.state.position,
								)
					}
		}
	}

	val stationaryFrom: Int by lazy {
		val terminal = frames.last().state.position
		var first = frames.size
		while (first > 1) {
			val input = tape[first - 1]
			val passive = input.forward == 0.0 && input.strafe == 0.0 && !input.jump && !input.sneak
			if (!passive || frames[first - 1].state.position != terminal) break
			first--
		}
		first
	}

	fun dependencyChunksBetween(nextFrame: Int, untilFrame: Int): Set<PathingChunk> {
		require(nextFrame in 0..tape.frameCount) { "Frame is outside the published tape" }
		return chunkReads.between(nextFrame, untilFrame)
	}

	fun reads(mutation: WorldMutation, nextFrame: Int, untilFrame: Int): Boolean = when (mutation) {
		is WorldMutation.Cell -> cellReads.reads(mutation.cell, nextFrame, untilFrame)
		is WorldMutation.Chunk -> chunkReads.reads(mutation.chunk, nextFrame, untilFrame)
	}

	fun firstFrameReading(cells: Set<VoxelPos>, chunks: Set<PathingChunk>): Int? =
		(cells.mapNotNull(cellReads::first) + chunks.mapNotNull(chunkReads::first)).minOrNull()

	fun readsAny(cells: Collection<VoxelPos>, nextFrame: Int, untilFrame: Int): Boolean = cells.any { cellReads.reads(it, nextFrame, untilFrame) }

	fun firstFrameReading(mutation: WorldMutation): Int? = when (mutation) {
		is WorldMutation.Cell -> cellReads.first(mutation.cell)
		is WorldMutation.Chunk -> chunkReads.first(mutation.chunk)
	}

	companion object {
		private const val REQUIRED_STABLE_STOP_FRAMES = 3
		private const val TERMINAL_STOP_SPEED = MotionConstraints.STOPPED_SPEED + 1.0E-9
	}
}

internal fun TrajectoryPlan.firstRejectedFrame(environment: SimulationEnvironment, nextFrame: Int): Int? {
	if (nextFrame >= frames.size) return null
	val start = if (nextFrame == 0) initialState else frames[nextFrame - 1].state
	val expected = frames.subList(nextFrame, frames.size)
	val replayed = TrajectoryRolloutEngine.rollout(start, physicsProfile, environment, InputTape(expected.map { it.input }), expected.size)
	return (replayed.termination as? TrajectoryRolloutTermination.Rejected)?.let { nextFrame + it.frame }
}

internal fun TrajectoryPlan.replaysUnchanged(environment: SimulationEnvironment, nextFrame: Int, untilFrame: Int): Boolean {
	val until = minOf(untilFrame, frames.size)
	if (nextFrame >= until) return true
	val start = if (nextFrame == 0) initialState else frames[nextFrame - 1].state
	val expected = frames.subList(nextFrame, until)
	val replayed = TrajectoryRolloutEngine.rollout(start, physicsProfile, environment, InputTape(expected.map { it.input }), expected.size)
	return !(!replayed.completed || replayed.frames.size != expected.size) && replayed.frames.indices.all { index ->
		val actual = replayed.frames[index].state
		val planned = expected[index].state
		actual.position == planned.position && actual.velocity == planned.velocity && actual.onGround == planned.onGround
	}
}
