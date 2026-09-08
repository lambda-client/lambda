package pathing

import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.SimulationSnapshotOutOfBoundsException
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.floor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupportingBlockParityTest {
    @Test
    fun `cached local boxes preserve support and all read dependencies at large coordinates`() {
        val random = Random(973252)
        val shapes = listOf(
            VoxelShapes.fullCube(), VoxelShapes.empty(),
            VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
            VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625),
            VoxelShapes.cuboid(-0.25, 0.0, 0.2, 1.25, 0.75, 0.8),
            VoxelShapes.union(
                VoxelShapes.cuboid(0.0, 0.0, 0.0, 0.2, 0.8, 1.0),
                VoxelShapes.cuboid(0.8, 0.0, 0.0, 1.0, 0.8, 1.0),
            ),
        ).map { SnapshotBlockPhysics(it) }
        for (origin in listOf(-29_999_990, -16, 0, 16, 29_999_990)) {
            val blocks = buildMap {
                for (x in -3..3) for (z in -3..3) for (y in 99..100) {
                    put(BlockPos(origin + x, y, origin + z), shapes.random(random))
                }
            }
            val environment = SnapshotSimulationEnvironment.synthetic(
                SimulationSnapshotBounds(origin - 8, 90, origin - 8, origin + 8, 110, origin + 8), blocks,
            )
            val tracked = environment.trackingView()
            repeat(200) {
                val x = origin + random.nextDouble(-2.0, 2.0)
                val z = origin + random.nextDouble(-2.0, 2.0)
                val y = random.nextDouble(99.0, 102.0)
                val box = Box(x - 0.3, y - 1.0E-6, z - 0.3, x + 0.3, y + 0.01, z + 0.3)
                val pos = Vec3d(x, y, z)
                val expectedReads = HashSet<VoxelPos>()
                val expected = reference(blocks, box, pos, expectedReads)
                assertEquals(expected, tracked.findSupportingBlockPos(box, pos), "origin=$origin case=$it")
                assertEquals(expectedReads, tracked.takeFrameDependencies(), "read footprint changed")
            }
        }
    }

    @Test
    fun `equidistant supports and touching faces match the shape based reference`() {
        val blocks = mapOf(BlockPos(0, 99, 0) to SnapshotBlockPhysics.FULL_CUBE,
            BlockPos(1, 99, 0) to SnapshotBlockPhysics.FULL_CUBE)
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-4, 95, -4, 5, 105, 4), blocks,
        )
        for (height in listOf(100.0, Math.nextDown(100.0), Math.nextUp(100.0))) {
            val box = Box(0.7, height, 0.2, 1.3, 100.01, 0.8)
            val position = Vec3d(1.0, 100.0, 0.5)
            assertEquals(reference(blocks, box, position, HashSet()), environment.findSupportingBlockPos(box, position))
        }
    }

    @Test
    fun `support queries still refuse reads outside captured bounds`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(0, 99, 0, 0, 101, 0),
            mapOf(BlockPos(0, 99, 0) to SnapshotBlockPhysics.FULL_CUBE),
        )
        val failure = assertFailsWith<SimulationSnapshotOutOfBoundsException> {
            environment.findSupportingBlockPos(Box(0.2, 99.9999, 0.2, 0.8, 100.0, 0.8), Vec3d(0.5, 100.0, 0.5))
        }
        assertEquals(BlockPos(-1, 98, -1), failure.pos)
    }

    /** Original translated-VoxelShape query, independent of cached geometry. */
    private fun reference(
        blocks: Map<BlockPos, SnapshotBlockPhysics>, box: Box, entityPos: Vec3d, reads: MutableSet<VoxelPos>,
    ): BlockPos? {
        var best: BlockPos? = null
        var bestDistance = Double.MAX_VALUE
        for (y in floor(box.minY - 1.0E-7).toInt() - 1..floor(box.maxY + 1.0E-7).toInt() + 1) {
            for (z in floor(box.minZ - 1.0E-7).toInt() - 1..floor(box.maxZ + 1.0E-7).toInt() + 1) {
                for (x in floor(box.minX - 1.0E-7).toInt() - 1..floor(box.maxX + 1.0E-7).toInt() + 1) {
                    val pos = BlockPos(x, y, z)
                    reads += VoxelPos(x, y, z)
                    val block = blocks[pos] ?: continue
                    if (!block.collisionShape.offset(x.toDouble(), y.toDouble(), z.toDouble()).boundingBoxes.any { it.intersects(box) }) continue
                    val distance = pos.getSquaredDistance(entityPos)
                    if (distance < bestDistance || (distance == bestDistance && (best == null || best < pos))) {
                        best = pos
                        bestDistance = distance
                    }
                }
            }
        }
        return best
    }
}
