package com.lambda.pathing.world.snapshot

import com.lambda.pathing.world.TerrainJournal

internal interface SectionSource {
	operator fun get(key: Long): ImmutableSnapshotSection?

	fun isKnown(key: Long): Boolean

	val revision: Any

	val journal: TerrainJournal?

	val size: Int

	val mayBounce: Boolean

	val maySoftLand: Boolean

	fun bouncyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean

	fun waterWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean

	fun bubblesWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean

	fun honeyWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean

	fun hotWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean

	fun softWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean

	fun forEachKnownKey(action: (Long) -> Unit)

	fun forEachSection(action: (Long, ImmutableSnapshotSection) -> Unit)
}
