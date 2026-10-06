package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.util.math.ChunkSectionPos

internal class StanceMemo(private val view: CoarseVoxelView, private val moves: SimpleMoveLibrary) {
	private val sections = Long2ObjectOpenHashMap<LongArray>()
	private var lastKey = 0L
	private var last: LongArray? = null
	private var revision: Any? = view.revision

	fun isStance(stance: Stance): Boolean {
		val current = view.revision
		if (current !== revision) {
			sections.clear()
			last = null
			revision = current
		}
		val bits = section(stance)
		val index = index(stance)
		val word = index ushr 6
		val mask = 1L shl index
		if (bits[word] and mask != 0L) return bits[WORDS + word] and mask != 0L
		val stands = moves.isStance(view, stance)
		bits[word] = bits[word] or mask
		bits[WORDS + word] = if (stands) bits[WORDS + word] or mask else bits[WORDS + word] and mask.inv()
		return stands
	}

	fun remove(stance: Stance) {
		val bits = sections.get(key(stance)) ?: return
		val index = index(stance)
		bits[index ushr 6] = bits[index ushr 6] and (1L shl index).inv()
	}

	fun removeColumns(ranges: List<Pair<IntRange, IntRange>>) {
		val iterator = sections.keys.iterator()
		while (iterator.hasNext()) {
			val key = iterator.nextLong()
			val minX = ChunkSectionPos.unpackX(key) shl 4
			val minZ = ChunkSectionPos.unpackZ(key) shl 4
			if (ranges.any { (xs, zs) -> xs.first <= minX + 15 && minX <= xs.last && zs.first <= minZ + 15 && minZ <= zs.last }) {
				iterator.remove()
			}
		}
		last = null
	}

	private fun section(stance: Stance): LongArray {
		val key = key(stance)
		last?.let { if (key == lastKey) return it }
		val bits = sections.get(key) ?: LongArray(WORDS * 2).also { sections.put(key, it) }
		lastKey = key
		last = bits
		return bits
	}

	private fun key(stance: Stance): Long = ChunkSectionPos.asLong(stance.x shr 4, stance.y shr 4, stance.z shr 4)

	private fun index(stance: Stance): Int = ((stance.y and 15) shl 8) or ((stance.z and 15) shl 4) or (stance.x and 15)

	private companion object {
		const val WORDS = 4096 / 64
	}
}
