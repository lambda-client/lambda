package com.lambda.pathing.search

import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.CorridorFollowerProgram
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.PursuitTracker
import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.world.center
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.math.hypot
import com.lambda.pathing.world.Medium

internal class FinishPlanner(
    private val field: ValueField,
    private val environment: SnapshotSimulationEnvironment,
    private val config: MotionConstraints,
    private val gate: RolloutGate,
    private val attempts: AttemptAccumulator,
    private val probe: SearchProbe,
    private val goalPoint: () -> HorizontalPoint,
    private val routeLastIndex: () -> Int,
    private val progressOf: (Stance) -> Int,
) {
    private var provenFinish: TerminalApproach? = null

    /**
     * A denied attempt stops the parameter search, retaining any already certified best
     * tail. The proven parameters are tried first and, unless [exhaustive], returned as soon
     * as they certify: the search finishes many anchors and pays for the memo once. The
     * improver finishes one body that arrived faster than the spine's, so it sweeps the
     * whole grid: the memoised brake was tuned for a slower arrival.
     */
    fun finishFrom(anchor: ValueAnchor, exhaustive: Boolean = false, canStartRollout: () -> Boolean = { true }): Solution? {
        val chain = field.chain(anchor.stance, null, FINISH_CHAIN_LENGTH)
        if (!field.reachesGoal(chain)) return null
        val points = chain.map { it.center(environment) }
        val leads: List<Double?> = if (chain.asSequence().zipWithNext().any { (from, to) -> to.y > from.y }) {
            config.stepUpJumpLeadDistances
        } else {
            listOf(null)
        }

        val grid = sequence {
            provenFinish?.let { yield(it) }
            for (sprint in config.sprintModes) {
                for (brake in config.brakeDistances) {
                    for (lead in leads) {
                        yield(TerminalApproach(sprint, PursuitTracker.DEFAULT_LOOK_AHEAD_NODES, brake, lead))
                    }
                }
            }
        }

        var bestRank: TrajectoryRank? = null
        var bestFrames: List<SimulatedTrajectoryFrame>? = null
        var bestParameters: TerminalApproach? = null
        for (parameters in grid) {
            if (!canStartRollout()) break
            val frames = terminalRun(anchor, chain, points, parameters) ?: continue
            val rank = TrajectoryRank(
                certifiedAndSafe = true,
                certifiedHorizon = routeLastIndex(),
                elapsedPlusTail = (anchor.elapsed + frames.size).toDouble(),
                collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames),
                launchMargin = anchor.launchMargin,
                inputSwitches = anchor.inputSwitches +
                    inputSwitches(anchor.inputs.lastOrNull(), frames),
            )
            if (!exhaustive && parameters == provenFinish) {
                return Solution.of(anchor, frames, parameters, rank.collisionEvents)
            }
            val incumbent = bestRank
            if (incumbent == null || rank < incumbent) {
                bestRank = rank
                bestFrames = frames
                bestParameters = parameters
            }
        }

        val rank = bestRank ?: return null
        val parameters = bestParameters!!
        provenFinish = parameters
        return Solution.of(anchor, bestFrames!!, parameters, rank.collisionEvents)
    }

    private fun terminalRun(
        anchor: ValueAnchor,
        chain: List<Stance>,
        points: List<HorizontalPoint>,
        parameters: TerminalApproach,
    ): List<SimulatedTrajectoryFrame>? {
        val gated = gate.run(
            anchor.state, points,
            CorridorFollowerProgram(chain, parameters, config),
            config.maxFrames,
        )
        val goal = goalPoint()
        val evaluation = evaluate(gated.rollout, points, goal, config, climbing = { p ->
            field.view.medium(
                kotlin.math.floor(p.x).toInt(), kotlin.math.floor(p.y).toInt(), kotlin.math.floor(p.z).toInt(),
            ) == Medium.CLIMBABLE
        })
        probe.attempt(gated.rollout, gated.stopFrame != null, evaluation.diagnostic)
        attempts.record(PlanAttempt(
            parameters = parameters,
            simulatedFrames = gated.rollout.frames.size,
            finalGoalError = hypot(
                gated.rollout.finalState.position.x - goal.x,
                gated.rollout.finalState.position.z - goal.z,
            ),
            finalHorizontalSpeed = gated.rollout.finalState.velocity.horizontalLength(),
            diagnostic = evaluation.diagnostic,
            blockedProgress = progressOf(anchor.stance),
        ))
        val stop = gated.stopFrame ?: return null
        if (gated.failed) return null
        return gated.rollout.frames.take(stop + 1)
    }

    private companion object {
        const val FINISH_CHAIN_LENGTH = 24
    }
}
