package com.lambda.pathing.search

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

/**
 * One decision, executed and certified: the source a tape was compiled from. Segments are
 * re-run by re-executing the decision (a closed-loop controller), never by replaying the
 * inputs; [Move.points] pins the guide chain the program was built from so a re-run
 * against an expanded field builds the same program. See docs/decisions/improver.md.
 */
sealed interface PlanSegment {
    val entry: MovementSimulationState
    val exit: MovementSimulationState
    val inputs: List<MovementSimulationInput>

    /** Index of this segment's first frame in the plan's tape. */
    val startFrame: Int

    val frameCount: Int get() = inputs.size

    /** Index one past this segment's last frame. */
    val endFrame: Int get() = startFrame + inputs.size

    /**
     * Whether a rejoining branch may re-enter the plan here: grounded and still moving.
     * Being airborne is the exclusion, not speed.
     */
    fun settledExit(stoppedSpeed: Double): Boolean =
        exit.onGround && exit.velocity.horizontalLength() > stoppedSpeed

    /** The same segment, renumbered after a splice moved it along the tape. */
    fun at(startFrame: Int): PlanSegment

    /** A movement the search chose, re-runnable from any entry state. */
    class Move(
        val decision: TrajectoryDecision,
        val points: List<HorizontalPoint>,
        override val entry: MovementSimulationState,
        override val exit: MovementSimulationState,
        override val inputs: List<MovementSimulationInput>,
        override val startFrame: Int,
    ) : PlanSegment {
        override fun at(startFrame: Int) =
            Move(decision, points, entry, exit, inputs, startFrame)
    }

    /**
     * The braking run onto the goal, or a brake tail published as a safe stop. Re-derived
     * by `FinishPlanner` from its new entry rather than re-run; [approach] is the one that
     * won last time.
     */
    class Terminal(
        val approach: TerminalApproach,
        override val entry: MovementSimulationState,
        override val exit: MovementSimulationState,
        override val inputs: List<MovementSimulationInput>,
        override val startFrame: Int,
    ) : PlanSegment {
        override fun at(startFrame: Int) =
            Terminal(approach, entry, exit, inputs, startFrame)
    }
}
