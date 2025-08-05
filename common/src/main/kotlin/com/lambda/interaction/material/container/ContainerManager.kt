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

import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.core.Loadable
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.ContainerSelection
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.containers.ChestContainer
import com.lambda.interaction.material.container.containers.EnderChestContainer
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.blockEntity
import com.lambda.util.BlockUtils.item
import com.lambda.util.Communication.info
import com.lambda.util.extension.containerStacks
import com.lambda.util.reflections.getInstances
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType

// ToDo: Make this a Configurable to save container caches. Should use a cached region based storage system.
object ContainerManager : Loadable {
    private val container: List<MaterialContainer>
        // ToDo: Filter containers based on a filter setting TaskFlowModule.inventory.accessEnderChest etc
        get() = compileContainers.filter { it !is EnderChestContainer } + runtimeContainers

    private val compileContainers = getInstances<MaterialContainer>()
    private val runtimeContainers = mutableSetOf<MaterialContainer>()

    private var lastInteractedBlockEntity: BlockEntity? = null

    override fun load() = "Loaded ${compileContainers.size} containers"

    init {
        listen<PlayerEvent.Interact.Block> {
            lastInteractedBlockEntity = blockEntity(it.blockHitResult.blockPos)
        }

        listen<InventoryEvent.Close> { event ->
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

    fun StackSelection.transfer(destination: MaterialContainer, inventory: InventoryConfig = TaskFlowModule.inventory) =
        findContainerWithMaterial(inventory)?.transfer(this, destination)

    fun findContainer(
        block: (MaterialContainer) -> Boolean,
    ): MaterialContainer? = container().find(block)

    fun StackSelection.findContainerWithMaterial(
        inventory: InventoryConfig
    ): MaterialContainer? =
        containerWithMaterial(inventory).firstOrNull()

    fun findContainerWithSpace(
        selection: StackSelection,
    ): MaterialContainer? =
        containerWithSpace(selection).firstOrNull()

    fun StackSelection.containerWithMaterial(
        inventory: InventoryConfig = TaskFlowModule.inventory,
        containerSelection: ContainerSelection = inventory.containerSelection,
    ): List<MaterialContainer> =
        container()
            .sortedWith(inventory.providerPriority.materialComparator(this))
            .filter { it.materialAvailable(this) >= count }
            .filter { containerSelection.matches(it) }

    fun containerWithSpace(
        selection: StackSelection,
        inventory: InventoryConfig = TaskFlowModule.inventory,
    ): List<MaterialContainer> =
        container()
            .sortedWith(inventory.providerPriority.spaceComparator(selection))
            .filter { it.spaceAvailable(selection) >= selection.count }
            .filter { inventory.containerSelection.matches(it) }

    fun findBestAvailableTool(
        blockState: BlockState,
        availableTools: Set<Item> = ItemUtils.tools,
        inventory: InventoryConfig = TaskFlowModule.inventory,
    ) = availableTools.map {
        it to it.getMiningSpeed(it.defaultStack, blockState)
    }.filter { (item, speed) ->
        speed > 1.0
                && item.defaultStack.isSuitableFor(blockState)
                && containerWithMaterial(item.select(), inventory).isNotEmpty()
    }.maxByOrNull {
        it.second
    }?.first

    fun findDisposable(inventory: InventoryConfig = TaskFlowModule.inventory) = container().find { container ->
        inventory.disposables.any { container.materialAvailable(it.item.select()) > 0 }
    }

    class NoContainerFound(selection: StackSelection) : Exception("No container found matching $selection")
}
