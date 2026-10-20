package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.families.DiveMovement
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.util.math.BlockPos

internal fun interface EdgeVisitor {
	fun visit(template: MotionTemplate, ticks: Double, standingStart: Boolean)
}

internal fun interface PendingFilter {
	fun wanted(template: MotionTemplate): Boolean
}

internal fun interface FallVisitor {
	fun visit(origin: Stance, edge: FallEdge)
}

internal fun interface ChainVisitor {
	fun visit(origin: Stance, edge: ChainEdge)
}

internal class CoarseEdgeCache(
	private val view: CoarseVoxelView,
	private val moves: SimpleMoveLibrary,
	goals: List<Stance> = emptyList(),
) {
	private val outgoing = Long2ObjectLinkedOpenHashMap<List<CoarseEdge>>()

	private val checkedOrigins = LongOpenHashSet()

	private val completeOrigins = LongOpenHashSet()

	private val standable = StanceMemo(view, moves, goals)

	private val stamper = EdgeStamper(view, moves, standable)

	private val originRows = RowCache(stamper::row)

	private val targetRows = RowCache(::gather)

	private val gathered = LongArray(moves.templates.size * 4)

	private val touched = LongArray((moves.templates.size + 63) ushr 6)

	private val bounceTemplates = moves.templates.filter { it.movement == MovementId.BOUNCE }

	private val diveTemplates = moves.templates.filter { it.movement == MovementId.DIVE }

	private val bouncesFrom = Long2ObjectOpenHashMap<StanceEdges>()

	private val bouncesTo = Long2ObjectOpenHashMap<StanceEdges>()

	private val pads = BouncePads(view, moves.bounceIndex)

	private val chains = if (bounceTemplates.isEmpty()) null else BounceChains(view, standable, pads, moves.catalog.options.maxBounceDrop, bends = moves.catalog.options.allowOffAxisJumps)

	private val falls = if (moves.falls) FallEdges(view, moves, standable) else null

	private val shafts = moves.templates.firstOrNull { it.movement == MovementId.DIVE && it.dx == 0 && it.dy == -1 && it.dz == 0 }
		?.let { WaterShafts(view, standable, it.lowerBoundTicks) }

	var exactChecks = 0L
		private set

	var arrivalChecks = 0L
		private set

	fun isStance(stance: Stance): Boolean = standable.isStance(stance)

	fun floorSlowdown(x: Int, y: Int, z: Int): Double = CoarseMoveRates.floorSlowdown(view.voxel(x, y - 1, z).velocityMultiplier)

	fun edgesFrom(node: Long): List<CoarseEdge> {
		val key = PackedStance.stanceKey(node)
		return outgoing.get(key) ?: run {
			val stance = PackedStance.stance(key)
			check(key, stance)
			val edges = if (!standable.isStance(stance)) {
				if (shafts != null && DiveMovement.openWater(view, stance.x, stance.y, stance.z)) openWaterEdges(shafts, stance) else emptyList()
			} else {
				val slowdown = floorSlowdown(stance.x, stance.y, stance.z)
				val stamped = stampedEdgesFrom(stance).let { edges ->
					if (slowdown == 1.0) edges else edges.map { it.copy(lowerBoundTicks = it.lowerBoundTicks * slowdown) }
				}
				val falling = falls
				val dropped = falling?.from(stance)
				val landed = if (falling == null || dropped.isNullOrEmpty()) stamped else stamped + dropped.map { falling.coarseEdge(stance, it) }
				val sunk = shafts?.edgesFrom(stance).orEmpty()
				val chained = chains?.let { chains -> chains.from(stance).map { chains.coarseEdge(stance, it) } }.orEmpty()
				if (sunk.isEmpty() && chained.isEmpty()) landed else landed + sunk + chained
			}
			if (outgoing.size >= CACHED_EDGE_LISTS) outgoing.removeFirst()
			outgoing.put(key, edges)
			edges
		}
	}

	fun edgesFrom(origin: Stance): List<CoarseEdge> = edgesFrom(key(origin))

	private fun openWaterEdges(shafts: WaterShafts, origin: Stance): List<CoarseEdge> =
		shafts.edgesFromOpenWater(origin) + diveTemplates.mapNotNull { template -> template.edge(view, origin)?.takeIf { standable.isStance(it.to) } }

	fun cachedEdgesFrom(origin: Stance): List<CoarseEdge>? = outgoing.get(key(origin))

	fun forEachEdgeFrom(node: Long, pending: PendingFilter? = null, visitor: EdgeVisitor) {
		val key = PackedStance.stanceKey(node)
		val origin = PackedStance.stance(key)
		check(key, origin)
		if (!standable.isStance(origin)) return
		val row = row(originRows, origin.x shr 6, origin.y, origin.z)
		val bit = origin.x and 63
		val lane = row.lane(bit)
		if (lane >= 0) for (position in 0 until row.size) {
			val index = row.byTemplate(position)
			if (row.isPending(index, lane)) {
				if (pending != null && !pending.wanted(moves.templates[row.template(index)])) continue
				settle(row, index, bit, origin)
			}
			if (row.hasEdge(index, lane)) visit(row, index, bit, lane, visitor, origin)
		}
		if (bounces(origin)) visit(bouncesFrom.get(key) ?: bouncesFrom(origin).also { bouncesFrom.put(key, it) }, visitor)
	}

	fun settleFrom(node: Long, template: Int, visitor: EdgeVisitor) {
		val origin = PackedStance.stance(PackedStance.stanceKey(node))
		val row = row(originRows, origin.x shr 6, origin.y, origin.z)
		val bit = origin.x and 63
		val lane = row.lane(bit)
		if (lane < 0) return
		val index = row.indexOf(template, moves.groups.rank)
		if (index < 0) return
		if (row.isPending(index, lane)) settle(row, index, bit, origin)
		if (row.hasEdge(index, lane)) visit(row, index, bit, lane, visitor, origin)
	}

	fun forEachStance(word: Int, y: Int, z: Int, lanes: Long, action: (Int) -> Unit) {
		stamper.synchronize()
		var bits = stamper.candidates(word, y, z) and lanes
		while (bits != 0L) {
			val x = (word shl 6) + java.lang.Long.numberOfTrailingZeros(bits)
			bits = bits and (bits - 1)
			if (standable.isStance(Stance(x, y, z))) action(x)
		}
	}

	fun forEachSettledEdgeFrom(node: Long, visitor: EdgeVisitor) {
		val key = PackedStance.stanceKey(node)
		val origin = PackedStance.stance(key)
		val row = originRows.peek(origin.x shr 6, origin.y, origin.z) ?: return
		val bit = origin.x and 63
		val lane = row.lane(bit)
		if (lane >= 0) for (index in 0 until row.size) if (row.hasEdge(index, lane)) visit(row, index, bit, lane, visitor, origin)
		bouncesFrom.get(key)?.let { visit(it, visitor) }
	}

	fun forEachEdgeTo(node: Long, pending: PendingFilter? = null, visitor: EdgeVisitor) {
		val target = PackedStance.stance(node)
		if (!standable.isStance(target)) return
		val row = row(targetRows, target.x shr 6, target.y, target.z)
		val bit = target.x and 63
		val lane = row.lane(bit)
		if (lane >= 0) for (index in 0 until row.size) {
			if (row.isPending(index, lane)) {
				if (pending != null && !pending.wanted(moves.templates[row.template(index)])) continue
				settleArrival(row, index, bit, target)
			}
			if (row.hasEdge(index, lane)) visit(row, index, bit, lane, visitor)
		}
		val key = PackedStance.stanceKey(node)
		if (bounces(target)) visit(bouncesTo.get(key) ?: bouncesTo(target).also { bouncesTo.put(key, it) }, visitor)
	}

	fun forEachFallFrom(node: Long, visitor: FallVisitor) {
		val falls = falls ?: return
		val origin = PackedStance.stance(node)
		if (!standable.isStance(origin)) return
		for (edge in falls.from(origin)) visitor.visit(origin, edge)
	}

	fun forEachFallTo(node: Long, visitor: FallVisitor) {
		val falls = falls ?: return
		val target = PackedStance.stance(node)
		val water = view.medium(target.x, target.y, target.z) == Medium.WATER
		if (water != (PackedStance.speed(node) == SpeedClass.STOPPED)) return
		for (arrival in falls.to(target)) visitor.visit(arrival.origin, arrival.edge)
	}

	fun forEachChainFrom(node: Long, visitor: ChainVisitor) {
		val chains = chains ?: return
		val origin = PackedStance.stance(node)
		if (!standable.isStance(origin)) return
		for (edge in chains.from(origin)) visitor.visit(origin, edge)
	}

	fun forEachChainTo(node: Long, visitor: ChainVisitor) {
		val chains = chains ?: return
		val target = PackedStance.stance(node)
		if (!standable.isStance(target)) return
		for (arrival in chains.to(target)) visitor.visit(arrival.origin, arrival.edge)
	}

	fun forEachShaft(node: Long, visitor: ShaftVisitor) {
		val shafts = shafts ?: return
		val stance = PackedStance.stance(node)
		if (!standable.isStance(stance)) return
		shafts.forEachFrom(stance, visitor)
	}

	fun shaftReaders(changed: List<CellBox>): List<CellBox> = if (shafts == null) emptyList() else changed.map(WaterShafts::readers)

	fun fallsRead(readers: List<CellBox>, key: Long): Boolean = falls?.reads(readers, key) == true

	fun fallReaders(changed: List<CellBox>): List<CellBox> = falls?.let { changed.map(it::readers) }.orEmpty()

	fun chainsRead(changed: List<CellBox>, key: Long): Boolean = chains?.reads(changed, key) == true

	fun chainReaders(changed: List<CellBox>): List<CellBox> = chains?.let { changed.map(it::readers) }.orEmpty()

	private fun bounces(stance: Stance): Boolean =
		bounceTemplates.isNotEmpty() && moves.bouncesAt(view, stance.x, stance.y, stance.z)

	private fun stampedEdgesFrom(origin: Stance): List<CoarseEdge> {
		val departures = if (bounces(origin)) pads.departures(origin) else NO_TEMPLATES
		val edges = ArrayList<CoarseEdge>()
		var next = 0
		for (template in moves.templatesWithoutBounces) {
			while (next < departures.size && departures[next] < template.id.value) moves.templates[departures[next++]].edge(view, origin)?.let(edges::add)
			stamper.edge(template, origin)?.takeIf(::fieldTarget)?.let(edges::add)
		}
		while (next < departures.size) moves.templates[departures[next++]].edge(view, origin)?.let(edges::add)
		return edges
	}

	private fun fieldTarget(edge: CoarseEdge): Boolean =
		edge.movement !in WATER_TARGETED || standable.isStance(edge.to) || DiveMovement.openWater(view, edge.to.x, edge.to.y, edge.to.z)

	private fun bouncesFrom(origin: Stance): StanceEdges = StanceEdges.of(pads.departures(origin).map { moves.templates[it] }) { it.edge(view, origin) }

	private fun bouncesTo(target: Stance): StanceEdges = StanceEdges.of(pads.arrivals(target).map { moves.templates[it] }) { template ->
		val origin = target.offset(-template.dx, -template.dy, -template.dz)
		if (standable.isStance(origin)) template.edge(view, origin) else null
	}

	private fun settle(row: RowEdges, index: Int, bit: Int, origin: Stance) {
		exactChecks++
		val edge = moves.templates[row.template(index)].edge(view, origin)
		row.resolve(index, bit, edge != null, edge?.standingStart == true, edge?.lowerBoundTicks ?: Double.NaN)
	}

	private fun settleArrival(row: RowEdges, index: Int, bit: Int, target: Stance) {
		arrivalChecks++
		val template = row.template(index)
		val motion = moves.templates[template]
		val origin = target.offset(-motion.dx, -motion.dy, -motion.dz)
		val origins = row(originRows, origin.x shr 6, origin.y, origin.z)
		val position = origins.indexOf(template, moves.groups.rank)
		if (position < 0) return row.resolve(index, bit, found = false, standingStart = false, irregularTicks = Double.NaN)
		val originBit = origin.x and 63
		val originLane = origins.lane(originBit)
		if (origins.isPending(position, originLane)) settle(origins, position, originBit, origin)
		val found = origins.hasEdge(position, originLane)
		val ticks = if (found && origins.isIrregular(position, originLane)) origins.ticksOf(template, originBit) else Double.NaN
		row.resolve(index, bit, found, origins.isStanding(position, originLane), ticks)
	}

	private fun visit(edges: StanceEdges, visitor: EdgeVisitor) {
		for (index in edges.templates.indices) visitor.visit(moves.templates[edges.templates[index]], edges.ticks[index], edges.standing[index])
	}

	private fun visit(row: RowEdges, index: Int, bit: Int, lane: Int, visitor: EdgeVisitor, origin: Stance? = null) {
		val id = row.template(index)
		val template = moves.templates[id]
		if (origin != null && !standable.isStance(origin.offset(template.dx, template.dy, template.dz))) return
		val ticks = if (row.isIrregular(index, lane)) row.ticksOf(id, bit) else template.stampedTicks
		visitor.visit(template, ticks, row.isStanding(index, lane))
	}

	fun isComplete(node: Long): Boolean = completeOrigins.contains(PackedStance.stanceKey(node))

	private fun check(key: Long, origin: Stance) {
		if (view.journal == null && checkedOrigins.add(key) && !moves.readsUnknown(view, origin)) completeOrigins.add(key)
	}

	private fun row(cache: RowCache, word: Int, y: Int, z: Int): RowEdges {
		stamper.synchronize()
		return cache[word, y, z]
	}

	private fun gather(word: Int, y: Int, z: Int): RowEdges {
		val stances = stamper.candidates(word, y, z)
		if (stances == 0L) return RowEdges.EMPTY
		val groups = moves.groups
		val builder = RowEdges.Builder()
		val base = word shl 6
		for (group in 0 until groups.size) {
			val originY = y - groups.dy[group]
			val originZ = z - groups.dz[group]
			for (originWord in ((base - groups.maxDx[group]) shr 6)..((base + 63 - groups.minDx[group]) shr 6)) {
				val origin = originRows[originWord, originY, originZ]
				if (origin.size == 0) continue
				var index = origin.groupStart(group)
				val end = origin.groupStart(group + 1)
				while (index < end) {
					val template = origin.template(index)
					val shift = ((originWord - word) shl 6) + moves.templates[template].dx
					val edges = shifted(origin.edges(index), shift) and stances
					val pending = shifted(origin.pending(index), shift) and stances
					if (edges != 0L || pending != 0L) {
						touched[template ushr 6] = touched[template ushr 6] or (1L shl template)
						val irregular = shifted(origin.irregular(index), shift) and stances
						gathered[template * 4] = gathered[template * 4] or edges
						gathered[template * 4 + 1] = gathered[template * 4 + 1] or (shifted(origin.standing(index), shift) and stances)
						gathered[template * 4 + 2] = gathered[template * 4 + 2] or irregular
						gathered[template * 4 + 3] = gathered[template * 4 + 3] or pending
						var bits = irregular
						val start = if (bits != 0L) origin.irregularStart(template) else 0
						while (bits != 0L) {
							val bit = java.lang.Long.numberOfTrailingZeros(bits)
							bits = bits and (bits - 1)
							builder.irregular(template, bit, origin.ticksOf(template, bit - shift, start))
						}
					}
					index++
				}
			}
		}
		for (chunk in touched.indices) {
			var bits = touched[chunk]
			touched[chunk] = 0L
			while (bits != 0L) {
				val template = (chunk shl 6) + java.lang.Long.numberOfTrailingZeros(bits)
				bits = bits and (bits - 1)
				builder.add(template, gathered[template * 4], gathered[template * 4 + 1], gathered[template * 4 + 2], gathered[template * 4 + 3])
				gathered.fill(0L, template * 4, template * 4 + 4)
			}
		}
		return builder.build()
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
		pads.clear()
		falls?.clear()
		chains?.clear()
		shafts?.clear()
	}

	fun invalidate(changed: List<CellBox>, arrivalsOnly: Boolean) {
		if (changed.isEmpty()) return
		val origins = changed.map { moves.originsReading(it, view) }
		val targets = changed.map { moves.targetsReading(it, view) }
		val kept = if (arrivalsOnly) completeOrigins else null
		remove(outgoing.keys, origins, kept)
		if (shafts != null) remove(outgoing.keys, shaftReaders(changed))
		remove(checkedOrigins, origins, kept)
		if (!arrivalsOnly) remove(completeOrigins, origins)
		standable.remove(targets)
		originRows.remove(origins)
		targetRows.remove(targets)
		remove(bouncesFrom.keys, origins, kept)
		remove(bouncesTo.keys, targets)
		chains?.invalidate(changed)
		if (view.journal != null) return
		stamper.invalidate()
		pads.clear()
		falls?.clear()
		shafts?.clear()
	}

	private fun remove(keys: LongSet, boxes: List<CellBox>, keep: LongSet? = null) {
		val bounds = CellBox.enclosing(boxes)
		val iterator = keys.iterator()
		while (iterator.hasNext()) {
			val k = iterator.nextLong()
			if (!bounds.contains(PackedStance.unpackX(k), PackedStance.unpackY(k), PackedStance.unpackZ(k))) continue
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
		private val rows = Long2ObjectLinkedOpenHashMap<RowEdges>()
		private var bytes = 0L

		operator fun get(word: Int, y: Int, z: Int): RowEdges {
			val key = BlockPos.asLong(word, y, z)
			rows.get(key)?.let { cached ->
				if (cached !== RowEdges.EMPTY) cached.referenced = true
				return cached
			}
			val row = build(word, y, z)
			rows.putAndMoveToLast(key, row)
			bytes += row.bytes
			while (bytes > BUDGET_BYTES && rows.size > 1) {
				val oldest = rows.firstLongKey()
				val candidate = rows.get(oldest)
				if (candidate.referenced) {
					candidate.referenced = false
					rows.getAndMoveToLast(oldest)
				} else {
					bytes -= rows.removeFirst().bytes
				}
			}
			return row
		}

		fun peek(word: Int, y: Int, z: Int): RowEdges? = rows.get(BlockPos.asLong(word, y, z))

		fun clear() {
			rows.clear()
			bytes = 0L
		}

		fun remove(boxes: List<CellBox>) {
			if (boxes.sumOf(::rowCount) <= rows.size) {
				for (box in boxes) for (z in box.zs) for (y in maxOf(box.ys.first, MIN_ROW_Y)..minOf(box.ys.last, MAX_ROW_Y)) {
					for (word in (box.xs.first shr 6)..(box.xs.last shr 6)) rows.remove(BlockPos.asLong(word, y, z))?.let { bytes -= it.bytes }
				}
				return
			}
			val iterator = rows.long2ObjectEntrySet().fastIterator()
			while (iterator.hasNext()) {
				val entry = iterator.next()
				val minX = BlockPos.unpackLongX(entry.longKey) shl 6
				val y = BlockPos.unpackLongY(entry.longKey)
				val z = BlockPos.unpackLongZ(entry.longKey)
				if (boxes.none { it.overlaps(minX, y, z, minX + 63, y, z) }) continue
				bytes -= entry.value.bytes
				iterator.remove()
			}
		}

		private fun rowCount(box: CellBox): Long {
			val ys = minOf(box.ys.last, MAX_ROW_Y).toLong() - maxOf(box.ys.first, MIN_ROW_Y) + 1
			return if (ys <= 0) 0L else ys * (box.zs.last.toLong() - box.zs.first + 1) * ((box.xs.last shr 6) - (box.xs.first shr 6) + 1)
		}

		private companion object {
			const val BUDGET_BYTES = 128L shl 20

			const val MIN_ROW_Y = -2048

			const val MAX_ROW_Y = 2047
		}
	}


	companion object {
		private const val CACHED_EDGE_LISTS = 1 shl 10

		private val WATER_TARGETED = setOf(MovementId.DIVE, MovementId.ENTER_WATER)

		private val NO_TEMPLATES = IntArray(0)

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
