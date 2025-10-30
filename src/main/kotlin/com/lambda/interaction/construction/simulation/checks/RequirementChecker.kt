/*
 * Copyright 2025 Lambda
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
import com.lambda.interaction.construction.result.results.GenericResult
import com.lambda.interaction.construction.result.results.PreSimResult
import com.lambda.interaction.construction.simulation.SimBuilderDsl
import com.lambda.interaction.construction.simulation.SimChecker
import com.lambda.interaction.construction.simulation.SimInfo
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.util.player.gamemode
import com.lambda.util.world.WorldUtils.isLoaded
import net.minecraft.block.OperatorBlock

object RequirementChecker : SimChecker<PreSimResult>() {
    @SimBuilderDsl
    context(automatedSafeContext: AutomatedSafeContext)
    fun SimInfo.checkRequirements(): Boolean = with(automatedSafeContext) {
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
        if (state.block in breakConfig.ignoredBlocks && targetState.type == TargetState.Type.Air) {
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
        if (!world.worldBorder.contains(pos) || world.isOutOfHeightLimit(pos)) {
            result(PreSimResult.OutOfWorld(pos))
            return false
        }

        // block is unbreakable, so it cant be broken or replaced
        if (state.getHardness(world, pos) < 0 && !gamemode.isCreative) {
            result(PreSimResult.Unbreakable(pos, state))
            return false
        }

        return true
    }
}