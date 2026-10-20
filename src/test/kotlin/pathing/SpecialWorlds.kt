package pathing

import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.shape.VoxelShapes
import kotlin.random.Random

internal fun specialWorld(random: Random): SnapshotSimulationEnvironment {
	val slab = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0))
	val fence = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625), fenceLike = true)
	val carpet = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0))
	val slime = SnapshotBlockPhysics.of(VoxelShapes.fullCube(), bounceFactor = 1.0, dampensSteppingSpeed = true)
	val ladder = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.CLIMBABLE)
	val phantom = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.FULL_BLOCK)
	val water = SnapshotBlockPhysics.water()
	val deepWater = SnapshotBlockPhysics.water(1.0f)
	val lava = SnapshotBlockPhysics(VoxelShapes.empty(), unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID))
	val mud = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.875, 1.0))
	val bounds = SimulationSnapshotBounds(-16, 48, -16, 63, 95, 63)
	val builders = HashMap<Long, ImmutableSnapshotSection.Builder>()
	fun set(x: Int, y: Int, z: Int, physics: SnapshotBlockPhysics) =
		builders.getOrPut(ChunkSectionPos.toLong(BlockPos(x, y, z))) { ImmutableSnapshotSection.Builder() }.set(x, y, z, physics)
	for (x in -4..51) for (z in -4..51) {
		if (random.nextInt(9) == 0) continue
		val top = 62 + random.nextInt(4)
		for (y in 58..top) set(x, y, z, SnapshotBlockPhysics.FULL_CUBE)
		when (random.nextInt(40)) {
			0, 1 -> set(x, top + 1, z, slab)
			2 -> set(x, top + 1, z, fence)
			3 -> set(x, top + 1, z, carpet)
			4 -> set(x, top, z, slime)
			5 -> for (y in top + 1..top + 3) set(x, y, z, ladder)
			11 -> set(x, top, z, water)
			12 -> set(x, top, z, lava)
			13 -> {
				set(x, top + 1, z, deepWater)
				set(x, top + 2, z, water)
			}
			19, 20, 21 -> {
				for (y in top - 2 until top) set(x, y, z, deepWater)
				set(x, top, z, water)
			}
			10 -> {
				set(x, top + 1, z, ladder)
				set(x, top + 2, z, SnapshotBlockPhysics.FULL_CUBE)
			}
			6 -> set(x, top + 1, z, phantom)
			7, 8, 9 -> for (y in top + 1..top + 2) set(x, y, z, SnapshotBlockPhysics.FULL_CUBE)
			14, 15, 16, 17 -> set(x, top, z, mud)
			18 -> set(x, top + 2 + random.nextInt(2), z, mud)
		}
	}
	for (sectionX in -1..3) for (sectionY in 3..5) for (sectionZ in -1..3) {
		builders.getOrPut(ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)) { ImmutableSnapshotSection.Builder() }
	}
	val missing = ChunkSectionPos.asLong(1 + random.nextInt(2), 3 + random.nextInt(2), 1 + random.nextInt(2))
	return streamedSnapshot(bounds, builders.filterKeys { it != missing }.mapValues { it.value.build() })
}
