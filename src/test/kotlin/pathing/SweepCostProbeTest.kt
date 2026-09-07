package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.FrontierAnchors
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import kotlin.test.Test

class SweepCostProbeTest {
    @Test
    fun `where a frontier sweep spends its time`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -120..120) for (z in -120..120) for (y in 0..3) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-130, -8, -130, 130, 40, 130), blocks = blocks,
        )
        val start = Stance(0, 4, 0)
        val goal = Stance(400, 4, 0) // far outside: every ring edge is frontier
        val state = CoarsePlanningState(environment, SimpleMoveOptions(), start, goal, horizonChunks = 4, capturable = { _, _ -> true })
        val planner = state.planner
        repeat(1) { round ->
            var edgeCalls = 0
            var edgeNanos = 0L
            var expanded = 0
            val edges: (Stance) -> List<com.lambda.pathing.actions.CoarseEdge> = { stance ->
                edgeCalls++
                val t = System.nanoTime()
                val e = planner.moves.edgesFrom(planner.view, stance)
                edgeNanos += System.nanoTime() - t
                e
            }
            val t0 = System.nanoTime()
            val anchors = FrontierAnchors.sweep(
                planner.view, planner.moves, listOf(start), goal, HashSet(), null,
                capturable = { _, _ -> true }, onCaptureLag = { _, _, _ -> expanded++ }, edges = edges,
            )
            val total = (System.nanoTime() - t0) / 1e6
            println("[sweep] fresh-edges round=$round anchors=${anchors.size} edgeCalls=$edgeCalls edgeMs=%.1f totalMs=%.1f lagCells=$expanded".format(edgeNanos / 1e6, total))
        }
        repeat(2) { round ->
            val t0 = System.nanoTime()
            val changed = planner.advanceReachableFrontier(from = start)
            println("[sweep] planner.advanceReachableFrontier round=$round changed=$changed ms=%.1f anchors=${planner.optimisticAnchors.size}".format((System.nanoTime() - t0) / 1e6))
        }
    }
}
