/*
 * Copyright 2024 Lambda
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

package com.lambda.interaction.material

import com.lambda.core.Loadable
import com.lambda.event.events.InteractionEvent
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.*
import com.lambda.module.modules.client.TaskFlow
import com.lambda.util.BlockUtils.blockEntity
import com.lambda.util.BlockUtils.item
import com.lambda.util.Communication.info
import com.lambda.util.item.ItemUtils
import com.lambda.util.extension.containerStacks
import com.lambda.util.reflections.getInstances
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType

// ToDo: Make this a Configurable to save container caches. Should use a cached region based storage system.
object ContainerManager : Loadable {
    private val container: List<MaterialContainer>
        get() = compileContainers + runtimeContainers

    private val compileContainers =
        getInstances<MaterialContainer> { forPackages("com.lambda.interaction.material.container") }
    private val runtimeContainers = mutableSetOf<MaterialContainer>()

    private var lastInteractedBlockEntity: BlockEntity? = null

    init {
        listener<InteractionEvent.Block> {
            lastInteractedBlockEntity = it.blockHitResult.blockPos.blockEntity(world)
        }

        listener<ScreenHandlerEvent.Close> { event ->
            if (event.screenHandler !is GenericContainerScreenHandler) return@listener

            val handler = event.screenHandler

            when (val block = lastInteractedBlockEntity) {
                is EnderChestBlockEntity -> {
                    if (handler.type != ScreenHandlerType.GENERIC_9X3) return@listener

                    this@ContainerManager.info("Updating EnderChestContainer")
                    EnderChestContainer.update(handler.containerStacks)
                }

                is ChestBlockEntity -> {
                    // ToDo: Handle double chests and single chests
                    if (handler.type != ScreenHandlerType.GENERIC_9X6) return@listener
                    val stacks = handler.containerStacks

                    this@ContainerManager.info("Updating ChestContainer")
                    container
                        .filterIsInstance<ChestContainer>()
                        .find {
                            it.blockPos == block.pos
                        }?.update(stacks) ?: runtimeContainers.add(ChestContainer(stacks, block.pos))
                }
            }
            lastInteractedBlockEntity = null
        }
    }

    fun container() = container.flatMap {
        setOf(it) + it.shulkerContainer
    }.sorted()

    fun StackSelection.transfer(destination: MaterialContainer) =
        findContainerWithSelection(this)?.transfer(this, destination)

    fun findContainer(
        block: (MaterialContainer) -> Boolean
    ): MaterialContainer? = container().find(block)

    fun findContainerWithSelection(
        selection: StackSelection
    ): MaterialContainer? =
        container().find { it.available(selection) >= selection.count }

    fun containerMatchSelection(
        selection: StackSelection
    ): Set<MaterialContainer> =
        container().filter { it.available(selection) >= selection.count }.toSet()

    fun findContainerWithSelection(
        selectionBuilder: StackSelection.() -> Unit
    ): MaterialContainer? {
        val selection = StackSelection().apply(selectionBuilder)
        return container().find { it.available(selection) >= selection.count }
    }

    fun findContainerWithStacks(
        count: Int = StackSelection.DEFAULT_AMOUNT,
        selection: (ItemStack) -> Boolean,
    ): MaterialContainer? =
        findContainerWithSelection(selection.select())

    fun findBestAvailableTool(
        blockState: BlockState,
        availableTools: Set<Item> = ItemUtils.tools,
    ) = availableTools.map {
        it to it.getMiningSpeedMultiplier(it.defaultStack, blockState)
    }.filter { (item, speed) ->
        speed > 1.0
                && item.isSuitableFor(blockState)
                && findContainerWithSelection(item.select()) != null
    }.maxByOrNull {
        it.second
    }?.first

    fun findDisposable() = container().find { container ->
        TaskFlow.disposables.any { container.available(it.item.select()) >= 0 }
    }

    class NoContainerFound(selection: StackSelection) : Exception("No container found matching $selection")
}
