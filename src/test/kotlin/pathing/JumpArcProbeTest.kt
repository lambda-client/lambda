/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.coarse.JumpArcProbe
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.VoxelPos
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Phase 1 mask-fidelity gates (C1): the swept player box against *real* captured
 * shapes. Full cubes are the easy case; the exit gate is slabs, fences, and stairs --
 * partial shapes the boolean trait mask could only round to passable or blocked.
 */
class JumpArcProbeTest {
    @Test
    fun `an open flat span-4 jump is reachable with a sprint hint`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(4, 0, 0)

        val result = assertNotNull(
            JumpArcProbe.probe(world, Stance(0, 1, 0), 4, 0, rise = 0),
            "an open span-4 flat jump must be proposed",
        )
        assertTrue(result.solution.sprint, "span 4 is beyond any walking reach; the hint must say sprint")
        assertTrue(result.reads.isNotEmpty(), "the sweep must publish its shape reads")
    }

    @Test
    fun `a full block at the apex head blocks the arc`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(4, 0, 0)
        world.fullCube(2, 4, 0) // apex feet ~1.25 above stance y=1 -> head reaches past y=4

        assertNull(
            JumpArcProbe.probe(world, Stance(0, 1, 0), 4, 0, rise = 0),
            "a jump whose apex head passes through a block must not be proposed",
        )
    }

    @Test
    fun `a bottom slab at apex head height still blocks the arc`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(4, 0, 0)
        // Bottom slab occupies only [4.0, 4.5]; the apex head (~4.05) clips it. The old
        // trait mask saw `centerPassable` heuristics, never the shape.
        world.shape(2, 4, 0, VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0))

        assertNull(
            JumpArcProbe.probe(world, Stance(0, 1, 0), 4, 0, rise = 0),
            "a slab the apex head clips must block the arc",
        )
    }

    @Test
    fun `a top slab over the mid column blocks a flat span-2 jump`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(2, 0, 0)
        // Top slab at the mid column's head cell: [2.5, 3.0] above the stance floor.
        // The body crosses the column with feet around 1.0-1.25, head past 2.8.
        world.shape(1, 3, 0, VoxelShapes.cuboid(0.0, 0.5, 0.0, 1.0, 1.0, 1.0))

        assertNull(
            JumpArcProbe.probe(world, Stance(0, 1, 0), 2, 0, rise = 0),
            "a top slab the crossing head clips must block the arc",
        )
    }

    @Test
    fun `a fence on the mid column blocks the low crossing`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(2, 0, 0)
        // A fence's collision shape is 1.5 tall; a jump's feet only reach ~1.25.
        world.shape(1, 1, 0, VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625))

        assertNull(
            JumpArcProbe.probe(world, Stance(0, 1, 0), 2, 0, rise = 0),
            "a fence is taller than any jump arc; the crossing must be blocked",
        )
    }

    @Test
    fun `the same mid column with plain air is reachable`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(2, 0, 0)

        val result = assertNotNull(JumpArcProbe.probe(world, Stance(0, 1, 0), 2, 0, rise = 0))
        assertTrue(result.solution.clearance > 0.0)
    }

    @Test
    fun `a rising span-2 jump onto a one-block-higher landing is reachable`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(2, 1, 0)

        assertNotNull(
            JumpArcProbe.probe(world, Stance(0, 1, 0), 2, 0, rise = 1),
            "the measured reach admits a span-2 rise-1 jump; deleting it starved the field topology",
        )
    }

    @Test
    fun `a borderline span-4 rise-1 jump stays proposed -- permissiveness is the contract`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(4, 1, 0)

        // Sprint rise-1 reach is ~3.79 + 0.4 launch depth against a 4.0-block landing
        // with pad tolerance: inside the permissive band. A false positive costs one
        // M6 reroute; deleting it would starve topology forever (C1).
        assertNotNull(JumpArcProbe.probe(world, Stance(0, 1, 0), 4, 0, rise = 1))
    }

    @Test
    fun `a jump beyond every measured reach is refused by arithmetic alone`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(6, 0, 0)

        assertNull(
            JumpArcProbe.probe(world, Stance(0, 1, 0), 6, 0, rise = 0),
            "sprint flat reach is ~4.5 + 0.4 depth; six blocks is not reachable by any family",
        )
    }

    @Test
    fun `a diagonal span-2 jump sweeps the flanking corner columns`() {
        val world = ShapeWorld()
        world.fullCube(0, 0, 0)
        world.fullCube(2, 0, 2)
        // A wall beside the diagonal line: the swept body must catch it even though
        // no stance column is blocked.
        for (y in 1..3) world.fullCube(2, y, 1)
        for (y in 1..3) world.fullCube(1, y, 2)

        assertNull(
            JumpArcProbe.probe(world, Stance(0, 1, 0), 2, 2, rise = 0),
            "walls flanking the diagonal chord must block the arc",
        )
    }

    @Test
    fun `an uncaptured cell on the arc fails closed`() {
        val world = ShapeWorld(unknownIsNull = true)
        world.fullCube(0, 0, 0)
        world.fullCube(2, 0, 0)

        assertNull(
            JumpArcProbe.probe(world, Stance(0, 1, 0), 2, 0, rise = 0),
            "a view without shapes must propose no jumps rather than guess",
        )
    }

    /** Minimal shape-carrying view: air everywhere except the placed shapes. */
    private class ShapeWorld(private val unknownIsNull: Boolean = false) : CoarseVoxelView {
        private val shapes = HashMap<VoxelPos, VoxelShape>()

        fun fullCube(x: Int, y: Int, z: Int) {
            shapes[VoxelPos(x, y, z)] = VoxelShapes.fullCube()
        }

        fun shape(x: Int, y: Int, z: Int, shape: VoxelShape) {
            shapes[VoxelPos(x, y, z)] = shape
        }

        override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel =
            if (shapes.containsKey(VoxelPos(x, y, z))) CoarseVoxel.FULL_BLOCK else CoarseVoxel.AIR

        override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? =
            shapes[VoxelPos(x, y, z)] ?: if (unknownIsNull) null else VoxelShapes.empty()
    }
}
