/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.prediction.snapshot

import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.IdentityHashMap

/**
 * One [SnapshotBlockPhysics] per distinct [BlockState], not per cell.
 *
 * Everything in the physics record is state-derived -- collision shape, block
 * friction and multipliers, medium, fence-likeness -- and block states are canonical
 * singletons, so capture cost should scale with the handful of DISTINCT states a
 * section holds, not its 4096 cells. Before this, capture resolved a collision shape
 * and allocated a fresh record per cell, which is why sections cost milliseconds and
 * a cold start spent seconds standing still while its surroundings trickled in.
 *
 * The escape hatch mirrors Minecraft's own shape-cache contract: a block whose shape
 * genuinely depends on position (shulker boxes, pistons mid-extension) declares
 * `hasDynamicBounds()` and is captured per cell exactly as before. Context-sensitive
 * shapes (scaffolding) are safe to intern because the [shapeContext] is fixed for the
 * interner's lifetime -- the same object the per-cell path always used per session.
 *
 * Not thread-safe: one capture pipeline owns it, on the client thread.
 */
internal class BlockPhysicsInterner(private val shapeContext: ShapeContext) {
    private val byState = IdentityHashMap<BlockState, SnapshotBlockPhysics>()

    fun capture(world: World, pos: BlockPos, state: BlockState): SnapshotBlockPhysics {
        if (state.block.hasDynamicBounds()) {
            return with(SnapshotSimulationEnvironment.Companion) {
                state.capturePhysics(world, pos, shapeContext)
            }
        }
        return byState.getOrPut(state) {
            with(SnapshotSimulationEnvironment.Companion) {
                state.capturePhysics(world, pos, shapeContext)
            }
        }
    }
}
