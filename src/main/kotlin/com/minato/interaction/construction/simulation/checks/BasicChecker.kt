
package com.minato.interaction.construction.simulation.checks

import com.minato.config.blocks.BreakConfig.WhitelistMode
import com.minato.context.AutomatedSafeContext
import com.minato.interaction.construction.simulation.BreakSimInfo
import com.minato.interaction.construction.simulation.Results
import com.minato.interaction.construction.simulation.SimDsl
import com.minato.interaction.construction.simulation.SimInfo
import com.minato.interaction.construction.simulation.result.results.GenericResult
import com.minato.interaction.construction.simulation.result.results.PreSimResult
import com.minato.util.player.PlayerUtils.gamemode
import com.minato.util.world.WorldUtils.isLoaded
import net.minecraft.block.OperatorBlock

object BasicChecker : Results<PreSimResult> {
    /**
     * A sequence of basic checks to make sure that the block is worth simulating.
     */
    @SimDsl
    context(automatedSafeContext: AutomatedSafeContext)
    fun SimInfo.hasBasicRequirements(): Boolean = with(automatedSafeContext) {
        // the chunk is not loaded
        if (!isLoaded(pos)) {
            result(PreSimResult.ChunkNotLoaded(pos))
            return false
        }

        // block is already in the correct state
        if (targetState.matches(state, pos)) {
            result(PreSimResult.Done(pos))
            return false
        }

        // block should be ignored
        if (this@hasBasicRequirements is BreakSimInfo) {
            val mode = breakConfig.whitelistMode
            if ((mode == WhitelistMode.Whitelist && state.block !in breakConfig.whitelist) ||
                (mode == WhitelistMode.Blacklist && state.block in breakConfig.blacklist)
                ) {
                result(GenericResult.Ignored(pos))
                return false
            }
        }

        // the player is in the wrong game mode to alter the block state
        if (player.isBlockBreakingRestricted(world, pos, gamemode)) {
            result(PreSimResult.Restricted(pos))
            return false
        }

        // the player has no permissions to alter the block state
        if (state.block is OperatorBlock && !player.isCreativeLevelTwoOp) {
            result(PreSimResult.NoPermission(pos, state))
            return false
        }

        // block is outside the world so it cant be altered
        if (world.isOutOfHeightLimit(pos)) {
            result(PreSimResult.OutOfHeightLimit(pos))
            return false
        }

        // block is unbreakable, so it can't be broken or replaced
        if (state.getHardness(world, pos) < 0 && !gamemode.isCreative) {
            result(PreSimResult.Unbreakable(pos, state))
            return false
        }

        return true
    }
}