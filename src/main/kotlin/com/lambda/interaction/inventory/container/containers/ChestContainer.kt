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

package com.lambda.interaction.inventory.container.containers

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.handlers.ContainerHandler
import com.lambda.interaction.inventory.container.ExternalContainer
import com.lambda.interaction.inventory.container.Container
import com.lambda.task.Task
import com.lambda.task.TaskGenerator
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
) : Container(Rank.Chest), ExternalContainer {
    context(safeContext: SafeContext)
    override val slots
        get(): List<Slot> =
            if (ContainerHandler.lastInteractedBlockEntity is ChestBlockEntity)
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

    context(automatedSafeContext: AutomatedSafeContext)
    override fun accessThen(exitAfter: Boolean, taskGenerator: TaskGenerator<Unit>): Task<*> =
        OpenContainerTask(blockPos, automatedSafeContext).then {
            taskGenerator.invoke(automatedSafeContext, Unit).finally {
                if (exitAfter) automatedSafeContext.player.closeHandledScreen()
            }
        }
}
