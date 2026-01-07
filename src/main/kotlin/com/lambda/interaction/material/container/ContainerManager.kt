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

package com.lambda.interaction.material.container

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.ContainerSelection
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.containers.ChestContainer
import com.lambda.interaction.material.container.containers.EnderChestContainer
import com.lambda.util.BlockUtils.blockEntity
import com.lambda.util.extension.containerStacks
import com.lambda.util.reflections.getInstances
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType

// ToDo: Make this a Configurable to save container caches. Should use a cached region based storage system.
object ContainerManager : Loadable {
    private val containers: List<MaterialContainer>
        get() = compileContainers + runtimeContainers

    private val compileContainers = getInstances<MaterialContainer>()
    private val runtimeContainers = mutableSetOf<MaterialContainer>()

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
                        }?.update(stacks) ?: runtimeContainers.add(ChestContainer(stacks, block.pos))
                }
            }
            lastInteractedBlockEntity = null
        }
    }

    context(_: SafeContext)
    fun containers() = containers.flatMap { setOf(it) + it.shulkerContainer }.sorted()

    context(automatedSafeContext: AutomatedSafeContext)
    fun StackSelection.transfer(destination: MaterialContainer) =
        with(automatedSafeContext) {
            findContainerWithMaterial(
                inventoryConfig.containerSelection
            )?.transfer(this@transfer, destination)
                ?: false
        }

    context(automatedSafeContext: AutomatedSafeContext)
    fun StackSelection.transferByTask(destination: MaterialContainer) =
        with(automatedSafeContext) {
            findContainerWithMaterial()?.transferByTask(this@transferByTask, destination)
        }

    context(_: SafeContext)
    fun findContainer(
        block: (MaterialContainer) -> Boolean,
    ): MaterialContainer? = containers().find(block)

    context(automatedSafeContext: AutomatedSafeContext)
    fun StackSelection.findContainerWithMaterial(
        containerSelection: ContainerSelection = automatedSafeContext.inventoryConfig.containerSelection
    ): MaterialContainer? = findContainersWithMaterial(containerSelection).firstOrNull()

    context(_: AutomatedSafeContext)
    fun findContainerWithSpace(selection: StackSelection): MaterialContainer? =
        findContainersWithSpace(selection).firstOrNull()

    context(automated: Automated, safeContext: SafeContext)
    fun StackSelection.findContainersWithMaterial(
        containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection,
    ): List<MaterialContainer> =
        containers()
            .filter { containerSelection.matches(it) }
            .filter { it.materialAvailable(this) >= count }
            .sortedWith(automated.inventoryConfig.providerPriority.materialComparator(this))

    context(automatedSafeContext: AutomatedSafeContext)
    fun findContainersWithSpace(
        selection: StackSelection,
    ): List<MaterialContainer> =
        containers()
            .filter { it.spaceAvailable(selection) >= selection.count }
            .filter { automatedSafeContext.inventoryConfig.containerSelection.matches(it) }
            .sortedWith(automatedSafeContext.inventoryConfig.providerPriority.spaceComparator(selection))

    context(automatedSafeContext: AutomatedSafeContext)
    fun findDisposable() = containers().find { container ->
        automatedSafeContext.inventoryConfig.disposables.any { container.materialAvailable(it.asItem().select()) > 0 }
    }

    class NoContainerFound(selection: StackSelection) : Exception("No container found matching $selection")
}
