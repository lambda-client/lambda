package me.zeroeightsix.kami.util.math

import me.zeroeightsix.kami.util.Waypoint
import net.minecraft.util.math.BlockPos

object CoordinateConverter {
    fun toCurrent(dimension: Int, pos: BlockPos): BlockPos {
        return if (dimension == Waypoint.genDimension()) {
            pos
        } else {
            if (dimension == -1) {
                toOverworld(pos)
            } else {
                toNether(pos)
            }
        }
    }

    fun bothConverted(dimension: Int, pos: BlockPos): String {
        return if (dimension == -1) {
            "${toOverworld(pos)} ($pos)"
        } else {
            "$pos (${toNether(pos)})"
        }
    }

    fun toNether(pos: BlockPos): BlockPos {
        return BlockPos(pos.x / 8, pos.y, pos.z / 8)
    }

    fun toOverworld(pos: BlockPos): BlockPos {
        return BlockPos(pos.x * 8, pos.y, pos.z * 8)
    }
}