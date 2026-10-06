package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.util.math.ChunkSectionPos

internal class StanceMemo {
	private val sections = Long2ObjectOpenHashMap<LongArray>()
	private var lastKey = 0L
	private var last: LongArray? = null

	fun get(stance: Stance): Boolean? {
		val bits = section(stance, create = false) ?: return null
		val index = index(stance)
		val mask = 1L shl index
		if (bits[index ushr 6] and mask == 0L) return null
		return bits[WORDS + (index ushr 6)] and mask != 0L
	}

	fun put(stance: Stance, stands: Boolean) {
		val bits = checkNotNull(section(stance, create = true))
		val index = index(stance)
		val word = index ushr 6
		val mask = 1L shl index
		bits[word] = bits[word] or mask
		bits[WORDS + word] = if (stands) bits[WORDS + word] or mask else bits[WORDS + word] and mask.inv()
	}

	fun clear() {
		sections.clear()
		last = null
	}

	fun remove(stance: Stance) {
		val bits = section(stance, create = false) ?: return
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

	private fun section(stance: Stance, create: Boolean): LongArray? {
		val key = ChunkSectionPos.asLong(stance.x shr 4, stance.y shr 4, stance.z shr 4)
		last?.let { if (key == lastKey) return it }
		val bits = sections.get(key) ?: if (create) LongArray(WORDS * 2).also { sections.put(key, it) } else return null
		lastKey = key
		last = bits
		return bits
	}

	private fun index(stance: Stance): Int = ((stance.y and 15) shl 8) or ((stance.z and 15) shl 4) or (stance.x and 15)

	private companion object {
		const val WORDS = 4096 / 64
	}
}
