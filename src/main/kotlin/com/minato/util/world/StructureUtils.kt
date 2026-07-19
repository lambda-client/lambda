
package com.minato.util.world

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.EightWayDirection

object StructureUtils {
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
