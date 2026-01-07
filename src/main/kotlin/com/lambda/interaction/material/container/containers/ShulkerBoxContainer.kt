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

package com.lambda.interaction.material.container.containers

import com.lambda.context.SafeContext
import com.lambda.interaction.material.container.ContainerManager
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.threading.runSafe
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

data class ShulkerBoxContainer(
    override var stacks: List<ItemStack>,
    val containedIn: MaterialContainer,
    val shulkerSlot: Slot,
) : MaterialContainer(Rank.ShulkerBox) {
    context(safeContext: SafeContext)
    override val slots
        get(): List<Slot> =
            if (ContainerManager.lastInteractedBlockEntity is ShulkerBoxBlockEntity)
                safeContext.player.currentScreenHandler.containerSlots
            else emptyList()

    override val description =
        buildText {
            highlighted(shulkerSlot.stack.name.string)
            literal(" in ")
            highlighted(containedIn.name)
            literal(" in slot ")
            highlighted("${runSafe { slotInContainer }}")
        }

    context(_: SafeContext)
    private val slotInContainer: Int get() = containedIn.slots.indexOf(shulkerSlot)
}
