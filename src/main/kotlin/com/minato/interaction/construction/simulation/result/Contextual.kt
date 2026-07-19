
package com.minato.interaction.construction.simulation.result

import com.minato.config.blocks.ActionConfig
import com.minato.interaction.construction.simulation.context.BreakContext
import com.minato.interaction.construction.simulation.context.BuildContext
import com.minato.interaction.construction.simulation.context.InteractContext
import com.minato.interaction.managers.hotbar.HotbarManager
import com.minato.interaction.managers.rotating.RotationManager
import com.minato.threading.runSafe
import com.minato.util.BlockUtils
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