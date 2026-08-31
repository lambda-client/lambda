package com.lambda.pathing.trajectory

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState

/**
 * One decision, executed and certified: the source a tape was compiled from.
 *
 * A [TrajectoryPlan] used to be a recording -- a flat input tape plus the frames it
 * produced -- and a recording can only be replayed from exactly the state it was recorded
 * at. Measured against this corpus, replaying a certified suffix from a body displaced by
 * a quarter of a beam bucket certified 1.9% of the time, and from three centimetres out,
 * 74%. Open-loop inputs do not survive a different body, because a launch amplifies state
 * error into a missed pad while walking contracts it.
 *
 * The decisions that generated those frames do survive, because each one is a closed-loop
 * controller that re-solves rather than replays: a [TrajectoryDecision.Launch] re-run
 * three centimetres left re-solves its launch window. So the plan keeps both -- segments
 * as the source, the tape as the compiled artifact the executor replays unchanged.
 *
 * [points] is the reason a decision alone is not enough. `AnchorRollout.prepare` builds a
 * movement's program from the coarse guide chain, not from the decision, so a decision
 * re-run against a value field that has since expanded would silently build a different
 * program. Storing the resolved chain makes a segment reproducible on its own terms.
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
     * Whether a rejoining branch may re-enter the plan here.
     *
     * Grounded and still moving. Not "stopped" -- the body never slows down for this.
     * The exclusion is being airborne: re-entering a ballistic arc solved for a different
     * launch is meaningless, while re-running a launch decision from a different ground
     * state is exactly what the launch solver is for.
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
     * The braking run onto the goal, or a brake tail published as a safe stop.
     *
     * Not re-run like a [Move]: the terminal is re-derived by asking `FinishPlanner` to
     * finish from whatever anchor it now starts at, which searches its own approach grid.
     * The recorded [approach] is the one that won last time, which is worth trying first.
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
