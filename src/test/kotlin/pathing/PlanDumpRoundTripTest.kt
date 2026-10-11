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
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.store.TerrainStore
import minecraft.MinecraftBootstrap
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.BlockView
import net.minecraft.world.chunk.PaletteProvider
import net.minecraft.world.chunk.PalettedContainer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

/**
 * A dump that quietly loses shape detail is worse than no dump: it would reproduce a
 * *different* world and send the next investigation somewhere the reporter never was.
 * Dumps now reference block states in the terrain store and derive physics on load, and the
 * dumps that embedded their physics must keep loading.
 */
class PlanDumpRoundTripTest {
    @Test
    fun `a version 5 dump still loads with identical physics, shapes and entry state`() {
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
        val water = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.empty(),
            unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID, "minecraft:water"),
            coarseVoxel = CoarseVoxel.UNKNOWN,
            water = WaterPhysics(0.7777778f, Vec3d(-0.7071067811865475, 0.0, 0.7071067811865476)),
        )
        val blocks = mapOf(
            BlockPos(0, 64, 0) to SnapshotBlockPhysics.FULL_CUBE,
            BlockPos(1, 64, 0) to slab,
            BlockPos(2, 64, 0) to fence,
            BlockPos(3, 64, 0) to lava,
            BlockPos(4, 64, 0) to water,
        )
        val bounds = SimulationSnapshotBounds(-4, 60, -4, 8, 76, 8)
        val environment = SnapshotSimulationEnvironment.synthetic(bounds, blocks)
        val profile = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8,
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
            allowDeepDropJumps = false,
        )

        // Written by the last writer that embedded physics; the field corpus is full of these.
        val loaded = PlanDump.read(Path.of(checkNotNull(javaClass.getResource("/pathing/plan-v5.dump")).toURI()))

        assertEquals(bounds, loaded.bounds)
        assertEquals(Stance(0, 65, 0), loaded.start)
        assertEquals(Stance(7, 65, 3), loaded.goal)
        assertEquals(profile, loaded.profile)
        assertEquals(moveOptions, loaded.moveOptions)
        assertEquals(240, loaded.searchConfig.maxFrames)
        assertEquals(42.5, loaded.searchConfig.maxYawDegreesPerFrame)
        assertEquals(0.33, loaded.searchConfig.goalRadius)
        assertEquals(listOf(false), loaded.searchConfig.sprintModes)
        assertEquals(initial, loaded.initialState)
        assertEquals("unit round trip", loaded.note)

        val reloaded = loaded.environment()
        for ((pos, expected) in blocks) {
            val actual = checkNotNull(reloaded.installedBlock(pos.x, pos.y, pos.z))
            assertEquals(expected.slipperiness, actual.slipperiness, "slipperiness at $pos")
            assertEquals(expected.velocityMultiplier, actual.velocityMultiplier, "velocity at $pos")
            assertEquals(expected.jumpVelocityMultiplier, actual.jumpVelocityMultiplier, "jump at $pos")
            assertEquals(expected.unsupportedPhysics, actual.unsupportedPhysics, "physics at $pos")
            assertEquals(expected.coarseVoxel, actual.coarseVoxel, "voxel at $pos")
            assertEquals(expected.fenceLike, actual.fenceLike, "fenceLike at $pos")
            assertEquals(expected.water, actual.water, "water at $pos")
            assertEquals(expected.collisionShape.boundingBoxes, actual.collisionShape.boundingBoxes, "collision shape at $pos")
        }
        for (x in -1..4) for (y in 63..66) for (z in -1..1) {
            assertEquals(environment.collisionShape(x, y, z).boundingBoxes, reloaded.collisionShape(x, y, z).boundingBoxes, "reloaded shape at ($x, $y, $z)")
            assertEquals(environment.voxel(x, y, z), reloaded.voxel(x, y, z), "reloaded voxel at ($x, $y, $z)")
        }
    }

    @Test
    fun `a dumped plan replays the terrain store with physics derived from block states`() {
        MinecraftBootstrap.ensure()
        val states = mapOf(
            BlockPos(0, 64, 0) to Blocks.STONE.defaultState,
            BlockPos(1, 64, 0) to Blocks.OAK_SLAB.defaultState,
            BlockPos(2, 64, 0) to Blocks.OAK_FENCE.defaultState,
            BlockPos(3, 64, 0) to Blocks.LAVA.defaultState,
            BlockPos(4, 64, 0) to Blocks.WATER.defaultState,
            BlockPos(5, 64, 0) to Blocks.LADDER.defaultState,
            BlockPos(6, 64, 0) to Blocks.OAK_LEAVES.defaultState,
            BlockPos(7, 64, 0) to Blocks.ICE.defaultState,
            BlockPos(20, 64, 0) to Blocks.SLIME_BLOCK.defaultState,
        )
        val directory = Files.createTempDirectory("plan-dump-terrain")
        val store = TerrainStore(directory.resolve("terrain"))
        val sections = states.keys.map { ChunkSectionPos.toLong(it) }.distinct()
        for (key in sections) {
            val container = PalettedContainer(Blocks.AIR.defaultState, PaletteProvider.forBlockStates(Block.STATE_IDS))
            states.filterKeys { ChunkSectionPos.toLong(it) == key }.forEach { (pos, state) -> container.set(pos.x and 15, pos.y and 15, pos.z and 15, state) }
            store.record(key, container, System.currentTimeMillis())
        }
        val bounds = SimulationSnapshotBounds(-64, -64, -64, 64, 319, 64)
        val profile = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false, width = 0.6, height = 1.8,
        )
        val initial = MovementSimulationState.synthetic(profile, Vec3d(0.5, 65.0, 0.5), Rotation(37.5, 12.0), onGround = true)
        val path = PlanDump.write(
            directory.resolve("dumps"), store, sections.toLongArray(), bounds, Stance(0, 65, 0), Stance(7, 65, 3), initial, profile,
            SimpleMoveOptions(), MotionConstraints(), "terrain round trip",
        )
        store.close()

        val loaded = PlanDump.read(path)
        assertEquals(bounds, loaded.bounds)
        assertEquals(initial, loaded.initialState)
        assertEquals(sections.toSet(), loaded.sections.keys)
        val environment = loaded.environment()
        val live = BlockPhysicsSampler(StatesView(states), ShapeContext.absent())
        for (pos in states.keys + listOf(BlockPos(0, 65, 0), BlockPos(9, 64, 0))) {
            val expected = live.physicsAt(pos.x, pos.y, pos.z)
            assertEquals(expected, environment.installedBlock(pos.x, pos.y, pos.z), "derived physics at $pos")
        }
        assertEquals(SnapshotBlockPhysics.FULL_CUBE.collisionShape.boundingBoxes, environment.collisionShape(0, 64, 0).boundingBoxes)
        assertEquals(0.5, environment.voxel(1, 64, 0).standingSurface)
        assertTrue(checkNotNull(environment.installedBlock(2, 64, 0)).exceedsCube, "a fence exceeds its cube")
        assertEquals(CoarseVoxel.WATER, environment.voxel(4, 64, 0))
        assertEquals(CoarseVoxel.CLIMBABLE, environment.voxel(5, 64, 0))
        assertEquals(0.98, checkNotNull(environment.installedBlock(7, 64, 0)).slipperiness, 1e-6)
        assertTrue(environment.voxel(20, 64, 0).bouncy, "slime bounces")
        assertTrue(Files.size(path) < 1_000, "a dump that references its terrain stays small: ${Files.size(path)} bytes")
    }

    private class StatesView(private val states: Map<BlockPos, BlockState>) : BlockView {
        override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
        override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
        override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
        override fun getHeight(): Int = 384
        override fun getBottomY(): Int = -64
    }
}
