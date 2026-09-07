/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import kotlin.test.Test
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Tag

/**
 * A hundred blocks of flat, straight, open ground: the purest momentum fixture.
 *
 * GaitSpeedProbeTest's hand-rolled reference crosses it in 286 ticks by jumping on
 * every landing (0.3506 b/t sustained); plain sprinting takes 358. Whatever tape the
 * planner produces here, the distance to 286 is the momentum headroom the search has
 * not claimed -- with no parkour noise, no corners and no terrain excuses. Non-gating.
 */
@Tag("bedrock-corpus")
class FlatRunProbeTest {

    @Test
    fun `a flat hundred-block run against the gait reference`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (z in -6..110) for (x in -6..6) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-10, 58, -10, 10, 90, 114), blocks,
        )
        for (gait in listOf(false, true)) {
            val scenario = ProbeScenarios.Scenario(
                "flat-100", environment, Stance(0, 64, 0), Stance(0, 64, 100),
                SimpleMoveOptions(maxJumpDrop = 2),
            )
            val outcome = ProbeScenarios.plan(scenario, momentumGait = gait)
            val path = (outcome.result as? PathPlanResult.Planned)?.path
            if (path != null && !path.partial) {
                println(
                    "[flat] gait=%-5s frames=%-4d (gait reference 286, sprint 358) %s"
                        .format(gait, path.plan.frames.size, path.movementProfile()),
                )
                // Grounded-run lengths between flights: the gait loses exactly one tick
                // per landing spent on the ground beyond the single tick a chain needs.
                val runs = ArrayList<Int>()
                var grounded = 0
                var sawAir = false
                for (frame in path.plan.frames) {
                    if (frame.state.onGround) grounded++ else {
                        if (sawAir && grounded > 0) runs += grounded
                        if (grounded > 0) sawAir = true
                        grounded = 0
                    }
                }
                val histogram = runs.groupingBy { it }.eachCount().toSortedMap()
                println("[flat]   grounded runs between flights: $histogram")
            } else {
                println("[flat] gait=$gait FAIL last: ${outcome.exhaustions.lastOrNull()}")
            }
        }
    }
}
