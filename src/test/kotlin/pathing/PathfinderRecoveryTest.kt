/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.manager.footprintSupportedStance
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.worldview.BlockTraits
import com.lambda.worldview.WorldView
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals

class PathfinderRecoveryTest {
    private val air = BlockTraits(
        passable = true,
        centerPassable = true,
        standableFullTop = false,
        collisionTopBlips = 0,
        exact = true,
    )
    private val stone = BlockTraits(
        passable = false,
        centerPassable = false,
        standableFullTop = true,
        collisionTopBlips = 16,
        exact = true,
    )

    private class TraitWorld(
        private val air: BlockTraits,
        private val stone: BlockTraits,
        private val support: Set<FastVector>,
    ) : WorldView {
        override fun stateId(x: Int, y: Int, z: Int): Int = 0
        override fun traits(x: Int, y: Int, z: Int): BlockTraits =
            if (fastVectorOf(x, y, z) in support) stone else air
    }

    @Test
    fun `lost recovery anchors to an overlapped neighbouring stance`() {
        val world = TraitWorld(air, stone, setOf(fastVectorOf(12, 64, -10)))

        val recovered = footprintSupportedStance(
            view = world,
            playerPosition = Vec3d(11.70, 65.0, -9.83),
            playerBlock = BlockPos(11, 65, -10),
        )

        assertEquals(fastVectorOf(12, 65, -10), recovered)
    }
}
