package com.lambda.pathing.world.snapshot

import com.lambda.pathing.world.TerrainJournal
import it.unimi.dsi.fastutil.longs.Long2ObjectMap

data class CellEdit(val x: Int, val y: Int, val z: Int, val physics: SnapshotBlockPhysics)

internal class EditedSections(
	private val base: SectionSource,
	private val patches: Long2ObjectMap<ImmutableSnapshotSection>,
) : SectionSource {
	private class Revision(val base: Any)

	@Volatile
	private var revisionOverBase: Revision? = null

	override fun get(key: Long): ImmutableSnapshotSection? = patches[key] ?: base[key]

	override fun isKnown(key: Long): Boolean = patches.containsKey(key) || base.isKnown(key)

	override val revision: Any
		get() {
			val current = base.revision
			revisionOverBase?.takeIf { it.base === current }?.let { return it }
			return Revision(current).also { revisionOverBase = it }
		}

	override val journal: TerrainJournal? get() = base.journal

	override val size: Int get() = base.size

	override val mayBounce: Boolean get() = base.mayBounce

	override val maySoftLand: Boolean get() = base.maySoftLand

	override val mayToggle: Boolean get() = base.mayToggle

	override fun bouncyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) = base.bouncyWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override fun waterWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) = base.waterWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override fun bubblesWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) = base.bubblesWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override fun honeyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) = base.honeyWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override fun hotWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) = base.hotWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override fun softWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int) = base.softWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override fun forEachKnownKey(action: (Long) -> Unit) = base.forEachKnownKey(action)

	override fun forEachSection(action: (Long, ImmutableSnapshotSection) -> Unit) =
		base.forEachSection { key, section -> action(key, patches[key] ?: section) }
}
