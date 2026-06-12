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

package com.lambda.interaction.construction.simulation.result

import com.lambda.config.settings.blocks.ActionConfig
import com.lambda.interaction.construction.simulation.context.BreakContext
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.context.InteractContext
import com.lambda.interaction.managers.hotbar.HotbarManager
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils
import net.minecraft.block.Blocks

/**
 * Represents a result holding a [BuildContext].
 */
interface Contextual : ComparableResult<Rank> {
    val context: BuildContext

    override fun compareResult(other: ComparableResult<Rank>) = runSafe {
        when (other) {
            is Contextual -> compareBy<BuildContext> {
                if (it is InteractContext) BlockUtils.fluids.indexOf(it.cachedState.fluidState.fluid)
                else BlockUtils.fluids.size - 1
            }.thenByDescending {
                if (it is InteractContext && it.cachedState.fluidState.level != 0) it.blockPos.y
                else Int.MIN_VALUE
            }.thenByDescending {
                if (it is InteractContext) it.cachedState.fluidState.level
                else Int.MIN_VALUE
            }.thenByDescending {
                it.expectedState.block != Blocks.OBSERVER
            }.thenByDescending {
                context.sorter == ActionConfig.SortMode.Tool && it.hotbarIndex == HotbarManager.serverSlot
            }.thenBy {
                when (it.sorter) {
                    ActionConfig.SortMode.Tool,
                    ActionConfig.SortMode.Closest -> it.sortDistance
                    ActionConfig.SortMode.Farthest -> -it.sortDistance
                    ActionConfig.SortMode.Rotation -> it.rotationRequest dist RotationManager.activeRotation
                    ActionConfig.SortMode.Random -> it.random
                }
            }.thenByDescending {
                it is InteractContext && it.sneak == player.isSneaking
            }.thenByDescending {
                it.hotbarIndex == HotbarManager.serverSlot
            }.thenByDescending {
                it is BreakContext && it.instantBreak
            }.compare(context, other.context)

            else -> super.compareResult(other)
        }
    } ?: 0
}