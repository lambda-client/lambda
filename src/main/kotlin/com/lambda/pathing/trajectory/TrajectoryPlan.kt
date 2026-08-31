package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.InputTape
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import java.util.Collections

@JvmInline
value class TrajectoryPlanId(val value: Long)

enum class CertifiedTerminal {
    STABLE_GROUNDED_STOP,
}

class TrajectoryPlan private constructor(
    val id: TrajectoryPlanId,
    val snapshotRevision: Long,
    val coarseRouteVersion: Long,
    val physicsProfile: PlayerPhysicsProfile,
    val initialState: MovementSimulationState,
    val dependencies: Set<VoxelPos>,
    frameDependencies: List<Set<VoxelPos>>,
    val tape: InputTape,
    frames: List<SimulatedTrajectoryFrame>,
    val terminal: CertifiedTerminal,
    /**
     * The decisions the [tape] was compiled from, in execution order.
     *
     * Empty for plans built before segments existed or from a source that has none; any
     * consumer must treat that as "cannot recompile" rather than "no segments ran".
     */
    val segments: List<PlanSegment> = emptyList(),
) {
    val frames: List<SimulatedTrajectoryFrame> = Collections.unmodifiableList(ArrayList(frames))
    private val lastSectionReadFrame: Map<PathingSection, Int> = buildMap {
        frameDependencies.forEachIndexed { frame, reads ->
            reads.forEach { put(PathingSection.containing(it), frame) }
        }
    }
    private val lastChunkReadFrame: Map<PathingChunk, Int> = buildMap {
        frameDependencies.forEachIndexed { frame, reads ->
            reads.forEach { put(PathingChunk.containing(it), frame) }
        }
    }
    val certifiedThrough: Int get() = frames.lastIndex

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
        // A terminal is either a plain grounded stop (solid ground) or a closed period-2
        // rest cycle ending grounded (bouncy blocks: a standing micro-bounce alternates
        // grounded and airborne frames with frozen position).
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
        val terminalFrames = frames.takeLast(REQUIRED_STABLE_STOP_FRAMES)
        require(slow && (grounded || cycleClosed)) {
            "Published trajectory does not end in a stable grounded stop: " +
                terminalFrames.joinToString(" | ") { frame ->
                    "f=${frame.index} ground=${frame.state.onGround} " +
                        "speed=%.4f vy=%.4f pos=%s".format(
                            frame.state.velocity.horizontalLength(),
                            frame.state.velocity.y,
                            frame.state.position,
                        )
                }
        }
    }

    // First frame of the stationary terminal suffix: from here on every input is
    // passive and the body no longer moves. Executing these frames is physically
    // inert, so execution may complete once the cursor reaches this index.
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

    fun dependencySectionsFrom(nextFrame: Int): Set<PathingSection> {
        require(nextFrame in 0..tape.frameCount) { "Frame is outside the published tape" }
        return lastSectionReadFrame.filterValues { it >= nextFrame }.keys
    }

    fun dependencyChunksFrom(nextFrame: Int): Set<PathingChunk> {
        require(nextFrame in 0..tape.frameCount) { "Frame is outside the published tape" }
        return lastChunkReadFrame.filterValues { it >= nextFrame }.keys
    }

    companion object {
        private const val REQUIRED_STABLE_STOP_FRAMES = 3
        private const val TERMINAL_STOP_SPEED = MotionConstraints.DEFAULT_STOPPED_SPEED + 1.0E-9

        fun fromWalkingSeed(
            id: TrajectoryPlanId,
            seed: MotionPlanResult.Success,
            physicsProfile: PlayerPhysicsProfile,
        ): TrajectoryPlan = TrajectoryPlan(
            id = id,
            snapshotRevision = seed.sourceRoute.snapshotRevision,
            coarseRouteVersion = seed.sourceRoute.routeVersion,
            physicsProfile = physicsProfile,
            initialState = seed.rollout.initialState,
            dependencies = Collections.unmodifiableSet(HashSet(seed.dependencies)),
            frameDependencies = seed.frameDependencies.map { Collections.unmodifiableSet(HashSet(it)) },
            tape = InputTape(seed.tape.asList()),
            frames = seed.rollout.frames,
            terminal = CertifiedTerminal.STABLE_GROUNDED_STOP,
            segments = seed.planSegments,
        )
    }
}
