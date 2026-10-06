package com.lambda.pathing.world

import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics

internal class SectionCapture {
	var activeKey: Long? = null
		private set
	private var builder: ImmutableSnapshotSection.Builder? = null
	private var cursor = 0

	val localX: Int get() = cursor and 15
	val localY: Int get() = cursor shr 8
	val localZ: Int get() = (cursor shr 4) and 15

	fun begin(key: Long) {
		activeKey = key
		builder = ImmutableSnapshotSection.Builder()
		cursor = 0
	}

	fun writeNext(physics: SnapshotBlockPhysics): Boolean {
		val target = checkNotNull(builder) { "SectionCapture.writeNext without begin" }
		target.set(localX, localY, localZ, physics)
		return ++cursor == SECTION_CELLS
	}

	fun build(): ImmutableSnapshotSection {
		val frozen = checkNotNull(builder) { "SectionCapture.build without begin" }
			.build(expectedWrites = SECTION_CELLS)
		abandon()
		return frozen
	}

	fun abandon() {
		activeKey = null
		builder = null
		cursor = 0
	}

	companion object {
		const val SECTION_CELLS = 16 * 16 * 16
	}
}
