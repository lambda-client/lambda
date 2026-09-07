/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.SectionCapture
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SectionCaptureTest {
    @Test
    fun `cursor walks x fastest, then z, then y`() {
        val capture = SectionCapture()
        capture.begin(0L)
        val visited = ArrayList<Triple<Int, Int, Int>>()
        var completed = false
        var writes = 0
        while (!completed) {
            visited += Triple(capture.localX, capture.localY, capture.localZ)
            completed = capture.writeNext(SnapshotBlockPhysics.AIR)
            writes++
        }
        assertEquals(SectionCapture.SECTION_CELLS, writes)
        val expected = ArrayList<Triple<Int, Int, Int>>()
        for (y in 0..15) for (z in 0..15) for (x in 0..15) expected += Triple(x, y, z)
        assertEquals(expected, visited)
    }

    @Test
    fun `build freezes what was written and clears the active key`() {
        val capture = SectionCapture()
        val marker = SnapshotBlockPhysics(VoxelShapes.fullCube(), slipperiness = 0.42)
        capture.begin(7L)
        assertEquals(7L, capture.activeKey)
        repeat(SectionCapture.SECTION_CELLS) { index ->
            val x = index and 15
            val z = (index shr 4) and 15
            val y = index shr 8
            val completed = capture.writeNext(if (x == 3 && y == 5 && z == 9) marker else SnapshotBlockPhysics.AIR)
            assertEquals(index == SectionCapture.SECTION_CELLS - 1, completed)
        }
        val section = capture.build()
        assertNull(capture.activeKey)
        assertEquals(marker, section[3, 5, 9])
        assertEquals(SnapshotBlockPhysics.AIR, section[4, 5, 9])
        assertEquals(2, section.paletteSize)
    }

    @Test
    fun `abandon resets the cursor and forbids further writes until begin`() {
        val capture = SectionCapture()
        capture.begin(1L)
        assertFalse(capture.writeNext(SnapshotBlockPhysics.AIR))
        assertEquals(1, capture.localX)
        capture.abandon()
        assertNull(capture.activeKey)
        assertEquals(0, capture.localX)
        assertFailsWith<IllegalStateException> { capture.writeNext(SnapshotBlockPhysics.AIR) }
        assertFailsWith<IllegalStateException> { capture.build() }
        capture.begin(2L)
        assertTrue(capture.activeKey == 2L && capture.localX == 0)
    }

    @Test
    fun `build refuses an incomplete section`() {
        val capture = SectionCapture()
        capture.begin(1L)
        capture.writeNext(SnapshotBlockPhysics.AIR)
        assertFailsWith<IllegalArgumentException> { capture.build() }
    }
}
