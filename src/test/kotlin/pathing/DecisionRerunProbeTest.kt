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
 * The measurement that decides whether shortcutting is affordable, on the terrain that
 * matters: rugged bedrock, where every tail contains jumps.
 *
 * Raw input replay is open loop and integrates any entry error. Re-running the decisions
 * hands each choice back to the controller that made it, which re-derives the keys from
 * the body it actually has.
 */
@Tag("bedrock-corpus")
class DecisionRerunProbeTest {
    @Test
    fun `decision re-run versus raw replay on jumpy terrain`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(
                -2, 56, -BedrockFieldLayout.HALF_WIDTH - 2,
                BedrockFieldLayout.LENGTH + 1, 71, BedrockFieldLayout.HALF_WIDTH + 2,
            ),
            blocks = BedrockFieldLayout.solidCells().associate {
                BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics.FULL_CUBE
            },
        )
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(maxJumpDrop = 2),
        )
        val config = WalkingSeedSearchConfig()
        val nudges = listOf(0.02, 0.05, 0.10, 0.25)
        val raw = nudges.associateWith { IntArray(2) }
        val rerun = nudges.associateWith { IntArray(2) }
        var originalFrames = 0
        var rerunFrames = 0

        for ((index, endpoints) in BedrockFieldLayout.randomEndpointPairs(count = 12).withIndex()) {
            val start = Stance(endpoints.first.x, endpoints.first.y, endpoints.first.z)
            val goal = Stance(endpoints.second.x, endpoints.second.y, endpoints.second.z)
            val planner = CoarsePlanner(environment.withinBudget(start, goal), moves, start, goal)
            if (!planner.repair(Duration.INFINITE).converged) continue
            planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
            val field = planner.valueField()
            val dx = (goal.x - start.x).toDouble()
            val dz = (goal.z - start.z).toDouble()
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
            )
            val outcome = TrajectoryPlanner.searchWithRerouting(planner, index.toLong()) { route ->
                ValueFieldAnchorSearch.search(route, field, initial, PROFILE, environment, config)
            }
            val success = outcome?.result as? WalkingSeedSearchResult.Success ?: continue
            val plan = success.planDecisions ?: continue
            val route = outcome.route

            // Perturb the *start* and ask both methods to reproduce the whole plan.
            for (nudge in nudges) {
                val perturbed = initial.copy(
                    position = initial.position.add(nudge, 0.0, nudge * 0.5),
                    boundingBox = initial.boundingBox.offset(nudge, 0.0, nudge * 0.5),
                )
                val replay = TrajectoryRolloutEngine.rollout(
                    perturbed, PROFILE, environment,
                    InputTape(success.tape.asList()), success.tape.frameCount,
                )
                raw.getValue(nudge)[if (arrived(replay.frames.map { it.state }, goal, config)) 0 else 1]++

                val derived = ValueFieldAnchorSearch.rerun(
                    plan, perturbed, route, field, PROFILE, environment, config,
                )
                val ok = derived != null && run {
                    val check = TrajectoryRolloutEngine.rollout(
                        perturbed, PROFILE, environment, InputTape(derived), derived.size,
                    )
                    arrived(check.frames.map { it.state }, goal, config)
                }
                rerun.getValue(nudge)[if (ok) 0 else 1]++
                if (ok && nudge == 0.10) {
                    originalFrames += success.tape.frameCount
                    rerunFrames += derived!!.size
                }
            }
        }

        println("[rerun] nudge   raw input replay     decision re-run")
        nudges.forEach {
            val a = raw.getValue(it)
            val b = rerun.getValue(it)
            println("[rerun] %.2f    %2d/%2d survived        %2d/%2d survived"
                .format(it, a[0], a[0] + a[1], b[0], b[0] + b[1]))
        }
        println("[rerun] frames at nudge 0.10: original $originalFrames vs re-run $rerunFrames")
    }

    private fun arrived(states: List<MovementSimulationState>, goal: Stance, config: WalkingSeedSearchConfig) =
        states.any {
            it.onGround && it.velocity.horizontalLength() <= config.stoppedSpeed &&
                hypot(it.position.x - (goal.x + 0.5), it.position.z - (goal.z + 0.5)) <= config.goalRadius &&
                kotlin.math.abs(it.position.y - goal.y) <= 0.05
        }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
