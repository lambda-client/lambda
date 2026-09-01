package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.CorridorFollowerProgram
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.PursuitTracker
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.world.center
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import kotlin.math.hypot
import com.lambda.pathing.world.Medium

internal class FinishPlanner(
    private val field: CoarseValueField,
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

    fun finishFrom(anchor: ValueAnchor): Solution? {
        val chain = field.chain(anchor.stance, null, FINISH_CHAIN_LENGTH)
        if (!field.reachesGoal(chain)) {
            probe.finishAttempt(anchor.stance, anchor.elapsed, anchor.speed, chainReached = false, sealed = false)
            return null
        }
        val points = chain.map { it.center(environment) }
        val leads: List<Double?> = if (chain.zipWithNext().any { (from, to) -> to.y > from.y }) {
            config.stepUpJumpLeadDistances
        } else {
            listOf(null)
        }

        val grid = buildList {
            provenFinish?.let { add(it) }
            for (sprint in config.sprintModes) {
                for (brake in config.brakeDistances) {
                    for (lead in leads) {
                        add(TerminalApproach(sprint, PursuitTracker.DEFAULT_LOOK_AHEAD_NODES, brake, lead))
                    }
                }
            }
        }

        var bestRank: TrajectoryRank? = null
        var bestFrames: List<SimulatedTrajectoryFrame>? = null
        var bestParameters: TerminalApproach? = null
        for (parameters in grid) {
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
            if (parameters == provenFinish) {
                return Solution.of(anchor, frames, parameters, rank.collisionEvents)
            }
            val incumbent = bestRank
            if (incumbent == null || rank < incumbent) {
                bestRank = rank
                bestFrames = frames
                bestParameters = parameters
            }
        }

        val rank = bestRank
        probe.finishAttempt(
            anchor.stance, anchor.elapsed, anchor.speed,
            chainReached = true, sealed = rank != null,
        )
        if (rank == null) return null
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
