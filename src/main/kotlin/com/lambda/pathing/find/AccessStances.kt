package com.lambda.pathing.find

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

internal object AccessStances {
	private val BESIDE = listOf(Vec3i(1, 0, 0), Vec3i(-1, 0, 0), Vec3i(0, 0, 1), Vec3i(0, 0, -1)).flatMap { listOf(it, it.down()) }

	private val GROUPS = listOf(BESIDE, listOf(Vec3i(0, 1, 0)), listOf(Vec3i(0, -2, 0)))

	private const val READ_REACH = 2

	private const val READ_BELOW = 4

	private const val READ_ABOVE = 3

	fun choose(view: CoarseVoxelView, target: BlockPos, player: Vec3d): Stance? {
		for (group in GROUPS) {
			group.map { Stance(target.x + it.x, target.y + it.y, target.z + it.z) }
				.filter { it.y in view.simulableStanceY && Footing.standsAt(view, it) }
				.minByOrNull { player.squaredDistanceTo(it.x + 0.5, it.y.toDouble(), it.z + 0.5) }
				?.let { return it }
		}
		return null
	}

	fun view(target: BlockPos, minY: Int, maxY: Int, section: (Long) -> ImmutableSnapshotSection?): CoarseVoxelView {
		val sections = HashMap<Long, ImmutableSnapshotSection>()
		for (sectionX in ((target.x - READ_REACH) shr 4)..((target.x + READ_REACH) shr 4)) {
			for (sectionZ in ((target.z - READ_REACH) shr 4)..((target.z + READ_REACH) shr 4)) {
				for (sectionY in (maxOf(target.y - READ_BELOW, minY) shr 4)..(minOf(target.y + READ_ABOVE, maxY) shr 4)) {
					val key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
					section(key)?.let { sections[key] = it }
				}
			}
		}
		val bounds = SimulationSnapshotBounds(Int.MIN_VALUE, minY, Int.MIN_VALUE, Int.MAX_VALUE, maxY, Int.MAX_VALUE)
		return SnapshotSimulationEnvironment(bounds, sections, defaultBlock = null)
	}

	fun loaded(target: BlockPos, loaded: (chunkX: Int, chunkZ: Int) -> Boolean): Boolean {
		for (chunkX in ((target.x - READ_REACH) shr 4)..((target.x + READ_REACH) shr 4)) {
			for (chunkZ in ((target.z - READ_REACH) shr 4)..((target.z + READ_REACH) shr 4)) if (!loaded(chunkX, chunkZ)) return false
		}
		return true
	}
}
