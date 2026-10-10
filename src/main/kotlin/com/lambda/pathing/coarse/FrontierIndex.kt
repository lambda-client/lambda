package com.lambda.pathing.coarse

import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.ChunkSectionPos
import kotlin.math.hypot

internal class FrontierIndex(
	private val view: CoarseVoxelView,
	private val moves: SimpleMoveLibrary,
	private val goal: Stance,
	private val onChange: (Stance) -> Unit,
) {
	private val costs = Long2DoubleOpenHashMap()
	private val bySection = Long2ObjectOpenHashMap<LongOpenHashSet>()
	private var refused = false

	val size: Int get() = costs.size

	operator fun contains(stance: Stance): Boolean = costs.containsKey(PackedStance.pack(stance, SpeedClass.STOPPED))

	fun cost(node: Long): Double = costs.getOrDefault(PackedStance.stanceKey(node), Double.NaN)

	inline fun forEach(action: (Stance, Double) -> Unit) {
		val iterator = entries().fastIterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			action(PackedStance.stance(entry.longKey), entry.doubleValue)
		}
	}

	@PublishedApi
	internal fun entries() = costs.long2DoubleEntrySet()

	fun rebuild() {
		for (key in costs.keys.toLongArray()) retire(key)
		refused = FrontierAnchors.refusesOptimism(view, moves, goal)
		if (!refused) view.forEachKnownSection { section -> refreshSection(section, EVERYWHERE) }
	}

	fun refresh(changed: List<CellBox>) {
		if (FrontierAnchors.refusesOptimism(view, moves, goal) != refused || changed.any(::spansWorld)) return rebuild()
		if (!refused) changed.forEach(::refreshAround)
	}

	private fun spansWorld(box: CellBox): Boolean =
		box.xs.last - box.xs.first > REBUILD_SPAN_BLOCKS || box.zs.last - box.zs.first > REBUILD_SPAN_BLOCKS

	private fun refreshAround(box: CellBox) {
		val stanceY = view.simulableStanceY
		val area = CellBox(
			box.xs.first - 1..box.xs.last + 1,
			maxOf(box.ys.first - 1, stanceY.first)..minOf(box.ys.last + 2, stanceY.last),
			box.zs.first - 1..box.zs.last + 1,
		)
		if (area.ys.isEmpty()) return
		for (sx in (area.xs.first shr 4)..(area.xs.last shr 4)) for (sz in (area.zs.first shr 4)..(area.zs.last shr 4)) {
			for (sy in (area.ys.first shr 4)..(area.ys.last shr 4)) refreshSection(ChunkSectionPos.asLong(sx, sy, sz), area)
		}
	}

	private fun refreshSection(section: Long, area: CellBox) {
		bySection.get(section)?.toLongArray()?.forEach { key ->
			val x = PackedStance.unpackX(key)
			val y = PackedStance.unpackY(key)
			val z = PackedStance.unpackZ(key)
			if (area.contains(x, y, z)) evaluate(x, y, z)
		}
		val baseX = ChunkSectionPos.unpackX(section) shl 4
		val baseY = ChunkSectionPos.unpackY(section) shl 4
		val baseZ = ChunkSectionPos.unpackZ(section) shl 4
		val ys = maxOf(baseY, view.simulableStanceY.first, area.ys.first)..minOf(baseY + 15, view.simulableStanceY.last, area.ys.last)
		if (ys.isEmpty() || !view.isKnown(baseX, ys.first, baseZ)) return
		val xs = maxOf(baseX, area.xs.first)..minOf(baseX + 15, area.xs.last)
		val zs = maxOf(baseZ, area.zs.first)..minOf(baseZ + 15, area.zs.last)
		if (baseX + 15 in xs && view.isUnknown(baseX + 16, ys.first, baseZ)) for (y in ys) for (z in zs) evaluate(baseX + 15, y, z)
		if (baseX in xs && view.isUnknown(baseX - 1, ys.first, baseZ)) for (y in ys) for (z in zs) evaluate(baseX, y, z)
		if (baseZ + 15 in zs && view.isUnknown(baseX, ys.first, baseZ + 16)) for (y in ys) for (x in xs) evaluate(x, y, baseZ + 15)
		if (baseZ in zs && view.isUnknown(baseX, ys.first, baseZ - 1)) for (y in ys) for (x in xs) evaluate(x, y, baseZ)
	}

	private fun evaluate(x: Int, y: Int, z: Int) {
		val key = PackedStance.pack(x, y, z, SpeedClass.STOPPED)
		val anchored = costs.containsKey(key)
		val stance = Stance(x, y, z)
		val anchors = (view.isUnknown(x + 1, y, z) || view.isUnknown(x - 1, y, z) || view.isUnknown(x, y, z + 1) || view.isUnknown(x, y, z - 1)) &&
				stance != goal && moves.isStance(view, stance)
		if (anchors && !anchored) {
			val cost = optimisticCost(stance)
			costs.put(key, cost)
			bySection.computeIfAbsent(ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)) { LongOpenHashSet() }.add(key)
			onChange(stance)
		} else if (!anchors && anchored) {
			retire(key)
		}
	}

	private fun retire(key: Long) {
		costs.remove(key)
		val section = ChunkSectionPos.asLong(PackedStance.unpackX(key) shr 4, PackedStance.unpackY(key) shr 4, PackedStance.unpackZ(key) shr 4)
		bySection.get(section)?.let { if (it.remove(key) && it.isEmpty()) bySection.remove(section) }
		onChange(PackedStance.stance(key))
	}

	private fun optimisticCost(anchor: Stance): Double {
		val realistic = hypot((goal.x - anchor.x).toDouble(), (goal.z - anchor.z).toDouble()) * moves.sustainedTicksPerBlock
		return maxOf(moves.heuristic(anchor, goal), realistic)
	}

	private companion object {
		const val REBUILD_SPAN_BLOCKS = 1024

		val EVERYWHERE = ((Int.MIN_VALUE / 2)..(Int.MAX_VALUE / 2)).let { CellBox(it, it, it) }
	}
}
