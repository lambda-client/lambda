/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.util.player.prediction

import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertEquals

class ImmutableSnapshotSectionTest {
    @Test
    fun `uniform sections need no per-cell index storage`() {
        val section = ImmutableSnapshotSection.Builder().build()

        assertEquals(1, section.paletteSize)
        assertEquals(0, section.indexStorageBytes)
        assertEquals(SnapshotBlockPhysics.AIR, section[0, 0, 0])
        assertEquals(SnapshotBlockPhysics.AIR, section[15, 15, 15])
    }

    @Test
    fun `local indexing handles negative world coordinates`() {
        val section = ImmutableSnapshotSection.Builder().apply {
            set(-1, -1, -1, SnapshotBlockPhysics.FULL_CUBE)
        }.build()

        assertEquals(SnapshotBlockPhysics.FULL_CUBE, section[-1, -1, -1])
        assertEquals(SnapshotBlockPhysics.AIR, section[-16, -16, -16])
        assertEquals(2, section.paletteSize)
        assertEquals(4096, section.indexStorageBytes)
    }

    @Test
    fun `large palettes promote indices without truncation`() {
        val physics = List(257) { index ->
            SnapshotBlockPhysics(
                collisionShape = VoxelShapes.empty(),
                slipperiness = index.toDouble(),
            )
        }
        val section = ImmutableSnapshotSection.Builder().apply {
            physics.forEachIndexed { index, value ->
                set(index and 15, (index shr 8) and 15, (index shr 4) and 15, value)
            }
        }.build()

        assertEquals(258, section.paletteSize) // The untouched cells retain the air entry.
        assertEquals(8192, section.indexStorageBytes)
        assertEquals(physics[256], section[0, 1, 0])
    }

    @Test
    fun `capture completeness checks fail closed`() {
        val builder = ImmutableSnapshotSection.Builder().apply {
            set(0, 0, 0, SnapshotBlockPhysics.FULL_CUBE)
        }

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            builder.build(expectedWrites = 2)
        }
    }
}
