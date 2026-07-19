
package com.minato.interaction.construction.simulation.result.results

import com.minato.interaction.construction.simulation.result.BuildResult
import com.minato.interaction.construction.simulation.result.Rank
import net.minecraft.util.math.BlockPos

sealed class PostSimResult : BuildResult() {
    override val name: String get() = "${this::class.simpleName} at ${pos.toShortString()}"

    /**
     * No result can be found for the given position.
     */
    data class NoMatch(
        override val pos: BlockPos,
    ) : PostSimResult() {
        override val rank = Rank.NoMatch
    }
}