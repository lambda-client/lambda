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

package com.lambda.interaction.inventory.container.containers.external

import com.lambda.Lambda.mc
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.handlers.ContainerHandler.lastInteractedBlockEntity
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.ExternalContainer
import com.lambda.task.Task
import com.lambda.task.TaskGenerator
import com.lambda.task.TaskOrNullGenerator
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.task.tasks.PlaceContainerTask
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
    val containedIn: Container,
    val shulkerSlot: Slot?,
) : Container(Rank.ShulkerBox), ExternalContainer {
    override val slots
        get(): List<Slot> =
            if (lastInteractedBlockEntity is ShulkerBoxBlockEntity)
                mc.player?.currentScreenHandler?.containerSlots ?: emptyList()
            else emptyList()

    override val description =
        buildText {
            highlighted(shulkerSlot?.stack?.name?.string ?: "Shulker Box")
            literal(" in ")
            highlighted(containedIn.name)
            literal(" in slot ")
            highlighted("${runSafe { slotInContainer }}")
        }

    context(_: SafeContext)
    private val slotInContainer: Int get() = containedIn.slots.indexOf(shulkerSlot)

    override val isAccessed get() = lastInteractedBlockEntity is ShulkerBoxBlockEntity

    context(automatedSafeContext: AutomatedSafeContext)
    override fun accessThen(closeAfter: Boolean, afterClose: TaskOrNullGenerator<Unit>?, afterOpen: TaskGenerator<Unit>): Task<*> {
        return PlaceContainerTask(shulkerSlot, automatedSafeContext).then { pos ->
            OpenContainerTask(pos, automatedSafeContext).then {
                afterOpen.invoke(automatedSafeContext, Unit).thenOrNull {
                    if (closeAfter) {
                        player.closeHandledScreen()
                        automatedSafeContext.breakAndCollectBlock(pos)
                    } else null
                }
            }
        }
    }
}