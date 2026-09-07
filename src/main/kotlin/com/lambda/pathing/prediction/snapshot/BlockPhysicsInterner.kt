/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.prediction.snapshot

import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.IdentityHashMap

/**
 * One [SnapshotBlockPhysics] per distinct [BlockState], not per cell.
 *
 * Everything in the physics record is state-derived and block states are canonical
 * singletons, so capture scales with the DISTINCT states a section holds, not its 4096
 * cells. A block declaring `hasDynamicBounds()` (shulker boxes, pistons mid-extension)
 * is captured per cell. Context-sensitive shapes (scaffolding) are safe to intern because
 * [shapeContext] is fixed for the interner's lifetime.
 * See docs/decisions/snapshot-capture.md.
 *
 * Not thread-safe: one capture pipeline owns it, on the client thread.
 */
internal class BlockPhysicsInterner(private val shapeContext: ShapeContext) {
    private val byState = IdentityHashMap<BlockState, SnapshotBlockPhysics>()

    fun capture(world: World, pos: BlockPos, state: BlockState): SnapshotBlockPhysics {
        if (state.block.hasDynamicBounds()) {
            return BlockPhysicsCapture.capture(state, world, pos, shapeContext)
        }
        return byState.getOrPut(state) {
            BlockPhysicsCapture.capture(state, world, pos, shapeContext)
        }
    }
}
