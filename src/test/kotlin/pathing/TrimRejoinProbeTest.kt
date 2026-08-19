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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.time.Duration

/**
 * Does restricting a rejoin to a trim rescue the tails that jumps destroy?
 *
 * The decision re-run measured 7 of 7 on flat ground and 4 of 9 on bedrock, and the
 * difference was never the controller: it was that a bedrock tail launches, and a
 * ballistic arc entered a tenth of a block late lands a tenth of a block late. If that
 * reading is right, the same bedrock tails should survive at flat-ground rates as soon as
 * the rejoin point is required to be somewhere the body is grounded, cruising and pointed
 * where it is already going -- and the maneuver rejoins should carry all the failures.
 *
 * This is the measurement that decides whether shortcutting is worth building.
 */
@Tag("bedrock-corpus")
class TrimRejoinProbeTest {
    @Test
    fun `trim rejoins versus maneuver rejoins on jumpy terrain`() {
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
        val buckets = HashMap<String, HashMap<Double, IntArray>>()
        fun tally(bucket: String, nudge: Double, ok: Boolean) {
            buckets.getOrPut(bucket) { HashMap() }
                .getOrPut(nudge) { IntArray(2) }[if (ok) 0 else 1]++
        }

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
            val frames = success.rollout.frames
            val phases = MotionPhases.classify(success.rollout.initialState, frames)

            // Rejoin only where one decision hands over to the next: re-entering halfway
            // through a committed manoeuvre is not the same plan.
            for (boundary in plan.boundaries) {
                if (boundary < 4 || boundary > success.tape.frameCount - 12) continue
                val suffix = plan.suffixFrom(boundary) ?: continue
                if (suffix.decisions.isEmpty()) continue
                val joint = frames[boundary - 1].state
                val next = suffix.decisions.first()
                val labels = listOf(
                    if (phases[boundary - 1] == MotionPhase.TRIM) "trim" else "maneuver",
                    if (joint.onGround) "grounded" else "airborne",
                    when (next) {
                        is TrajectoryDecision.Launch -> "next=launch"
                        is TrajectoryDecision.Heading -> if (next.delayFrames != null) "next=heading-jump" else "next=heading"
                        is TrajectoryDecision.Walk -> "next=walk"
                    },
                    if (suffix.decisions.any { it is TrajectoryDecision.Launch }) "tail-has-launch" else "tail-no-launch",
                    if (hypot(joint.velocity.x, joint.velocity.z) >= 0.24) "fast" else "slow",
                )

                for (nudge in nudges) {
                    val perturbed = joint.copy(
                        position = joint.position.add(nudge, 0.0, nudge * 0.5),
                        boundingBox = joint.boundingBox.offset(nudge, 0.0, nudge * 0.5),
                    )
                    val derived = ValueFieldAnchorSearch.rerun(
                        suffix, perturbed, route, field, PROFILE, environment, config,
                    )
                    val ok = derived != null && run {
                        val check = TrajectoryRolloutEngine.rollout(
                            perturbed, PROFILE, environment, InputTape(derived), derived.size,
                        )
                        arrived(check.frames.map { it.state }, goal, config)
                    }
                    tally("all", nudge, ok)
                    labels.forEach { tally(it, nudge, ok) }
                }
            }
        }

        println("[trim] %-18s %s".format("bucket", nudges.joinToString("   ") { "%.2f".format(it) }))
        buckets.entries.sortedBy { it.key }.forEach { (name, byNudge) ->
            println(
                "[trim] %-18s %s".format(
                    name,
                    nudges.joinToString("  ") { nudge ->
                        val counts = byNudge[nudge] ?: IntArray(2)
                        val total = counts[0] + counts[1]
                        if (total == 0) "   -   " else "%3d/%3d".format(counts[0], total)
                    },
                )
            )
        }
    }

    private fun arrived(states: List<MovementSimulationState>, goal: Stance, config: WalkingSeedSearchConfig) =
        states.any {
            it.onGround && it.velocity.horizontalLength() <= config.stoppedSpeed &&
                hypot(it.position.x - (goal.x + 0.5), it.position.z - (goal.z + 0.5)) <= config.goalRadius &&
                abs(it.position.y - goal.y) <= 0.05
        }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
