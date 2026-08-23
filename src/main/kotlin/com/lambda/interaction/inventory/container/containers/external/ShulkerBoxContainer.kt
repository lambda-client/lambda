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
import com.lambda.interaction.handler.handlers.ContainerHandler.lastInteractedBlockEntity
import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.selectStack
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.ExternalContainer
import com.lambda.interaction.inventory.container.NestedContainer
import com.lambda.interaction.inventory.container.containers.HotbarContainer
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollect
import com.lambda.task.tasks.OpenContainerTask.Companion.openContainer
import com.lambda.task.tasks.PlaceContainerTask.Companion.placeContainer
import com.lambda.task.tasks.SimpleActionTask.Companion.simpleAction
import com.lambda.task.wrappers.TaskOrNullSupplier
import com.lambda.task.wrappers.then
import com.lambda.task.wrappers.thenOrNull
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos

data class ShulkerBoxContainer(
    override var stacks: List<ItemStack>,
    val containedIn: Container,
    override val slotCache: Slot,
) : Container(Rank.ShulkerBox), ExternalContainer, NestedContainer {
    override val slots
        get(): List<Slot> =
            if (isAccessed) mc.player?.currentScreenHandler?.containerSlots ?: emptyList()
            else emptyList()

    override val description =
        buildText {
            highlighted(slotCache.stack.name.string)
            literal(" in ")
            highlighted(containedIn.name)
            literal(" in slot $slotCache")
        }

    private var blockPos: BlockPos? = null

    override val isAccessed
        get() =
            lastInteractedBlockEntity?.let { blockEntity ->
                blockEntity is ShulkerBoxBlockEntity &&
                        blockEntity.pos == blockPos &&
                        mc.player?.currentScreenHandler?.type == ScreenHandlerType.SHULKER_BOX
            } ?: false

    context(automatedSafeContext: AutomatedSafeContext)
    override fun <R> accessThen(
        closeAfter: Boolean,
        afterOpen: TaskOrNullSupplier<Unit, R?>,
        afterClose: TaskOrNullSupplier<R?, *>
    ) =
        with(automatedSafeContext) {
            taskOrSkipOrNull(
                optional = {
                    if (isAccessed) null
                    else nullableWrappedTask<BlockPos>("Setup Shulker Box") { success ->
                        containedIn.accessThen(
                            afterOpen = {
                                if (player.currentScreenHandler.syncId == 0) null
                                else {
                                    val selection = selectStack { isSlot(slotCache) }
                                    containedIn.transferByTask(selection, HotbarContainer)
                                }
                            }
                        ) { slot ->
                            slot?.let { s ->
                                placeContainer(s).then { pos ->
                                    openContainer(pos).finally {
                                        success(pos)
                                    }
                                }
                            }
                        }
                    }
                }
            ) { pos ->
                taskOrSkipOrNull({ afterOpen(Unit) }) { result ->
                    if (closeAfter && pos != null) {
                        simpleAction("Close inventory") { player.closeHandledScreen() }.then {
                            breakAndCollect(pos).thenOrNull {
                                afterClose(result)
                            }
                        }
                    } else null
                }
            }
        }
}