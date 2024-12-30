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

package com.lambda.interaction.material.container

import com.lambda.core.Loadable
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.containers.ChestContainer
import com.lambda.interaction.material.container.containers.EnderChestContainer
import com.lambda.module.modules.client.TaskFlowModule
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
        listen<PlayerEvent.Interact.Block> {
            lastInteractedBlockEntity = it.blockHitResult.blockPos.blockEntity(world)
        }

        listen<ScreenHandlerEvent.Close> { event ->
            if (event.screenHandler !is GenericContainerScreenHandler) return@listen

            val handler = event.screenHandler

            when (val block = lastInteractedBlockEntity) {
                is EnderChestBlockEntity -> {
                    if (handler.type != ScreenHandlerType.GENERIC_9X3) return@listen

                    this@ContainerManager.info("Updating EnderChestContainer")
                    EnderChestContainer.update(handler.containerStacks)
                }

                is ChestBlockEntity -> {
                    // ToDo: Handle double chests and single chests
                    if (handler.type != ScreenHandlerType.GENERIC_9X6) return@listen
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

    fun container() = container.flatMap { setOf(it) + it.shulkerContainer }.sorted()

    fun StackSelection.transfer(destination: MaterialContainer) =
        findContainerWithMaterial(this)?.transfer(this, destination)

    fun findContainer(
        block: (MaterialContainer) -> Boolean
    ): MaterialContainer? = container().find(block)

    fun findContainerWithMaterial(
        selection: StackSelection
    ): MaterialContainer? =
        containerWithMaterial(selection).firstOrNull()

    fun findContainerWithSpace(
        selection: StackSelection
    ): MaterialContainer? =
        containerWithSpace(selection).firstOrNull()

    fun containerWithMaterial(
        selection: StackSelection
    ): List<MaterialContainer> =
        container()
            .sortedWith(TaskFlowModule.inventory.providerPriority.materialComparator(selection))
            .filter { it.materialAvailable(selection) >= selection.count }

    fun containerWithSpace(
        selection: StackSelection
    ): List<MaterialContainer> =
        container()
            .sortedWith(TaskFlowModule.inventory.providerPriority.spaceComparator(selection))
            .filter { it.spaceAvailable(selection) >= selection.count }

    fun findBestAvailableTool(
        blockState: BlockState,
        availableTools: Set<Item> = ItemUtils.tools,
    ) = availableTools.map {
        it to it.getMiningSpeedMultiplier(it.defaultStack, blockState)
    }.filter { (item, speed) ->
        speed > 1.0
                && item.isSuitableFor(blockState)
                && containerWithMaterial(item.select()).isNotEmpty()
    }.maxByOrNull {
        it.second
    }?.first

    fun findDisposable() = container().find { container ->
        TaskFlowModule.disposables.any { container.materialAvailable(it.item.select()) >= 0 }
    }

    class NoContainerFound(selection: StackSelection) : Exception("No container found matching $selection")
}
