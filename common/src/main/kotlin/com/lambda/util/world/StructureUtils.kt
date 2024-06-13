package com.lambda.util.world

import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.EightWayDirection

object StructureUtils {
    val PORTAL = setOf(
        BlockPos(0, 0, 0),
        BlockPos(0, 1, 0),
        BlockPos(0, 2, 0),
        BlockPos(0, 3, 0),
        BlockPos(1, 3, 0),
        BlockPos(2, 3, 0),
        BlockPos(3, 3, 0),
        BlockPos(1, -1, 0),
        BlockPos(2, -1, 0),
        BlockPos(3, 0, 0),
        BlockPos(3, 1, 0),
        BlockPos(3, 2, 0),
        BlockPos(3, 3, 0),
    )
    val LIGHT_UP = mapOf(
        BlockPos(1, 0, 0) to Blocks.FIRE.defaultState,
    )

    /**
     * Generates a tube of blocks in the specified direction.
     *
     * @param direction The direction in which the tube should be generated.
     * @param width The width of the tube.
     * @param height The height of the tube.
     * @param leftRightOffset The offset along the X-axis.
     * @param heightOffset The offset along the Y-axis.
     * @return A set of BlockPos representing the generated tube.
     */
    fun generateDirectionalTube(
        direction: EightWayDirection,
        width: Int,
        height: Int,
        leftRightOffset: Int,
        heightOffset: Int,
    ): Set<BlockPos> {
        val tube = mutableSetOf<BlockPos>()

        val offsetX = leftRightOffset * direction.offsetX
        val offsetZ = leftRightOffset * direction.offsetZ

        (heightOffset until heightOffset + height).forEach { y ->
            (0 until width).forEach { x ->
                (0 until width).forEach { z ->
                    tube.add(
                        BlockPos(
                            offsetX + x * direction.offsetX,
                            y,
                            offsetZ + z * direction.offsetZ,
                        ),
                    )
                }
            }
        }

        return tube
    }
}
