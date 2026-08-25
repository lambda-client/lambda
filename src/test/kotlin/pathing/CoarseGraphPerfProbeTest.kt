package pathing

import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.VoxelPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import org.junit.jupiter.api.Test
import kotlin.time.measureTime

/**
 * Prints coarse-graph construction costs; asserts nothing.
 *
 * Kept as a measurement harness: run it after touching edge generation, the arc sweep,
 * or repair, and compare the printed numbers against the previous run. The 2026-08
 * optimization pass took edgesFrom on flat ground from 1041us/node and 16,100 collision
 * shape reads to 243us/node and none.
 */
class CoarseGraphPerfProbeTest {
    private class CountingFlatWorld(private val floorY: Int = 64) : CoarseVoxelView {
        var voxelReads = 0L
        var shapeReads = 0L
        var classReads = 0L

        override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel {
            voxelReads++
            return if (y < floorY) CoarseVoxel.FULL_BLOCK else CoarseVoxel.AIR
        }

        override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape {
            shapeReads++
            return if (y < floorY) VoxelShapes.fullCube() else VoxelShapes.empty()
        }

        override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass {
            classReads++
            return if (y < floorY) CollisionClass.FULL else CollisionClass.EMPTY
        }
    }

    @Test
    fun probe() {
        for (bounces in listOf(false, true)) {
            val options = SimpleMoveOptions(allowSlimeBounces = bounces)
            val library = SimpleMoveLibrary.build(CoarseMoveCosts.measured(), options)
            println("=== allowSlimeBounces=$bounces ===")
            println("templates: ${library.templates.size}")

            val world = CountingFlatWorld()
            val origin = Stance(0, 64, 0)

            repeat(50) { library.edgesFrom(world, origin) }
            world.voxelReads = 0; world.shapeReads = 0; world.classReads = 0

            var edges = 0
            val n = 500
            val fromTime = measureTime {
                repeat(n) { edges = library.edgesFrom(world, Stance(it, 64, it)).size }
            }
            println(
                "edgesFrom(flat): ${fromTime.inWholeMicroseconds / n}us/node, " +
                    "edges=$edges, voxelReads/node=${world.voxelReads / n}, " +
                    "shapeReads/node=${world.shapeReads / n}, classReads/node=${world.classReads / n}"
            )

            val world2 = CountingFlatWorld()
            val planner = CoarsePlanner(world2, library, Stance(0, 64, 0), Stance(100, 64, 0))
            val searchTime = measureTime {
                planner.repair(timeBudget = kotlin.time.Duration.INFINITE)
            }
            val route = planner.route()
            println(
                "flat 100-block route: $searchTime, nodes=${planner.graphSize}, " +
                    "routeLen=${route?.nodes?.size}, ticks=${route?.ticks}, " +
                    "voxelReads=${world2.voxelReads}, shapeReads=${world2.shapeReads}, " +
                    "classReads=${world2.classReads}"
            )

            world2.voxelReads = 0; world2.shapeReads = 0; world2.classReads = 0
            val repairTime = measureTime {
                planner.worldChanged(listOf(VoxelPos(50, 64, 0)))
                planner.repair(timeBudget = kotlin.time.Duration.INFINITE)
            }
            println(
                "one-block repair: $repairTime, voxelReads=${world2.voxelReads}, " +
                    "shapeReads=${world2.shapeReads}, classReads=${world2.classReads}"
            )
        }
    }
}
