package com.lambda.pathing.world

import com.lambda.pathing.prediction.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics

/**
 * The in-progress section: a 16 x 16 x 16 cursor (x fastest, then z, then y) writing into
 * an [ImmutableSnapshotSection.Builder]. Single-threaded (client thread).
 */
internal class SectionCapture {
    var activeKey: Long? = null
        private set
    private var builder: ImmutableSnapshotSection.Builder? = null

    var localX = 0
        private set
    var localY = 0
        private set
    var localZ = 0
        private set

    fun begin(key: Long) {
        activeKey = key
        builder = ImmutableSnapshotSection.Builder()
        localX = 0
        localY = 0
        localZ = 0
    }

    /** Writes the cell under the cursor and advances it; true once all cells are written. */
    fun writeNext(physics: SnapshotBlockPhysics): Boolean {
        val target = checkNotNull(builder) { "SectionCapture.writeNext without begin" }
        target.set(localX, localY, localZ, physics)
        return advanceCursor()
    }

    /** Freezes the completed section and clears the cursor. */
    fun build(): ImmutableSnapshotSection {
        val frozen = checkNotNull(builder) { "SectionCapture.build without begin" }
            .build(expectedWrites = SECTION_CELLS)
        abandon()
        return frozen
    }

    fun abandon() {
        activeKey = null
        builder = null
        localX = 0
        localY = 0
        localZ = 0
    }

    private fun advanceCursor(): Boolean {
        if (localX < 15) {
            localX++
            return false
        }
        localX = 0
        if (localZ < 15) {
            localZ++
            return false
        }
        localZ = 0
        if (localY < 15) {
            localY++
            return false
        }
        return true
    }

    companion object {
        const val SECTION_CELLS = 16 * 16 * 16
    }
}
