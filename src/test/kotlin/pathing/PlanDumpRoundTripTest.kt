/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.prediction.UnsupportedPhysics
import com.lambda.pathing.prediction.UnsupportedPhysicsKind
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

/**
 * A dump that quietly loses shape detail is worse than no dump: it would reproduce a
 * *different* world and send the next investigation somewhere the reporter never was.
 */
class PlanDumpRoundTripTest {
    @Test
    fun `a dumped plan reloads with identical physics, shapes and entry state`() {
        val slab = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
            slipperiness = 0.98,
            velocityMultiplier = 0.4,
            jumpVelocityMultiplier = 0.5,
            coarseVoxel = CoarseVoxel(false, true, 0.5, 0.0),
        )
        val fence = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.union(
                VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625),
                VoxelShapes.cuboid(0.0, 0.75, 0.375, 1.0, 0.9375, 0.625),
            ),
            coarseVoxel = CoarseVoxel(false, false, null, 0.5),
            fenceLike = true,
        )
        val lava = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.empty(),
            unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID, "minecraft:lava"),
            coarseVoxel = CoarseVoxel.UNKNOWN,
        )
        val blocks = mapOf(
            BlockPos(0, 64, 0) to SnapshotBlockPhysics.FULL_CUBE,
            BlockPos(1, 64, 0) to slab,
            BlockPos(2, 64, 0) to fence,
            BlockPos(3, 64, 0) to lava,
        )
        val bounds = SimulationSnapshotBounds(-4, 60, -4, 8, 76, 8)
        val environment = SnapshotSimulationEnvironment.synthetic(bounds, blocks)
        val profile = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
        val initial = MovementSimulationState.synthetic(
            profile = profile,
            position = Vec3d(0.5, 65.0, 0.5),
            rotation = Rotation(37.5, 12.0),
            velocity = Vec3d(0.123, -0.0784, -0.456),
            onGround = true,
        ).copy(isSprinting = true, jumpingCooldown = 3, collidedSoftly = true)

        val moveOptions = SimpleMoveOptions(
            allowDiagonal = false, allowStepUp = true, maxWalkOffDepth = 5,
            allowJumpCandidates = true, maxJumpSpan = 5, maxJumpDrop = 2, allowOffAxisJumps = false,
        )
        val searchConfig = MotionConstraints(
            maxFrames = 240, maxYawDegreesPerFrame = 42.5, goalRadius = 0.33, sprintModes = listOf(false),
        )
        val directory = Files.createTempDirectory("plan-dump-round-trip")
        val path = PlanDump.write(
            directory, environment, Stance(0, 65, 0), Stance(7, 65, 3), initial, profile,
            moveOptions, searchConfig, "unit round trip",
        )
        val loaded = PlanDump.read(path)

        assertEquals(bounds, loaded.bounds)
        assertEquals(Stance(0, 65, 0), loaded.start)
        assertEquals(Stance(7, 65, 3), loaded.goal)
        assertEquals(profile, loaded.profile)
        // A dump that loses these replays a different coarse graph and a different search.
        assertEquals(moveOptions, loaded.moveOptions)
        assertEquals(searchConfig.maxFrames, loaded.searchConfig.maxFrames)
        assertEquals(searchConfig.maxYawDegreesPerFrame, loaded.searchConfig.maxYawDegreesPerFrame)
        assertEquals(searchConfig.goalRadius, loaded.searchConfig.goalRadius)
        assertEquals(searchConfig.sprintModes, loaded.searchConfig.sprintModes)
        assertEquals(initial, loaded.initialState)
        assertEquals("unit round trip", loaded.note)

        // Air is not written, so the reload must be sparse and every solid cell exact.
        assertEquals(blocks.size, loaded.blocks.size)
        for ((pos, expected) in blocks) {
            val actual = loaded.blocks.getValue(pos)
            assertEquals(expected.slipperiness, actual.slipperiness, "slipperiness at $pos")
            assertEquals(expected.velocityMultiplier, actual.velocityMultiplier, "velocity at $pos")
            assertEquals(expected.jumpVelocityMultiplier, actual.jumpVelocityMultiplier, "jump at $pos")
            assertEquals(expected.unsupportedPhysics, actual.unsupportedPhysics, "physics at $pos")
            assertEquals(expected.coarseVoxel, actual.coarseVoxel, "voxel at $pos")
            assertEquals(expected.fenceLike, actual.fenceLike, "fenceLike at $pos")
            assertEquals(
                expected.collisionShape.boundingBoxes, actual.collisionShape.boundingBoxes,
                "collision shape at $pos",
            )
        }

        // The reloaded environment must answer the simulator identically, which is the
        // only property that actually matters for replaying a refusal.
        val reloaded = loaded.environment()
        for (x in -1..4) for (y in 63..66) for (z in -1..1) {
            assertEquals(
                environment.collisionShape(x, y, z).boundingBoxes,
                reloaded.collisionShape(x, y, z).boundingBoxes,
                "reloaded shape at ($x, $y, $z)",
            )
            assertEquals(
                environment.voxel(x, y, z), reloaded.voxel(x, y, z),
                "reloaded voxel at ($x, $y, $z)",
            )
        }
        assertTrue(Files.size(path) > 0)
    }
}
