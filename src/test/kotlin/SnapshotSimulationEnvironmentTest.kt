/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.util.player.prediction

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.physics.SimulationSnapshotOutOfBoundsException
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.physics.SnapshotSectionUnavailableException
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.physics.UnsupportedBlockPhysicsException
import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.world.CoarseVoxel
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pathing.ProbeScenarios.PROFILE

class SnapshotSimulationEnvironmentTest {
    @Test
    fun `an unstreamed section carries no terrain and rejects exact reads`() {
        val misses = ArrayList<Long>()
        val environment = SnapshotSimulationEnvironment.streaming(SimulationSnapshotBounds(0, 0, 0, 15, 15, 15)) { misses += it }

        // The coarse layer must not invent terrain here: an unstreamed cell is unknown,
        // which carries no stance and therefore no edge. What crosses it is the
        // planner's single optimistic edge, not a surface.
        assertEquals(CoarseVoxel.UNKNOWN, environment.voxel(4, 4, 4))
        assertFalse(environment.isKnown(4, 4, 4))
        assertTrue(environment.collisionShape(4, 4, 4).isEmpty)
        assertFailsWith<SnapshotSectionUnavailableException> { environment.slipperiness(BlockPos(4, 4, 4)) }
        assertEquals(listOf(ChunkSectionPos.asLong(0, 0, 0)), misses)
    }

    @Test
    fun `an exact read of the streaming frontier rejects the step instead of parking it`() {
        val environment = SnapshotSimulationEnvironment.streaming(SimulationSnapshotBounds(0, 0, 0, 15, 15, 15)) {}
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(4.5, 5.0, 4.5),
            rotation = Rotation(0.0, 0.0),
            onGround = true,
        )
        val simulator = MovementSimulator(PROFILE, environment, initial)

        assertFailsWith<SnapshotSectionUnavailableException> { simulator.tickMovement(MovementSimulationInput(forward = 1.0)) }
        assertEquals(CoarseVoxel.UNKNOWN, environment.voxel(4, 3, 4))
    }

    @Test
    fun `tracked snapshot view records immutable exact scalar dependencies`() {
        val environment = environment(emptyMap())
        val tracked = environment.trackingView()

        tracked.slipperiness(BlockPos(0, 0, 0))
        tracked.velocityMultiplier(BlockPos(1, 0, 0))
        val published = tracked.takeFrameDependencies()
        tracked.jumpVelocityMultiplier(BlockPos(0, 1, 0))

        assertEquals(
            setOf(
                com.lambda.pathing.core.VoxelPos(0, 0, 0),
                com.lambda.pathing.core.VoxelPos(1, 0, 0),
            ),
            published,
        )
        assertEquals(setOf(com.lambda.pathing.core.VoxelPos(0, 1, 0)), tracked.takeFrameDependencies())
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (published as MutableSet<com.lambda.pathing.core.VoxelPos>) += com.lambda.pathing.core.VoxelPos(2, 0, 0)
        }
    }

    @Test
    fun `snapshot exposes fail closed coarse traits without live world reads`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-1, -1, -1, 1, 1, 1),
            blocks = mapOf(
                BlockPos.ORIGIN to SnapshotBlockPhysics.FULL_CUBE,
                BlockPos(1, 0, 0) to SnapshotBlockPhysics(
                    collisionShape = VoxelShapes.empty(),
                    unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID),
                    coarseVoxel = CoarseVoxel.AIR,
                ),
            ),
        )

        assertEquals(CoarseVoxel.FULL_BLOCK, environment.voxel(0, 0, 0))
        assertEquals(CoarseVoxel.AIR, environment.voxel(0, 1, 0))

        // A fluid is impassable, never standable -- but it is *read*, not unknown. It does
        // not bulge into the cell above it, and saying it did made every arc that would
        // clear a lava pool unroutable.
        assertEquals(CoarseVoxel.HAZARD, environment.voxel(1, 0, 0))
        assertFalse(environment.voxel(1, 0, 0).centerPassable)
        assertNull(environment.voxel(1, 0, 0).standingSurface)
        assertFalse(environment.voxel(1, 0, 0).intrudesAbove)

        // Genuinely unknown: outside the snapshot. Pessimistic on every axis.
        assertEquals(CoarseVoxel.UNKNOWN, environment.voxel(2, 0, 0))
        assertTrue(environment.voxel(2, 0, 0).intrudesAbove)
    }

    @Test
    fun `snapshot copies block physics and defaults unspecified cells to air`() {
        val special = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.empty(),
            slipperiness = 0.98,
            velocityMultiplier = 0.4,
            jumpVelocityMultiplier = 0.5,
        )
        val environment = environment(mapOf(BlockPos(0, 0, 0) to special))

        assertClose(0.98, environment.slipperiness(BlockPos.ORIGIN))
        assertClose(0.4, environment.velocityMultiplier(BlockPos.ORIGIN))
        assertClose(0.5, environment.jumpVelocityMultiplier(BlockPos.ORIGIN))
        assertClose(Vanilla.DEFAULT_SLIPPERINESS.toDouble(), environment.slipperiness(BlockPos(1, 0, 0)))
    }

    @Test
    fun `section lookup preserves distinct values across negative boundaries`() {
        val values = mapOf(
            BlockPos(-17, -17, -17) to specialPhysics(0.11),
            BlockPos(-16, -16, -16) to specialPhysics(0.22),
            BlockPos(-1, -1, -1) to specialPhysics(0.33),
            BlockPos(0, 0, 0) to specialPhysics(0.44),
        )
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-17, -17, -17, 0, 0, 0),
            values,
        )

        values.forEach { (pos, physics) -> assertClose(physics.slipperiness, environment.slipperiness(pos)) }
        assertEquals(3, environment.sectionCount)
    }

    @Test
    fun `synthetic snapshots reject cells outside their declared bounds`() {
        assertFailsWith<IllegalArgumentException> {
            SnapshotSimulationEnvironment.synthetic(
                SimulationSnapshotBounds(0, 0, 0, 1, 1, 1),
                mapOf(BlockPos(2, 0, 0) to SnapshotBlockPhysics.FULL_CUBE),
            )
        }
    }

    @Test
    fun `full cube wall clips horizontal movement`() {
        val blocks = buildMap {
            for (y in 0..2) put(BlockPos(0, y, 1), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = environment(blocks)
        val playerBox = Box(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)

        val adjusted = environment.adjustMovementForCollisions(
            movement = Vec3d(0.0, 0.0, 2.0),
            boundingBox = playerBox,
            onGround = true,
            stepHeight = 0.6,
        )

        assertClose(0.7, adjusted.z)
        assertClose(0.0, adjusted.y)
    }

    /**
     * Field replans at trees (10 Oct 2026): a body one tick past a downhill edge is still on ground,
     * jumps forward and its head meets leaves. Vanilla's collision collection only offers step
     * heights from blocks the query box touches, plus margin blocks taller than a cube, so the
     * edge block just left behind is no candidate: the jump rises and stops at the leaves. Taking
     * step heights from every scanned block kept the body at its feet and slid it under them.
     */
    @Test
    fun `a block behind the step query is no step candidate`() {
        val blocks = buildMap {
            for (x in -2..2) {
                put(BlockPos(x, 85, 0), SnapshotBlockPhysics.FULL_CUBE)
                for (z in 1..3) put(BlockPos(x, 84, z), SnapshotBlockPhysics.FULL_CUBE)
                put(BlockPos(x, 88, 2), SnapshotBlockPhysics.FULL_CUBE)
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-8, 80, -8, 8, 95, 8), blocks)
        val pastTheEdge = Box(0.2, 86.0, 1.04, 0.8, 87.8, 1.64)

        val adjusted = environment.adjustMovementForCollisions(
            movement = Vec3d(0.0, 0.42, 0.58),
            boundingBox = pastTheEdge,
            onGround = true,
            stepHeight = 0.6,
        )

        assertClose(0.42, adjusted.y)
        assertClose(0.36, adjusted.z)
    }

    @Test
    fun `snapshot collision resolver performs vanilla slab auto step`() {
        val slab = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
        )
        val environment = environment(mapOf(BlockPos(0, 0, 1) to slab))
        val playerBox = Box(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3)

        val adjusted = environment.adjustMovementForCollisions(
            movement = Vec3d(0.0, 0.0, 1.0),
            boundingBox = playerBox,
            onGround = true,
            stepHeight = 0.6,
        )

        assertClose(1.0, adjusted.z)
        assertClose(0.5, adjusted.y)
    }

    @Test
    fun `snapshot reads fail closed outside captured bounds`() {
        val environment = environment(emptyMap())

        val failure = assertFailsWith<SimulationSnapshotOutOfBoundsException> {
            environment.slipperiness(BlockPos(10, 0, 0))
        }

        assertEquals(BlockPos(10, 0, 0), failure.pos)
    }

    @Test
    fun `unsupported physics is rejected when the body moves into it, not when its friction is read`() {
        val fluid = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.empty(),
            unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID),
        )
        val pad = SnapshotBlockPhysics(collisionShape = VoxelShapes.cuboid(0.0625, 0.0, 0.0625, 0.9375, 0.09375, 0.9375))
        val body = Box(0.2, 1.2, 0.2, 0.8, 3.0, 0.8)

        val open = environment(mapOf(BlockPos.ORIGIN to fluid))
        assertEquals(1.0, open.velocityMultiplier(BlockPos.ORIGIN), "a fluid under a support only lends its friction")
        val failure = assertFailsWith<UnsupportedBlockPhysicsException> {
            open.adjustMovementForCollisions(Vec3d(0.0, -0.5, 0.0), body, onGround = false, stepHeight = 0.6)
        }
        assertEquals(UnsupportedPhysicsKind.FLUID, failure.physics.kind)

        // The same fall onto a lily pad stops on the pad, above the water the query box reached into.
        val padded = environment(mapOf(BlockPos.ORIGIN to fluid, BlockPos(0, 1, 0) to pad))
        val landed = padded.adjustMovementForCollisions(Vec3d(0.0, -0.5, 0.0), body, onGround = false, stepHeight = 0.6)
        assertClose(1.09375 - 1.2, landed.y)
    }

    @Test
    fun `unsupported block in collision scan halo does not reject until touched`() {
        val environment = environment(
            mapOf(
                BlockPos(2, 0, 0) to SnapshotBlockPhysics(
                    collisionShape = VoxelShapes.empty(),
                    unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID),
                ),
            ),
        )

        val adjusted = environment.adjustMovementForCollisions(
            movement = Vec3d(0.1, 0.0, 0.0),
            boundingBox = Box(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3),
            onGround = true,
            stepHeight = 0.6,
        )

        assertEquals(Vec3d(0.1, 0.0, 0.0), adjusted)
    }

    @Test
    fun `landing on powder snow held up only by the neighbouring block is rejected`() {
        val environment = environment(mapOf(BlockPos(0, 0, 0) to POWDER_SNOW, BlockPos(0, 0, -1) to SnapshotBlockPhysics.FULL_CUBE))
        val body = Box(0.2, 1.2, -0.4, 0.8, 3.0, 0.2)

        val failure = assertFailsWith<UnsupportedBlockPhysicsException> {
            environment.adjustMovementForCollisions(Vec3d(0.0, -0.5, 0.65), body, onGround = false, stepHeight = 0.6)
        }
        assertEquals(BlockPos(0, 0, 0), failure.pos)
        val partly = environment.adjustMovementForCollisions(Vec3d(0.0, -0.5, 0.3), body, onGround = false, stepHeight = 0.6)
        assertClose(-0.2, partly.y)
    }

    @Test
    fun `a jump may take off from an edge beside powder snow`() {
        val environment = environment(mapOf(BlockPos(1, -1, 0) to POWDER_SNOW, BlockPos(0, -1, 0) to SnapshotBlockPhysics.FULL_CUBE))
        val body = Box(0.2, 0.0, 0.2, 0.8, 1.8, 0.8)

        assertEquals(Vec3d(0.5, 0.42, 0.0), environment.adjustMovementForCollisions(Vec3d(0.5, 0.42, 0.0), body, onGround = true, stepHeight = 0.6))
    }

    @Test
    fun `a body may walk out of powder snow it already touches but never into it`() {
        val environment = environment(mapOf(BlockPos(1, 0, 0) to POWDER_SNOW, BlockPos(0, -1, 0) to SnapshotBlockPhysics.FULL_CUBE))
        val touching = Box(0.5, 0.0, 0.2, 1.1, 1.8, 0.8)

        assertEquals(Vec3d(-0.2, 0.0, 0.0), environment.adjustMovementForCollisions(Vec3d(-0.2, 0.0, 0.0), touching, onGround = true, stepHeight = 0.6))
        assertFailsWith<UnsupportedBlockPhysicsException> {
            environment.adjustMovementForCollisions(Vec3d(0.3, 0.0, 0.0), Box(0.2, 0.0, 0.2, 0.8, 1.8, 0.8), onGround = true, stepHeight = 0.6)
        }
        assertFailsWith<UnsupportedBlockPhysicsException>("feet inside powder snow are slowed, which is not simulated") {
            environment.adjustMovementForCollisions(Vec3d(0.3, 0.0, 0.0), touching, onGround = true, stepHeight = 0.6)
        }
    }

    @Test
    fun `a step leaving the snapshot bounds is rejected`() {
        val tinyEnvironment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(0, 0, 0, 0, 0, 0),
            blocks = emptyMap(),
        )
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.5, 1.0, 0.5),
            rotation = Rotation(0.0, 0.0),
            onGround = true,
        )
        val simulator = MovementSimulator(PROFILE, tinyEnvironment, initial)

        assertFailsWith<SimulationSnapshotOutOfBoundsException> {
            simulator.tickMovement(MovementSimulationInput(forward = 1.0, sprint = true, jump = true))
        }
    }

    private fun environment(blocks: Map<BlockPos, SnapshotBlockPhysics>) =
        SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) <= 1.0E-9, "expected <$expected>, actual <$actual>")
    }

    private fun specialPhysics(slipperiness: Double) = SnapshotBlockPhysics(
        collisionShape = VoxelShapes.empty(),
        slipperiness = slipperiness,
    )

    private companion object {
        val BOUNDS = SimulationSnapshotBounds(-3, -3, -3, 3, 4, 4)

        val POWDER_SNOW = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.empty(),
            unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.POWDER_SNOW),
        )
    }
}
