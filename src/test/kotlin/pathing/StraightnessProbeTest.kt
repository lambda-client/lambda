/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.TrajectoryPlanner.withinBudget
import com.lambda.pathing.coarse.*
import com.lambda.pathing.trajectory.*
import com.lambda.util.player.prediction.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.time.Duration

/** How straight is the line, really? Flat ground, so any wander is the search's own. */
@Tag("bedrock-corpus")
class StraightnessProbeTest {
    @Test
    fun `path length and turning on open flat ground`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -20..20) for (z in -20..20) blocks[BlockPos(x, 99, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-24, 92, -24, 24, 112, 24), blocks,
        )
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(maxJumpDrop = 2),
        )
        for ((label, goal) in listOf(
            "diagonal-45" to Stance(10, 100, 10),
            "shallow-angle" to Stance(14, 100, 5),
            "straight" to Stance(0, 100, 14),
        )) {
            val start = Stance(0, 100, 0)
            for (engine in listOf("anchor", "value-field")) {
                val planner = CoarsePlanner(environment.withinBudget(start, goal), moves, start, goal)
                check(planner.repair(Duration.INFINITE).converged)
                val initial = MovementSimulationState.synthetic(
                    profile = PROFILE, position = Vec3d(0.5, 100.0, 0.5),
                    rotation = Rotation(0.0, 0.0), velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
                )
                val outcome = TrajectoryPlanner.searchWithRerouting(planner, 0L) { route ->
                    if (engine == "anchor") {
                        MotionAnchorSearch.search(route, initial, PROFILE, environment, WalkingSeedSearchConfig())
                    } else {
                        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
                        ValueFieldAnchorSearch.search(
                            route, planner.valueField(), initial, PROFILE, environment, WalkingSeedSearchConfig(),
                        )
                    }
                }
                val r = outcome?.result as? WalkingSeedSearchResult.Success
                if (r == null) { println("[straightness] $label $engine REFUSED"); continue }
                var travelled = 0.0
                var turning = 0.0
                var previous = r.rollout.initialState
                r.rollout.frames.forEach {
                    travelled += hypot(it.state.position.x - previous.position.x, it.state.position.z - previous.position.z)
                    turning += abs(Rotation.wrap(it.state.rotation.yaw - previous.rotation.yaw))
                    previous = it.state
                }
                val direct = hypot((goal.x - start.x).toDouble(), (goal.z - start.z).toDouble())
                println(
                    "[straightness] %-14s %-12s frames %3d  travelled %.1f vs direct %.1f (%.0f%% extra)  total turn %.0f deg"
                        .format(label, engine, r.tape.frameCount, travelled, direct, 100 * (travelled / direct - 1), turning)
                )
                if (label == "straight" && engine == "value-field") {
                    var prev = r.rollout.initialState
                    r.rollout.frames.forEach {
                        val turn = Rotation.wrap(it.state.rotation.yaw - prev.rotation.yaw)
                        if (abs(turn) > 0.5) println(
                            "    frame %3d yaw %7.1f -> target %7.1f (turn %+6.1f) x=%.2f z=%.2f v=%.3f"
                                .format(it.index, prev.rotation.yaw, it.input.rotation?.yaw ?: Double.NaN,
                                    turn, it.state.position.x, it.state.position.z,
                                    it.state.velocity.horizontalLength())
                        )
                        prev = it.state
                    }
                }
            }
        }
    }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
