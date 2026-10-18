package com.lambda.pathing.find

import com.lambda.config.automation.AutomationConfig
import com.lambda.context.SafeContext
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.block.ShapeContext
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

internal object AccessStances {
	private val BESIDE = listOf(Vec3i(1, 0, 0), Vec3i(-1, 0, 0), Vec3i(0, 0, 1), Vec3i(0, 0, -1)).flatMap { listOf(it, it.down()) }

	private val GROUPS = listOf(BESIDE, listOf(Vec3i(0, 1, 0)), listOf(Vec3i(0, -2, 0)))

	private val ON_GROUPS = listOf(listOf(Vec3i.ZERO)) + GROUPS

	private const val READ_REACH = 2

	private const val READ_BELOW = 4

	private const val READ_ABOVE = 3

	fun choose(view: CoarseVoxelView, target: BlockPos, player: Vec3d, on: Boolean = false): Stance? {
		for (group in if (on) ON_GROUPS else GROUPS) {
			group.map { Stance(target.x + it.x, target.y + it.y, target.z + it.z) }
				.filter { it.y in view.simulableStanceY && Footing.standsAt(view, it) }
				.minByOrNull { player.squaredDistanceTo(it.x + 0.5, it.y.toDouble(), it.z + 0.5) }
				?.let { return it }
		}
		return null
	}

	fun clicked(view: CoarseVoxelView, target: BlockPos, player: Vec3d): Stance? =
		listOf(Stance(target.x, target.y, target.z), Stance(target.x, target.y + 1, target.z))
			.firstOrNull { it.y in view.simulableStanceY && Footing.standsAt(view, it) }
			?: choose(view, target, player)

	fun SafeContext.liveView(target: BlockPos): CoarseVoxelView {
		val sampler = BlockPhysicsSampler(world, ShapeContext.absent(), AutomationConfig.DEFAULT.pathingConfig.hazards)
		return view(target, world.bottomY, world.bottomY + world.height - 1) { liveSection(sampler, it) }
	}

	fun SafeContext.liveSection(sampler: BlockPhysicsSampler, sectionKey: Long): ImmutableSnapshotSection? {
		if (!world.chunkManager.isChunkLoaded(ChunkSectionPos.unpackX(sectionKey), ChunkSectionPos.unpackZ(sectionKey))) return null
		val baseX = ChunkSectionPos.unpackX(sectionKey) shl 4
		val baseY = ChunkSectionPos.unpackY(sectionKey) shl 4
		val baseZ = ChunkSectionPos.unpackZ(sectionKey) shl 4
		val builder = ImmutableSnapshotSection.Builder()
		for (y in baseY until baseY + 16) for (z in baseZ until baseZ + 16) for (x in baseX until baseX + 16) builder.set(x, y, z, sampler.physicsAt(x, y, z))
		return builder.build()
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
