package com.lambda.pathing.coarse

import com.lambda.pathing.actions.families.DiveMovement
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.TerrainWatch
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.util.math.ChunkSectionPos

internal class StanceMemo(
	private val view: CoarseVoxelView,
	private val moves: SimpleMoveLibrary,
	private val goals: List<Stance> = emptyList(),
) {
	private val sections = Long2ObjectOpenHashMap<LongArray>()
	private var lastKey = 0L
	private var last: LongArray? = null
	private val watch = TerrainWatch(view)

	fun isStance(stance: Stance): Boolean {
		watch.poll(::clear) { box -> remove(box.grown(-STANCE_REACH, STANCE_REACH, -STANCE_REACH - 1, STANCE_REACH + 1, -STANCE_REACH, STANCE_REACH)) }
		val bits = section(stance)
		val index = index(stance)
		val word = index ushr 6
		val mask = 1L shl index
		if (bits[word] and mask != 0L) return bits[WORDS + word] and mask != 0L
		val stands = moves.isStance(view, stance) || inGoalColumn(stance)
		bits[word] = bits[word] or mask
		bits[WORDS + word] = if (stands) bits[WORDS + word] or mask else bits[WORDS + word] and mask.inv()
		return stands
	}

	fun inGoalColumn(stance: Stance): Boolean = goals.any { DiveMovement.inOpenColumn(view, it, stance) }

	fun remove(stance: Stance) {
		val bits = sections.get(key(stance)) ?: return
		val index = index(stance)
		bits[index ushr 6] = bits[index ushr 6] and (1L shl index).inv()
	}

	fun remove(boxes: List<CellBox>) {
		for (box in boxes) remove(box)
	}

	private fun remove(box: CellBox) {
		val count = ((box.xs.last shr 4) - (box.xs.first shr 4) + 1).toLong() *
				((box.ys.last shr 4) - (box.ys.first shr 4) + 1) * ((box.zs.last shr 4) - (box.zs.first shr 4) + 1)
		if (count <= sections.size) {
			for (sectionX in (box.xs.first shr 4)..(box.xs.last shr 4)) for (sectionY in (box.ys.first shr 4)..(box.ys.last shr 4)) {
				for (sectionZ in (box.zs.first shr 4)..(box.zs.last shr 4)) sections.remove(ChunkSectionPos.asLong(sectionX, sectionY, sectionZ))
			}
		} else {
			val iterator = sections.keys.iterator()
			while (iterator.hasNext()) {
				val key = iterator.nextLong()
				val minX = ChunkSectionPos.unpackX(key) shl 4
				val minY = ChunkSectionPos.unpackY(key) shl 4
				val minZ = ChunkSectionPos.unpackZ(key) shl 4
				if (box.overlaps(minX, minY, minZ, minX + 15, minY + 15, minZ + 15)) iterator.remove()
			}
		}
		last = null
	}

	private fun clear() {
		sections.clear()
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
		const val STANCE_REACH = 2
	}
}
