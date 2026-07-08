/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.worldview

import com.lambda.util.world.FastVector
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView

/**
 * The shared world substrate every planning layer reads through (research
 * plan §4.2): int-encoded blockstates + precomputed traits, never Minecraft
 * objects on the hot path. Implementations are snapshots, overlays over
 * other views, or synthetic test worlds — the planner cannot tell them
 * apart, which is what makes hypothetical futures and MC-free benchmarks
 * possible.
 */
interface WorldView {
    /** Raw blockstate id ([Block.STATE_IDS]) at the given block position. */
    fun stateId(x: Int, y: Int, z: Int): Int

    fun stateId(pos: FastVector): Int = stateId(pos.x, pos.y, pos.z)

    fun traits(x: Int, y: Int, z: Int): BlockTraits = BlockTraitRegistry.of(stateId(x, y, z))

    fun traits(pos: FastVector): BlockTraits = traits(pos.x, pos.y, pos.z)
}

/**
 * Uncached pass-through to a live [BlockView]. For one-off queries on the
 * client thread (start-node checks, probes) where building a snapshot is
 * not worth it. Not thread-safe, not consistent across ticks.
 */
class LiveWorldView(private val world: BlockView) : WorldView {
    private val mutable = BlockPos.Mutable()

    override fun stateId(x: Int, y: Int, z: Int): Int =
        Block.STATE_IDS.getRawId(world.getBlockState(mutable.set(x, y, z)))
}
