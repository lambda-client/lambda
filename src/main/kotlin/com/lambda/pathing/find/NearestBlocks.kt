package com.lambda.pathing.find

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.chunk.PalettedContainer
import java.util.*

enum class BlockSource(val label: String) {
	LOADED("loaded chunks"),
	STORE("Lambda's terrain cache"),
	BARITONE("Baritone's cache"),
}

class FoundBlock(val pos: BlockPos, val source: BlockSource, val distanceSquared: Double)

internal class NearestBlocks(private val origin: Vec3d, private val limit: Int, private val excluded: Set<BlockPos>) {
	private val heap = PriorityQueue<FoundBlock>(compareByDescending { it.distanceSquared })
	private val seen = HashSet<BlockPos>()

	val farthest: Double get() = if (heap.size < limit) Double.POSITIVE_INFINITY else heap.peek().distanceSquared

	fun offer(x: Int, y: Int, z: Int, source: BlockSource, exposed: (Int, Int, Int) -> Boolean) {
		val distance = squaredDistance(x + 0.5, y + 0.5, z + 0.5)
		if (distance >= farthest) return
		val pos = BlockPos(x, y, z)
		if (pos in excluded || pos in seen || !OPENINGS.any { (dx, dy, dz) -> exposed(x + dx, y + dy, z + dz) }) return
		seen += pos
		heap += FoundBlock(pos, source, distance)
		if (heap.size > limit) seen -= heap.poll().pos
	}

	fun sorted(): List<FoundBlock> = heap.sortedBy { it.distanceSquared }

	fun scan(sectionKey: Long, states: PalettedContainer<BlockState>, query: BlockQuery, source: BlockSource, exposed: (Int, Int, Int) -> Boolean) {
		val baseX = ChunkSectionPos.unpackX(sectionKey) shl 4
		val baseY = ChunkSectionPos.unpackY(sectionKey) shl 4
		val baseZ = ChunkSectionPos.unpackZ(sectionKey) shl 4
		if (boxDistance(baseX, baseY, baseZ, baseX + 15, baseY + 15, baseZ + 15) >= farthest) return
		if (!states.hasAny(query::matches)) return
		for (y in 0..15) for (z in 0..15) for (x in 0..15) {
			if (query.matches(states.get(x, y, z))) offer(baseX + x, baseY + y, baseZ + z, source, exposed)
		}
	}

	fun boxDistance(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Double =
		squaredDistance(
			origin.x.coerceIn(minX + 0.5, maxX + 0.5),
			origin.y.coerceIn(minY + 0.5, maxY + 0.5),
			origin.z.coerceIn(minZ + 0.5, maxZ + 0.5),
		)

	private fun squaredDistance(x: Double, y: Double, z: Double): Double {
		val dx = x - origin.x
		val dy = y - origin.y
		val dz = z - origin.z
		return dx * dx + dy * dy + dz * dz
	}

	private companion object {
		val OPENINGS = listOf(Triple(1, 0, 0), Triple(-1, 0, 0), Triple(0, 1, 0), Triple(0, -1, 0), Triple(0, 0, 1), Triple(0, 0, -1))
	}
}
