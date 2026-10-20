package com.lambda.pathing.world.snapshot

import com.lambda.pathing.world.TerrainJournal
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongCollection
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.ChunkSectionPos

internal class SectionStore private constructor(
	private val regions: Long2ObjectOpenHashMap<Array<ImmutableSnapshotSection?>>,
	override val size: Int,
	val bouncySections: Int,
	val softSections: Int,
	val bubblySections: Int,
	val honeySections: Int,
) : SectionSource {
	override operator fun get(key: Long): ImmutableSnapshotSection? = regions.get(regionOf(key))?.get(slotOf(key))

	override fun isKnown(key: Long): Boolean = get(key) != null

	override val revision: Any get() = this

	override val journal: TerrainJournal? get() = null

	override val mayBounce: Boolean get() = bouncySections > 0

	override val maySoftLand: Boolean get() = softSections > 0

	override val mayToggle: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
		var toggling = false
		forEach { _, section -> if (section.toggling) toggling = true }
		toggling
	}

	override fun forEachKnownKey(action: (Long) -> Unit) = forEach { key, _ -> action(key) }

	override fun forEachSection(action: (Long, ImmutableSnapshotSection) -> Unit) = forEach(action)

	override fun bouncyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		bouncySections > 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.bouncy }

	override fun waterWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.watery }

	override fun bubblesWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		bubblySections > 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.bubbly }

	override fun honeyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		honeySections > 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.honeyed }

	override fun hotWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.hot }

	override fun softWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		softSections > 0 && anyWithin(minX, minY, minZ, maxX, maxY, maxZ) { it.soft }

	private inline fun anyWithin(
		minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int,
		test: (ImmutableSnapshotSection) -> Boolean,
	): Boolean {
		for (x in (minX shr 4)..(maxX shr 4)) for (z in (minZ shr 4)..(maxZ shr 4)) for (y in (minY shr 4)..(maxY shr 4)) {
			if (get(ChunkSectionPos.asLong(x, y, z))?.let(test) == true) return true
		}
		return false
	}

	fun with(key: Long, section: ImmutableSnapshotSection): SectionStore =
		with(Long2ObjectOpenHashMap<ImmutableSnapshotSection>(1).apply { put(key, section) })

	fun with(sections: Long2ObjectMap<ImmutableSnapshotSection>): SectionStore {
		if (sections.isEmpty()) return this
		val copy = regions.clone()
		val copied = LongOpenHashSet()
		var size = size
		var bouncy = bouncySections
		var soft = softSections
		var bubbly = bubblySections
		var honey = honeySections
		val iterator = sections.long2ObjectEntrySet().iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			val regionKey = regionOf(entry.longKey)
			val region = if (copied.add(regionKey)) {
				(copy.get(regionKey)?.copyOf() ?: arrayOfNulls<ImmutableSnapshotSection>(REGION_SLOTS)).also { copy.put(regionKey, it) }
			} else copy.get(regionKey)!!
			val slot = slotOf(entry.longKey)
			val replaced = region[slot]
			region[slot] = entry.value
			if (replaced == null) size++
			bouncy += entry.value.bouncyCount() - replaced.bouncyCount()
			soft += entry.value.softCount() - replaced.softCount()
			bubbly += entry.value.bubblyCount() - replaced.bubblyCount()
			honey += entry.value.honeyCount() - replaced.honeyCount()
		}
		return SectionStore(copy, size, bouncy, soft, bubbly, honey)
	}

	fun without(keys: LongCollection): SectionStore {
		if (keys.isEmpty()) return this
		val copy = regions.clone()
		val copied = LongOpenHashSet()
		var size = size
		var bouncy = bouncySections
		var soft = softSections
		var bubbly = bubblySections
		var honey = honeySections
		val iterator = keys.iterator()
		while (iterator.hasNext()) {
			val key = iterator.nextLong()
			val regionKey = regionOf(key)
			val existing = copy.get(regionKey) ?: continue
			val region = if (copied.add(regionKey)) existing.copyOf().also { copy.put(regionKey, it) } else existing
			val slot = slotOf(key)
			val removed = region[slot] ?: continue
			region[slot] = null
			size--
			bouncy -= removed.bouncyCount()
			soft -= removed.softCount()
			bubbly -= removed.bubblyCount()
			honey -= removed.honeyCount()
		}
		val emptied = copied.iterator()
		while (emptied.hasNext()) {
			val regionKey = emptied.nextLong()
			if (copy.get(regionKey)?.all { it == null } == true) copy.remove(regionKey)
		}
		return SectionStore(copy, size, bouncy, soft, bubbly, honey)
	}

	inline fun forEach(action: (key: Long, section: ImmutableSnapshotSection) -> Unit) {
		val iterator = regionEntries().fastIterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			val region = entry.value
			for (slot in region.indices) {
				val section = region[slot] ?: continue
				action(keyOf(entry.longKey, slot), section)
			}
		}
	}

	@PublishedApi
	internal fun regionEntries() = regions.long2ObjectEntrySet()

	companion object {
		private const val X_SHIFT = 42

		private const val Z_SHIFT = 20

		const val REGION_SLOTS = 128

		private const val REGION_MASK = ((3L shl X_SHIFT) or (3L shl Z_SHIFT) or 7L).inv()

		val EMPTY = SectionStore(Long2ObjectOpenHashMap(), 0, 0, 0, 0, 0)

		fun of(sections: Map<Long, ImmutableSnapshotSection>): SectionStore =
			EMPTY.with(Long2ObjectOpenHashMap<ImmutableSnapshotSection>(sections.size).also { it.putAll(sections) })

		fun regionOf(key: Long): Long = key and REGION_MASK

		fun slotOf(key: Long): Int = (((key ushr X_SHIFT) and 3L) or (((key ushr Z_SHIFT) and 3L) shl 2) or ((key and 7L) shl 4)).toInt()

		@PublishedApi
		internal fun keyOf(region: Long, slot: Int): Long =
			region or ((slot and 3).toLong() shl X_SHIFT) or (((slot ushr 2) and 3).toLong() shl Z_SHIFT) or (slot ushr 4).toLong()

		private fun ImmutableSnapshotSection?.bouncyCount(): Int = if (this?.bouncy == true) 1 else 0

		private fun ImmutableSnapshotSection?.softCount(): Int = if (this?.soft == true) 1 else 0

		private fun ImmutableSnapshotSection?.bubblyCount(): Int = if (this?.bubbly == true) 1 else 0

		private fun ImmutableSnapshotSection?.honeyCount(): Int = if (this?.honeyed == true) 1 else 0
	}
}
