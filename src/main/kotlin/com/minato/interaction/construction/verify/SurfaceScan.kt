
package com.minato.interaction.construction.verify

import net.minecraft.util.math.Direction

data class SurfaceScan(
    val mode: ScanMode,
    val axis: Direction.Axis
) {
    companion object {
        val DEFAULT = SurfaceScan(ScanMode.Full, Direction.Axis.Y)
    }
}
