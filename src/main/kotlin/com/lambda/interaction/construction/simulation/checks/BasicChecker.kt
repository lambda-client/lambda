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

package com.lambda.interaction.construction.simulation.checks

import com.lambda.context.AutomatedSafeContext
import com.lambda.interaction.construction.simulation.BreakSimInfo
import com.lambda.interaction.construction.simulation.Results
import com.lambda.interaction.construction.simulation.SimDsl
import com.lambda.interaction.construction.simulation.SimInfo
import com.lambda.interaction.construction.simulation.result.results.GenericResult
import com.lambda.interaction.construction.simulation.result.results.PreSimResult
import com.lambda.util.player.gamemode
import com.lambda.util.world.WorldUtils.isLoaded
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
        if (this@hasBasicRequirements is BreakSimInfo && state.block !in breakConfig.blocks) {
            result(GenericResult.Ignored(pos))
            return false
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