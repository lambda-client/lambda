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

package com.lambda.interaction.material.container.containers

import com.lambda.context.SafeContext
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.util.math.roundedBlockPos
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.Box

data class StashContainer(
    val container: Set<LootableContainer>,
    val pos: Box,
) : MaterialContainer(Rank.Stash) {
    context(_: SafeContext)
    override val slots: List<Slot>
        get() = container.flatMap { it.slots }
    override var stacks: List<ItemStack>
        get() = container.flatMap { it.stacks }
        set(_) {}

    override val description = buildText {
        literal("Stash at ")
        highlighted(pos.center.roundedBlockPos.toShortString())
    }

    context(_: SafeContext)
    override fun materialAvailable(selection: StackSelection): Int =
        container.sumOf {
            it.materialAvailable(selection)
        }
}
