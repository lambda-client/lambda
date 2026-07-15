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

package com.lambda.interaction.handler.handlers

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.inventory.ContainerMarker
import com.lambda.interaction.inventory.ContainerSelection
import com.lambda.interaction.inventory.StackSelection
import com.lambda.interaction.inventory.StackSelectionBuilder.Companion.select
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.containers.external.ChestContainer
import com.lambda.interaction.inventory.container.containers.external.EnderChestContainer
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.util.BlockUtils.blockEntity
import com.lambda.util.ReflectionUtils.getInstances
import com.lambda.util.extension.containerStacks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType

// ToDo: Make this a Configurable to save container caches. Should use a cached region based storage system.
@Suppress("unused")
object ContainerHandler : Loadable {
    private val containers: List<Container>
        get() = compileContainers + runtimeContainers

    private val compileContainers = getInstances<Container>()
    private val runtimeContainers = mutableSetOf<Container>()

    context(automated: Automated)
    val filteredContainers
        get() =
            containers
                .flatMap { setOf(it) + it.shulkerContainers }
                .filter { automated.inventoryConfig.containerSelection.matches(it) }
                .sorted()

    val allContainers
        get() =
            containers
                .flatMap { setOf(it) + it.shulkerContainers }
                .sorted()

    var lastInteractedBlockEntity: BlockEntity? = null

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

                    EnderChestContainer.update(handler.containerStacks)
                }

                is ChestBlockEntity -> {
                    // ToDo: Handle double chests and single chests
                    if (handler.type != ScreenHandlerType.GENERIC_9X6) return@listen
                    val stacks = handler.containerStacks

                    containers
                        .filterIsInstance<ChestContainer>()
                        .find {
                            it.blockPos == block.pos
                        }?.update(stacks) ?: runtimeContainers.add(ChestContainer(block.pos, stacks))
                }
            }
            lastInteractedBlockEntity = null
        }
    }

    @ContainerMarker
    context(automatedSafeContext: AutomatedSafeContext)
    fun StackSelection.transfer(destination: Container) =
        with(automatedSafeContext) {
            findContainer(inventoryConfig.containerSelection)
                ?.transfer(this@transfer, destination)
                ?: false
        }

    @ContainerMarker
    context(_: Automated)
    fun findContainer(block: (Container) -> Boolean) = filteredContainers.find(block)

    @ContainerMarker
    context(automated: Automated)
    fun StackSelection.findContainer(
        containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
    ) = findContainers(containerSelection).firstOrNull()

    @ContainerMarker
    context(automated: Automated)
    fun StackSelection.findContainers(
	    containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
    ) =
        filteredContainers
            .filter { containerSelection.matches(it) }
            .filter { it.stackCount(this) >= count }
            .sortedWith(automated.inventoryConfig.providerPriority.materialComparator(this))

    @ContainerMarker
    context(automated: Automated)
    fun StackSelection.findContainerWithSpace(
        containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
    ) = findContainersWithSpace(containerSelection).firstOrNull()

    @ContainerMarker
    context(automated: Automated)
    fun StackSelection.findContainersWithSpace(
        containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
    ) =
        filteredContainers
            .filter { containerSelection.matches(it) }
            .filter { it.spaceAvailable(this) >= count }
            .sortedWith(automated.inventoryConfig.providerPriority.spaceComparator(this))

    @ContainerMarker
    context(automated: Automated)
    fun StackSelection.findSlot(
        containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
    ) = findSlots(containerSelection).firstOrNull()

    @ContainerMarker
    context(automated: Automated)
    fun StackSelection.findSlots(
        containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
    ) = findContainers(containerSelection).flatMap { filter(it.slots) }

    @ContainerMarker
    context(automated: Automated)
    fun findDisposable() =
        filteredContainers.find { container ->
            automated.inventoryConfig.disposables.any { container.stackCount(it.asItem().select(1)) > 0 }
        }

    class NoContainerFound(selection: StackSelection) : Exception("No container found matching $selection")
}