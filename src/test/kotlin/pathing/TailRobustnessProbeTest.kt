/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.TrajectoryPlanner.withinBudget
import com.lambda.pathing.coarse.*
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.trajectory.*
import com.lambda.util.player.prediction.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.time.Duration

/**
 * Can a certified tail survive being started from a slightly different state?
 *
 * This decides whether shortcut-style improvement is possible at all. Splicing a faster
 * middle into a tape cannot reproduce the old join state exactly — no input sequence hits
 * a specific continuous state — so the question is whether the *rest* of the plan still
 * works from nearby. Replaying the tail's raw inputs is open loop and should drift;
 * re-running the tail's controller is closed loop and should steer back.
 */
@Tag("bedrock-corpus")
class TailRobustnessProbeTest {
    @Test
    fun `how much state error can a tail absorb`() {
        // Flat open ground: the coarse route is walk-only, so the corridor controller is
        // a faithful stand-in for "re-run the tail's intent" and the two arms compare
        // like for like. Rugged terrain needs the real decision list, which is the
        // plumbing this measurement is meant to justify.
        val flat = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -40..40) for (z in -40..40) flat[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-44, 56, -44, 44, 84, 44), flat,
        )
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(maxJumpDrop = 2),
        )
        val config = WalkingSeedSearchConfig()
        val nudges = listOf(0.02, 0.05, 0.10, 0.25)
        val openLoop = HashMap<Double, IntArray>()
        val closedLoop = HashMap<Double, IntArray>()
        nudges.forEach { openLoop[it] = IntArray(2); closedLoop[it] = IntArray(2) }
        var originalTailFrames = 0
        var closedLoopFrames = 0

        val goals = listOf(
            Stance(0, 64, 30), Stance(30, 64, 0), Stance(22, 64, 22),
            Stance(-25, 64, 18), Stance(30, 64, 12), Stance(-18, 64, -26),
        )
        for ((index, goal) in goals.withIndex()) {
            val start = Stance(0, 64, 0)
            val planner = CoarsePlanner(environment.withinBudget(start, goal), moves, start, goal)
            if (!planner.repair(Duration.INFINITE).converged) continue
            planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
            val dx = (goal.x - start.x).toDouble()
            val dz = (goal.z - start.z).toDouble()
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
            )
            val outcome = TrajectoryPlanner.searchWithRerouting(planner, index.toLong()) { route ->
                ValueFieldAnchorSearch.search(
                    route, planner.valueField(), initial, PROFILE, environment, config,
                )
            }
            val success = outcome?.result as? WalkingSeedSearchResult.Success ?: continue
            val route = outcome.route
            val goalPoint = route.goal

            for (fraction in listOf(0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80)) {
                val split = (success.tape.frameCount * fraction).toInt()
                if (split < 4 || split > success.tape.frameCount - 12) continue
                val joint = success.rollout.frames[split - 1].state
                if (!joint.onGround) continue

                for (nudge in nudges) {
                    val perturbed = joint.copy(
                        position = joint.position.add(nudge, 0.0, nudge * 0.5),
                        boundingBox = joint.boundingBox.offset(nudge, 0.0, nudge * 0.5),
                    )
                    // Open loop: the raw remaining inputs, replayed from the new state.
                    val remaining = route.suffix(nearestNode(route, perturbed))
                    if (remaining.edges.any { it.kind == CoarseMoveKind.JUMP_CANDIDATE }) continue
                    val tail = success.tape.asList().drop(split)
                    val replay = TrajectoryRolloutEngine.rollout(
                        perturbed, PROFILE, environment, InputTape(tail), tail.size,
                    )
                    openLoop.getValue(nudge)[if (arrived(replay, goalPoint, config)) 0 else 1]++

                    // Closed loop: the same intent, re-derived by the controller that
                    // produced the tail in the first place. The launch frames recorded on
                    // the whole tape are absolute indices and mean nothing to a suffix, so
                    // they are dropped and only jump-free tails are compared — otherwise
                    // this arm measures mis-timed jumps rather than the property in
                    // question.
                    val suffix = route.suffix(nearestNode(route, perturbed))
                    if (suffix.edges.any { it.kind == CoarseMoveKind.JUMP_CANDIDATE }) continue
                    val program = CorridorFollowerProgram(
                        suffix.nodes, success.parameters.copy(gapLaunchFrames = emptyList()), config,
                    )
                    val rerun = TrajectoryRolloutEngine.rollout(
                        perturbed, PROFILE, environment, program, config.maxFrames,
                    )
                    val survived = arrived(rerun, goalPoint, config)
                    closedLoop.getValue(nudge)[if (survived) 0 else 1]++
                    if (survived && nudge == 0.10) {
                        originalTailFrames += success.tape.frameCount - split
                        closedLoopFrames += rerun.frames.indexOfFirst {
                            it.state.onGround && it.state.velocity.horizontalLength() <= config.stoppedSpeed &&
                                hypot(it.state.position.x - (goalPoint.x + 0.5),
                                    it.state.position.z - (goalPoint.z + 0.5)) <= config.goalRadius
                        } + 1
                    }
                }
            }
        }

        println("[tail] original tail frames %d vs closed-loop re-run %d (nudge 0.10)"
            .format(originalTailFrames, closedLoopFrames))
        println("[tail] nudge  open-loop replay      closed-loop re-run")
        nudges.forEach { nudge ->
            val open = openLoop.getValue(nudge)
            val closed = closedLoop.getValue(nudge)
            println(
                "[tail] %.2f   %3d/%3d survived      %3d/%3d survived"
                    .format(nudge, open[0], open[0] + open[1], closed[0], closed[0] + closed[1])
            )
        }
    }

    private fun arrived(
        rollout: TrajectoryRollout,
        goal: Stance,
        config: WalkingSeedSearchConfig,
    ): Boolean {
        if (rollout.termination !is TrajectoryRolloutTermination.Completed) return false
        return rollout.frames.any {
            it.state.onGround &&
                it.state.velocity.horizontalLength() <= config.stoppedSpeed &&
                hypot(it.state.position.x - (goal.x + 0.5), it.state.position.z - (goal.z + 0.5)) <=
                config.goalRadius &&
                kotlin.math.abs(it.state.position.y - goal.y) <= 0.05
        }
    }

    private fun nearestNode(route: CoarseRoutePlan, state: MovementSimulationState): Int {
        var best = 0
        var bestDistance = Double.MAX_VALUE
        route.nodes.forEachIndexed { index, node ->
            val d = hypot(node.x + 0.5 - state.position.x, node.z + 0.5 - state.position.z) +
                kotlin.math.abs(node.y - state.position.y)
            if (d < bestDistance) { bestDistance = d; best = index }
        }
        return best
    }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
