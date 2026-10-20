package com.lambda.pathing.coarse

import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.BounceArcProbe
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.TerrainWatch
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos

internal class BounceIndex(templates: List<MotionTemplate>) {
	class Lookup(templates: List<MotionTemplate>, relative: (MotionTemplate, Int, Int, Int) -> Long) {
		private val byPad = Long2ObjectOpenHashMap<IntArray>()
		val min = IntArray(3) { Int.MAX_VALUE }
		val max = IntArray(3) { Int.MIN_VALUE }

		init {
			val lists = Long2ObjectOpenHashMap<IntArrayList>()
			for (template in templates) {
				val drop = template.bounceDrop ?: continue
				for ((x, z) in BounceArcProbe.padColumns(template.dx, template.dz)) {
					val key = relative(template, x, -drop - 1, z)
					val list = lists.get(key) ?: IntArrayList().also { lists.put(key, it) }
					if (list.isEmpty || list.getInt(list.size - 1) != template.id.value) list.add(template.id.value)
					val coordinates = intArrayOf(BlockPos.unpackLongX(key), BlockPos.unpackLongY(key), BlockPos.unpackLongZ(key))
					for (axis in 0..2) {
						min[axis] = minOf(min[axis], coordinates[axis])
						max[axis] = maxOf(max[axis], coordinates[axis])
					}
				}
			}
			for (entry in lists.long2ObjectEntrySet()) byPad.put(entry.longKey, entry.value.toIntArray())
		}

		operator fun get(x: Int, y: Int, z: Int): IntArray? = byPad.get(BlockPos.asLong(x, y, z))
	}

	val arrivals = Lookup(templates) { template, x, y, z -> BlockPos.asLong(x - template.dx, y - template.dy, z - template.dz) }

	val departures = Lookup(templates) { _, x, y, z -> BlockPos.asLong(x, y, z) }
}

internal class BouncePads(private val view: CoarseVoxelView, private val index: BounceIndex) {
	private val sections = Long2ObjectOpenHashMap<LongArray>()
	private val watch = TerrainWatch(view)

	fun arrivals(target: Stance): IntArray = candidates(target, index.arrivals)

	fun departures(origin: Stance): IntArray = candidates(origin, index.departures)

	fun clear() = sections.clear()

	private fun forget(changed: CellBox) {
		val keys = sections.keys.iterator()
		while (keys.hasNext()) {
			val key = keys.nextLong()
			val minX = ChunkSectionPos.unpackX(key) shl 4
			val minY = ChunkSectionPos.unpackY(key) shl 4
			val minZ = ChunkSectionPos.unpackZ(key) shl 4
			if (changed.overlaps(minX, minY - 1, minZ, minX + 15, minY + 15, minZ + 15)) keys.remove()
		}
	}

	private fun candidates(at: Stance, index: BounceIndex.Lookup): IntArray {
		if (index.min[0] > index.max[0]) return EMPTY
		var found: IntAVLTreeSet? = null
		val minX = at.x + index.min[0]
		val minY = at.y + index.min[1]
		val minZ = at.z + index.min[2]
		val maxX = at.x + index.max[0]
		val maxY = at.y + index.max[1]
		val maxZ = at.z + index.max[2]
		for (sectionX in (minX shr 4)..(maxX shr 4)) for (sectionY in (minY shr 4)..(maxY shr 4)) for (sectionZ in (minZ shr 4)..(maxZ shr 4)) {
			for (pad in pads(sectionX, sectionY, sectionZ)) {
				val x = BlockPos.unpackLongX(pad)
				val y = BlockPos.unpackLongY(pad)
				val z = BlockPos.unpackLongZ(pad)
				if (x !in minX..maxX || y !in minY..maxY || z !in minZ..maxZ) continue
				val templates = index[x - at.x, y - at.y, z - at.z] ?: continue
				(found ?: IntAVLTreeSet().also { found = it }).addAll(IntArrayList.wrap(templates))
			}
		}
		return found?.toIntArray() ?: EMPTY
	}

	fun padsIn(sectionX: Int, sectionY: Int, sectionZ: Int): LongArray = pads(sectionX, sectionY, sectionZ)

	private fun pads(sectionX: Int, sectionY: Int, sectionZ: Int): LongArray {
		watch.poll(::clear, ::forget)
		val key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
		sections.get(key)?.let { return it }
		val minX = sectionX shl 4
		val minY = sectionY shl 4
		val minZ = sectionZ shl 4
		val pads = if (!view.mayBounceWithin(minX, minY - 1, minZ, minX + 15, minY + 15, minZ + 15)) EMPTY_PADS else {
			val found = LongArrayList()
			for (y in minY..minY + 15) for (z in minZ..minZ + 15) for (x in minX..minX + 15) {
				if (BounceArcProbe.isPad(view, x, y, z)) found.add(BlockPos.asLong(x, y, z))
			}
			if (found.isEmpty) EMPTY_PADS else found.toLongArray()
		}
		sections.put(key, pads)
		return pads
	}

	private companion object {
		val EMPTY = IntArray(0)

		val EMPTY_PADS = LongArray(0)
	}
}
