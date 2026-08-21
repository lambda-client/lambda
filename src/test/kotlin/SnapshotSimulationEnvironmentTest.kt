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
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.Stance
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
import kotlin.test.assertTrue

class SnapshotSimulationEnvironmentTest {
    @Test
    fun `an unstreamed section carries no terrain and no exact reads`() {
        val key = ChunkSectionPos.asLong(0, 0, 0)
        val unavailable = ImmutableSnapshotSection.Builder().apply {
            fill(SnapshotBlockPhysics.UNAVAILABLE)
        }.build(expectedWrites = 4096)
        val exact = ImmutableSnapshotSection.Builder().apply {
            fill(SnapshotBlockPhysics.AIR)
        }.build(expectedWrites = 4096)
        var exactRequests = 0
        val environment = SnapshotSimulationEnvironment(
            bounds = SimulationSnapshotBounds(0, 0, 0, 15, 15, 15),
            sections = mapOf(key to unavailable),
            defaultBlock = null,
            missingSection = { _, _, _, exactRead ->
                assertTrue(exactRead)
                exactRequests++
                exact
            },
            unavailableSectionKeys = setOf(key),
        )

        // The coarse layer must not invent terrain here: an unstreamed cell is unknown,
        // which carries no stance and therefore no edge. What crosses it is the
        // planner's single optimistic edge, not a surface.
        assertEquals(CoarseVoxel.UNKNOWN, environment.voxel(4, 4, 4))
        assertFalse(environment.isKnown(4, 4, 4))
        assertTrue(environment.collisionShape(4, 4, 4).isEmpty)
        assertEquals(SnapshotBlockPhysics.DEFAULT_SLIPPERINESS, environment.slipperiness(BlockPos(4, 4, 4)))
        assertEquals(1, exactRequests)
    }

    @Test
    fun `an exact read of the streaming frontier rejects the step instead of parking it`() {
        val key = ChunkSectionPos.asLong(0, 0, 0)
        val unavailable = ImmutableSnapshotSection.Builder().apply {
            fill(SnapshotBlockPhysics.UNAVAILABLE)
        }.build(expectedWrites = 4096)
        val environment = SnapshotSimulationEnvironment(
            bounds = SimulationSnapshotBounds(0, 0, 0, 15, 15, 15),
            sections = mapOf(key to unavailable),
            defaultBlock = null,
            missingSection = { sectionX, sectionY, sectionZ, exactRead ->
                assertTrue(exactRead)
                throw SnapshotSectionUnavailableException(sectionX, sectionY, sectionZ)
            },
            unavailableSectionKeys = setOf(key),
        )
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(4.5, 5.0, 4.5),
            rotation = Rotation(0.0, 0.0),
            onGround = true,
        )
        val simulator = MovementSimulator(PROFILE, environment, initial)

        val result = simulator.tryTickMovement(MovementSimulationInput(forward = 1.0))

        assertTrue(result is MovementSimulationStepResult.Rejected)
        assertTrue(result.failure is SnapshotSectionUnavailableException)
        assertEquals(initial, simulator.state)
        assertEquals(CoarseVoxel.UNKNOWN, environment.voxel(4, 3, 4))
    }

    @Test
    fun `tracked snapshot view records immutable exact scalar dependencies`() {
        val environment = environment(emptyMap())
        val tracked = environment.trackingView()

        tracked.slipperiness(BlockPos(0, 0, 0))
        tracked.velocityMultiplier(BlockPos(1, 0, 0))
        val published = tracked.dependencies()
        tracked.jumpVelocityMultiplier(BlockPos(0, 1, 0))

        assertEquals(
            setOf(
                com.lambda.pathing.world.VoxelPos(0, 0, 0),
                com.lambda.pathing.world.VoxelPos(1, 0, 0),
            ),
            published,
        )
        assertEquals(3, tracked.dependencies().size)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (published as MutableSet<com.lambda.pathing.world.VoxelPos>) += com.lambda.pathing.world.VoxelPos(2, 0, 0)
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
        assertFalse(environment.voxel(1, 0, 0).standableFullTop)
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
        assertClose(0.6, environment.slipperiness(BlockPos(1, 0, 0)))
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
        assertEquals(3, environment.storageStats().sections)
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
    fun `unsupported captured physics is rejected`() {
        val environment = environment(
            mapOf(
                BlockPos.ORIGIN to SnapshotBlockPhysics(
                    collisionShape = VoxelShapes.empty(),
                    unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID),
                ),
            ),
        )

        val failure = assertFailsWith<UnsupportedBlockPhysicsException> {
            environment.velocityMultiplier(BlockPos.ORIGIN)
        }

        assertEquals(UnsupportedPhysicsKind.FLUID, failure.physics.kind)
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
    fun `rejected snapshot step rolls simulator state back transactionally`() {
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

        val result = simulator.tryTickMovement(
            MovementSimulationInput(forward = 1.0, sprint = true, jump = true),
        )

        assertTrue(result is MovementSimulationStepResult.Rejected)
        assertTrue(result.failure is SimulationSnapshotOutOfBoundsException)
        assertEquals(initial, simulator.state)
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

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1,
            sneakSpeedModifier = 0.3,
            gravity = 0.08,
            jumpStrength = 0.42,
            stepHeight = 0.6,
            jumpBoostVelocityModifier = 0.0,
            slowFalling = false,
            width = 0.6,
            height = 1.8,
            eyeHeight = 1.62,
        )
    }
}
