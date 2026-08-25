package com.lambda.pathing.trajectory

import com.lambda.pathing.movement.InputTape
import com.lambda.pathing.world.PathingChunk
import com.lambda.pathing.world.PathingSection
import com.lambda.pathing.world.VoxelPos
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
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
        val terminalFrames = frames.takeLast(REQUIRED_STABLE_STOP_FRAMES)
        require(terminalFrames.size == REQUIRED_STABLE_STOP_FRAMES && terminalFrames.all { frame ->
            frame.state.onGround && frame.state.velocity.horizontalLength() <= TERMINAL_STOP_SPEED
        }) {
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
        private const val TERMINAL_STOP_SPEED = 0.012 + 1.0E-9

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
        )
    }
}
