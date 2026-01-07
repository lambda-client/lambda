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

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.material.container.ContainerManager
import com.lambda.interaction.material.container.ExternalContainer
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos

data class ChestContainer(
    override var stacks: List<ItemStack>,
    val blockPos: BlockPos,
    val containedInStash: StashContainer? = null
) : MaterialContainer(Rank.Chest), ExternalContainer {
    context(safeContext: SafeContext)
    override val slots
        get(): List<Slot> =
            if (ContainerManager.lastInteractedBlockEntity is ChestBlockEntity)
                safeContext.player.currentScreenHandler.containerSlots
            else emptyList()

    override val description =
        buildText {
            literal("Chest at ")
            highlighted(blockPos.toShortString())
            containedInStash?.let { stash ->
                literal(" (contained in ")
                highlighted(stash.name)
                literal(")")
            }
        }

    context(automated: Automated)
    override fun access() = OpenContainerTask(blockPos, automated)
}
