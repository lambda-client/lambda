package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.SearchExhaustion
import com.lambda.pathing.search.VirtualSearchClock
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.bedrockEnvironment
import pathing.ProbeScenarios.moveLibrary

/** A/B of the published-spine span optimiser on the random bedrock walks; informational. */
class SpanOptimiserProbeTest {
    @Test
    fun `span optimiser A over B on bedrock walks`() {
        val environment = bedrockEnvironment()
        val moves = moveLibrary(SimpleMoveOptions(maxJumpDrop = 2))
        val pairs = BedrockFieldLayout.randomEndpointPairs(count = 6)
        for (budget in listOf(0, 1500)) {
            var total = 0
            var arrived = 0
            var splices = 0
            var saved = 0
            var rollouts = 0
            for ((index, endpoints) in pairs.withIndex()) {
                val start = Stance(endpoints.first.x, endpoints.first.y, endpoints.first.z)
                val goal = Stance(endpoints.second.x, endpoints.second.y, endpoints.second.z)
                val planner = CoarsePlanner(environment, moves, start, goal)
                if (!planner.repair(Duration.INFINITE).converged) continue
                planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
                val route = planner.routePlan(index.toLong()) ?: continue
                val dx = (goal.x - start.x).toDouble()
                val dz = (goal.z - start.z).toDouble()
                val initial = MovementSimulationState.synthetic(
                    profile = PROFILE,
                    position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                    rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                    velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
                )
                val clock = VirtualSearchClock()
                val executor = VirtualExecutor(clock)
                var report: SearchExhaustion? = null
                val outcome = TrajectoryPlanner.walkHorizon(
                    route, planner, initial, PROFILE, environment, MotionConstraints(),
                    cursorFrame = { executor.cursorFrame() },
                    publish = { path, _ -> executor.offer(path) },
                    started = System.currentTimeMillis(),
                    clock = clock,
                    adoptedSequence = executor::adoptedSequence,
                    improvementBudget = budget,
                    onExhaustion = { report = it },
                    fieldExpansionBudget = Duration.INFINITE,
                )
                val planned = (outcome as? PathPlanResult.Planned)?.path
                val frames = planned?.plan?.tape?.frameCount ?: 0
                if (planned != null && !planned.partial) { arrived++; total += frames }
                report?.let { splices += it.improvementSplices; saved += it.improvementSaved; rollouts += it.improvementRollouts }
                println("[span] budget=$budget case=$index ${if (planned?.partial == false) "arrived" else "SHORT"} frames=$frames improver=${report?.improvementDiagnosis}")
            }
            println("[span] budget=$budget arrived=$arrived totalFrames=$total splices=$splices saved=$saved rollouts=$rollouts")
        }
    }
}
