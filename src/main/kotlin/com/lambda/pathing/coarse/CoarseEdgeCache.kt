package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import it.unimi.dsi.fastutil.HashCommon
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.util.math.ChunkSectionPos

internal fun interface EdgeVisitor {
	fun visit(template: MotionTemplate, ticks: Double, standingStart: Boolean)
}

internal class CoarseEdgeCache(
	private val view: CoarseVoxelView,
	private val moves: SimpleMoveLibrary,
) {
	private val outgoing = Long2ObjectOpenHashMap<List<CoarseEdge>>()

	private val checkedOrigins = LongOpenHashSet()

	private val completeOrigins = LongOpenHashSet()

	private val standable = StanceMemo(view, moves)

	private val stamper = EdgeStamper(view, moves, standable)

	private val originRows = RowCache(stamper::row)

	private val targetRows = RowCache(::gather)

	private val gathered = LongArray(moves.templates.size * 3)

	private val touched = LongArray((moves.templates.size + 63) ushr 6)

	private val ordered = LongArray(moves.templates.size)

	private val bounceTemplates = moves.templates.filter { it.movement == MovementId.BOUNCE }

	private val bouncesFrom = Long2ObjectOpenHashMap<StanceEdges>()

	private val bouncesTo = Long2ObjectOpenHashMap<StanceEdges>()

	fun edgesFrom(node: Long): List<CoarseEdge> {
		val key = PackedStance.stanceKey(node)
		return outgoing.get(key) ?: run {
			val stance = PackedStance.stance(key)
			check(key, stance)
			val edges = if (!standable.isStance(stance)) emptyList() else moves.templates(view, stance).mapNotNull { stamper.edge(it, stance) }
			outgoing.put(key, edges)
			edges
		}
	}

	fun edgesFrom(origin: Stance): List<CoarseEdge> = edgesFrom(key(origin))

	fun cachedEdgesFrom(origin: Stance): List<CoarseEdge>? = outgoing.get(key(origin))

	fun forEachEdgeFrom(node: Long, visitor: EdgeVisitor) {
		val key = PackedStance.stanceKey(node)
		val origin = PackedStance.stance(key)
		check(key, origin)
		if (!standable.isStance(origin)) return
		val row = row(originRows, origin.x shr 6, origin.y, origin.z)
		val bit = origin.x and 63
		val mask = 1L shl bit
		var count = 0
		for (index in 0 until row.size) {
			if (row.edges[index] and mask != 0L) ordered[count++] = (row.templates[index].toLong() shl 32) or index.toLong()
		}
		ordered.sort(0, count)
		for (position in 0 until count) visit(row, ordered[position].toInt(), bit, visitor)
		if (bounces(origin)) visit(bouncesFrom.get(key) ?: bouncesFrom(origin).also { bouncesFrom.put(key, it) }, visitor)
	}

	fun forEachEdgeTo(node: Long, visitor: EdgeVisitor) {
		val target = PackedStance.stance(node)
		if (!standable.isStance(target)) return
		val row = row(targetRows, target.x shr 6, target.y, target.z)
		val bit = target.x and 63
		val mask = 1L shl bit
		for (index in 0 until row.size) {
			if (row.edges[index] and mask != 0L) visit(row, index, bit, visitor)
		}
		val key = PackedStance.stanceKey(node)
		if (bounces(target)) visit(bouncesTo.get(key) ?: bouncesTo(target).also { bouncesTo.put(key, it) }, visitor)
	}

	private fun bounces(stance: Stance): Boolean =
		bounceTemplates.isNotEmpty() && moves.bouncesAt(view, stance.x, stance.y, stance.z)

	private fun bouncesFrom(origin: Stance): StanceEdges = StanceEdges.of(bounceTemplates) { it.edge(view, origin) }

	private fun bouncesTo(target: Stance): StanceEdges = StanceEdges.of(bounceTemplates) { template ->
		val origin = target.offset(-template.dx, -template.dy, -template.dz)
		if (standable.isStance(origin)) template.edge(view, origin) else null
	}

	private fun visit(edges: StanceEdges, visitor: EdgeVisitor) {
		for (index in edges.templates.indices) visitor.visit(moves.templates[edges.templates[index]], edges.ticks[index], edges.standing[index])
	}

	private fun visit(row: RowEdges, index: Int, bit: Int, visitor: EdgeVisitor) {
		val mask = 1L shl bit
		val template = moves.templates[row.templates[index]]
		val ticks = if (row.irregular[index] and mask != 0L) row.ticksOf(row.templates[index], bit) else template.stampedTicks
		visitor.visit(template, ticks, row.standing[index] and mask != 0L)
	}

	fun isComplete(node: Long): Boolean = completeOrigins.contains(PackedStance.stanceKey(node))

	fun isComplete(origin: Stance): Boolean = completeOrigins.contains(key(origin))

	private fun check(key: Long, origin: Stance) {
		if (checkedOrigins.add(key) && !moves.readsUnknown(view, origin)) completeOrigins.add(key)
	}

	private fun row(cache: RowCache, word: Int, y: Int, z: Int): RowEdges {
		stamper.synchronize()
		return cache[word, y, z]
	}

	private fun gather(word: Int, y: Int, z: Int): RowEdges {
		val groups = moves.groups
		val base = word shl 6
		for (group in 0 until groups.size) {
			val originY = y - groups.dy[group]
			val originZ = z - groups.dz[group]
			for (originWord in ((base - groups.maxDx[group]) shr 6)..((base + 63 - groups.minDx[group]) shr 6)) {
				val origin = originRows[originWord, originY, originZ]
				var index = origin.groupStart(group, groups.of)
				while (index < origin.size && groups.of[origin.templates[index]] == group) {
					val template = origin.templates[index]
					val shift = ((originWord - word) shl 6) + moves.templates[template].dx
					val edges = shifted(origin.edges[index], shift)
					if (edges != 0L) {
						if (gathered[template * 3] == 0L) touched[template ushr 6] = touched[template ushr 6] or (1L shl template)
						gathered[template * 3] = gathered[template * 3] or edges
						gathered[template * 3 + 1] = gathered[template * 3 + 1] or shifted(origin.standing[index], shift)
						gathered[template * 3 + 2] = gathered[template * 3 + 2] or shifted(origin.irregular[index], shift)
					}
					index++
				}
			}
		}
		val builder = RowEdges.Builder()
		for (chunk in touched.indices) {
			var bits = touched[chunk]
			touched[chunk] = 0L
			while (bits != 0L) {
				val template = (chunk shl 6) + java.lang.Long.numberOfTrailingZeros(bits)
				bits = bits and (bits - 1)
				val irregular = gathered[template * 3 + 2]
				builder.add(template, gathered[template * 3], gathered[template * 3 + 1], irregular)
				if (irregular != 0L) ticksInto(builder, template, irregular, word, y, z)
				gathered.fill(0L, template * 3, template * 3 + 3)
			}
		}
		return builder.build()
	}

	private fun ticksInto(builder: RowEdges.Builder, template: Int, irregular: Long, word: Int, y: Int, z: Int) {
		val motion = moves.templates[template]
		var bits = irregular
		while (bits != 0L) {
			val bit = java.lang.Long.numberOfTrailingZeros(bits)
			bits = bits and (bits - 1)
			val originX = (word shl 6) + bit - motion.dx
			val origin = originRows[originX shr 6, y - motion.dy, z - motion.dz]
			builder.irregular(template, bit, origin.ticksOf(template, originX and 63))
		}
	}

	fun invalidateStances(stances: Iterable<Stance>) {
		for (stance in stances) {
			val k = key(stance)
			outgoing.remove(k)
			checkedOrigins.remove(k)
			completeOrigins.remove(k)
			standable.remove(stance)
		}
		stamper.invalidate()
		originRows.clear()
		targetRows.clear()
		bouncesFrom.clear()
		bouncesTo.clear()
	}

	fun invalidate(changed: List<CellBox>, arrivalsOnly: Boolean) {
		if (changed.isEmpty()) return
		val origins = changed.map(moves::originsReading)
		val targets = changed.map(moves::targetsReading)
		val kept = if (arrivalsOnly) completeOrigins else null
		remove(outgoing.keys, origins, kept)
		remove(checkedOrigins, origins, kept)
		if (!arrivalsOnly) remove(completeOrigins, origins)
		standable.remove(targets)
		stamper.invalidate()
		originRows.remove(origins)
		targetRows.remove(targets)
		remove(bouncesFrom.keys, origins, kept)
		remove(bouncesTo.keys, targets)
	}

	private fun remove(keys: LongSet, boxes: List<CellBox>, keep: LongSet? = null) {
		val iterator = keys.iterator()
		while (iterator.hasNext()) {
			val k = iterator.nextLong()
			if (keep != null && keep.contains(k)) continue
			if (covers(boxes, k)) iterator.remove()
		}
	}

	private class StanceEdges(val templates: IntArray, val ticks: DoubleArray, val standing: BooleanArray) {
		companion object {
			inline fun of(templates: List<MotionTemplate>, edge: (MotionTemplate) -> CoarseEdge?): StanceEdges {
				val found = templates.mapNotNull { template -> edge(template)?.let { template to it } }
				return StanceEdges(
					IntArray(found.size) { found[it].first.id.value },
					DoubleArray(found.size) { found[it].second.lowerBoundTicks },
					BooleanArray(found.size) { found[it].second.standingStart },
				)
			}
		}
	}

	private class RowCache(private val build: (Int, Int, Int) -> RowEdges) {
		private val blocks = Long2ObjectOpenHashMap<Array<RowEdges?>>()
		private val slotKeys = LongArray(SLOTS)
		private val slotBlocks = arrayOfNulls<Array<RowEdges?>>(SLOTS)

		operator fun get(word: Int, y: Int, z: Int): RowEdges {
			val block = block(word, y shr 4, z shr 4)
			val index = ((y and 15) shl 4) or (z and 15)
			return block[index] ?: build(word, y, z).also { block[index] = it }
		}

		private fun block(word: Int, sectionY: Int, sectionZ: Int): Array<RowEdges?> {
			val key = ChunkSectionPos.asLong(word, sectionY, sectionZ)
			val slot = (HashCommon.mix(key) and (SLOTS - 1).toLong()).toInt()
			val cached = slotBlocks[slot]
			if (cached != null && slotKeys[slot] == key) return cached
			val block = blocks.get(key) ?: arrayOfNulls<RowEdges>(ROWS).also {
				if (blocks.size >= CAPACITY) clear()
				blocks.put(key, it)
			}
			slotKeys[slot] = key
			slotBlocks[slot] = block
			return block
		}

		fun clear() {
			blocks.clear()
			slotBlocks.fill(null)
		}

		fun remove(boxes: List<CellBox>) {
			for (entry in blocks.long2ObjectEntrySet()) {
				val minX = ChunkSectionPos.unpackX(entry.longKey) shl 6
				val minY = ChunkSectionPos.unpackY(entry.longKey) shl 4
				val minZ = ChunkSectionPos.unpackZ(entry.longKey) shl 4
				for (box in boxes) {
					if (!box.overlaps(minX, minY, minZ, minX + 63, minY + 15, minZ + 15)) continue
					for (y in maxOf(box.ys.first, minY)..minOf(box.ys.last, minY + 15)) {
						for (z in maxOf(box.zs.first, minZ)..minOf(box.zs.last, minZ + 15)) entry.value[((y and 15) shl 4) or (z and 15)] = null
					}
				}
			}
		}

		private companion object {
			const val ROWS = 256
			const val SLOTS = 1_024
			const val CAPACITY = 1 shl 12
		}
	}

	companion object {
		private fun key(stance: Stance): Long = PackedStance.pack(stance, SpeedClass.STOPPED)

		private fun shifted(mask: Long, by: Int): Long = when {
			by >= 64 || by <= -64 -> 0L
			by >= 0 -> mask shl by
			else -> mask ushr -by
		}

		fun covers(boxes: List<CellBox>, key: Long): Boolean {
			val x = PackedStance.unpackX(key)
			val y = PackedStance.unpackY(key)
			val z = PackedStance.unpackZ(key)
			return boxes.any { it.contains(x, y, z) }
		}
	}
}
