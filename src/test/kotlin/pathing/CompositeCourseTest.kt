package pathing

import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.time.Duration

/**
 * Full-course coarse connectivity over composite parkour (beds + carpeted pads),
 * both unbounded and through the planning-horizon ring with terminal grants --
 * the shape of a real online course, pinned after a field no-route report.
 */
class CompositeCourseTest {
    @Test
    fun `a composite course routes end to end in one plan`() {
        val slime = SnapshotBlockPhysics.of(
            VoxelShapes.fullCube(), bounceFactor = 1.0, dampensSteppingSpeed = true,
        )
        val carpet = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0))
        val bed = SnapshotBlockPhysics.of(
            VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5625, 1.0),
            bounceFactor = SnapshotSimulationEnvironment.BED_BOUNCE_FACTOR,
        )
        val blocks = buildMap {
            for (x in -1..1) {
                // Walk strip, gap, bed platform, gap, bed platform, gap, lip strip.
                for (z in 0..3) put(BlockPos(x, 9, z), SnapshotBlockPhysics.FULL_CUBE)
                for (z in 6..7) put(BlockPos(x, 9, z), bed)
                for (z in 10..12) put(BlockPos(x, 9, z), SnapshotBlockPhysics.FULL_CUBE)
                // Pit with a single carpeted slime pad, landing two below the lip.
                for (z in 13..17) put(BlockPos(x, 5, z), SnapshotBlockPhysics.FULL_CUBE)
                for (z in 18..22) put(BlockPos(x, 7, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            put(BlockPos(0, 5, 15), slime)
            put(BlockPos(0, 6, 15), carpet)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 40), blocks,
        )
        val moves = SimpleMoveLibrary.build(
            CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            SimpleMoveOptions(allowSlimeBounces = true, maxBounceDrop = 12),
        )
        for ((start, goal, label) in listOf(
            Triple(Stance(0, 10, 0), Stance(0, 8, 21), "full course"),
            Triple(Stance(0, 10, 0), Stance(0, 10, 11), "first half (beds)"),
            Triple(Stance(0, 10, 11), Stance(0, 8, 21), "second half (pit)"),
        )) {
            val planner = CoarsePlanner(environment, moves, start, goal)
            planner.repair(Duration.INFINITE)
            val route = checkNotNull(planner.routePlan(0L)) {
                "$label must route: ${planner.routeFailureReport()}"
            }
            println("COURSE $label: ${route.edges.map { it.movement }}")
        }
    }

    /** The field configuration: a course longer than the planning horizon ring. */
    @Test
    fun `a course longer than the horizon ring resolves via terminal grants`() {
        val slime = SnapshotBlockPhysics.of(
            VoxelShapes.fullCube(), bounceFactor = 1.0, dampensSteppingSpeed = true,
        )
        val carpet = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0))
        val bed = SnapshotBlockPhysics.of(
            VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5625, 1.0),
            bounceFactor = SnapshotSimulationEnvironment.BED_BOUNCE_FACTOR,
        )
        // Six repeats of the 24-block segment: 144 blocks, nine chunks of course.
        val blocks = buildMap {
            for (segment in 0 until 6) {
                val base = segment * 24
                for (x in -1..1) {
                    for (z in 0..3) put(BlockPos(x, 9, base + z), SnapshotBlockPhysics.FULL_CUBE)
                    for (z in 6..7) put(BlockPos(x, 9, base + z), bed)
                    for (z in 10..12) put(BlockPos(x, 9, base + z), SnapshotBlockPhysics.FULL_CUBE)
                    for (z in 13..17) put(BlockPos(x, 5, base + z), SnapshotBlockPhysics.FULL_CUBE)
                    for (z in 18..23) put(BlockPos(x, 7, base + z), SnapshotBlockPhysics.FULL_CUBE)
                }
                put(BlockPos(0, 5, base + 15), slime)
                put(BlockPos(0, 6, base + 15), carpet)
                // A stair back up to the next segment's strip height.
                for (x in -1..1) {
                    put(BlockPos(x, 8, base + 23), SnapshotBlockPhysics.FULL_CUBE)
                }
            }
            for (x in -1..1) for (z in 144..146) put(BlockPos(x, 9, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 0, -16, 16, 30, 176), blocks,
        )
        val state = com.lambda.pathing.coarse.CoarsePlanningState(
            snapshot = environment,
            moveOptions = SimpleMoveOptions(allowSlimeBounces = true, maxBounceDrop = 12),
            start = Stance(0, 10, 0),
            goal = Stance(0, 10, 145),
            horizonChunks = 4,
        )
        val route = state.resolveRoute(
            start = Stance(0, 10, 0), snapshotRevision = 0L, maxExpansions = 1_000_000,
        )
        val resolved = checkNotNull(route) {
            "the ringed course must resolve: ${state.planner.routeFailureReport()}"
        }
        kotlin.test.assertEquals(Stance(0, 10, 145), resolved.goal, "the terminal grants must reach the true goal")
    }
}
