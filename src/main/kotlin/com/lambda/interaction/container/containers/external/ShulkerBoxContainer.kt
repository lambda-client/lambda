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

package com.lambda.interaction.container.containers.external

import com.lambda.Lambda.mc
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.ExternalContainer
import com.lambda.interaction.container.NestedContainer
import com.lambda.interaction.container.OpenContainerTask
import com.lambda.interaction.container.OpenedContainerContext
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.selectStack
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.breakAndCollect
import com.lambda.task.tasks.openContainer
import com.lambda.task.tasks.placeContainer
import com.lambda.task.tasks.transfer
import com.lambda.task.tasks.transferTo
import com.lambda.task.tasks.wrappers.actionTask
import com.lambda.task.tasks.wrappers.taskOrNull
import com.lambda.task.tasks.wrappers.then
import com.lambda.util.extension.containerSlots
import com.lambda.util.text.buildText
import com.lambda.util.text.highlighted
import com.lambda.util.text.literal
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos
import java.util.Objects.hash

data class ShulkerBoxContainer(
    val itemName: String,
    val shulkerItem: Item,
    override var stacks: List<ItemStack>,
    override val containedIn: Container,
    override val index: Int,
) : NestedContainer(ContainerType.ShulkerBox), ExternalContainer {
    override val slots
        get(): List<Slot> =
            if (isAccessed) mc.player?.currentScreenHandler?.containerSlots ?: emptyList()
            else emptyList()

    override val description =
        buildText {
            highlighted(itemName)
            literal(" in ")
            highlighted(containedIn.name)
            literal(" in slot index $index")
        }

    override val isAccessed = false

    @Ta5kBuilder
    context(automated: Automated)
    override fun access() = OpenShulkerBoxTask(automated)

    inner class OpenShulkerBoxTask @Ta5kBuilder internal constructor(
        automated: Automated
    ) : OpenContainerTask<OpenedShulkerBoxContext>(description), Automated by automated {
        override fun SafeContext.onStart() {
            transfer(
                selectStack { inIndex(index); isItem(shulkerItem) },
                containedIn,
                HotbarContainer
            ).then { slot -> placeContainer(slot) }
                .then { pos ->
                    openContainer(pos).onSuccess {
                        success(OpenedShulkerBoxContext(pos, containedIn))
                    }
                }
                .start()
        }
    }

    inner class OpenedShulkerBoxContext(
        val blockPos: BlockPos,
        val fromContainer: Container
    ) : OpenedContainerContext {
        context(automated: Automated)
        override fun close() =
            taskOrNull {
                if (!isAccessed) null
                else actionTask { player.closeScreen() }
            }.then { breakAndCollect(blockPos) }
                .then {
                    selectStack {
                        isItem(shulkerItem)
                        hasCustomName(itemName)
                        sortedByBestContentMatch(stacks)
                    }.transferTo()
                    transfer(
                        selectStack {
                            isItem(shulkerItem)
                            hasCustomName(itemName)
                            sortedByBestContentMatch(stacks)
                        },
                        HotbarAndInventoryContainer,
                        fromContainer
                    )
                }
    }

    override fun hashCode() = hash(containedIn.hashCode(), index)

    override fun equals(other: Any?) =
        other is ShulkerBoxContainer &&
                containedIn == other.containedIn &&
                index == other.index
}