
package com.minato.interaction.construction.simulation.result

import com.minato.util.Nameable
import net.minecraft.util.math.BlockPos

abstract class BuildResult : Nameable, ComparableResult<Rank> {
    abstract val pos: BlockPos
    override val compareBy = this

    final override fun compareTo(other: ComparableResult<Rank>) = super.compareTo(other)
}