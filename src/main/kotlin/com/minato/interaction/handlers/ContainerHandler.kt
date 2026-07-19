
package com.minato.interaction.handlers

import com.minato.context.AutomatedSafeContext
import com.minato.context.SafeContext
import com.minato.core.Loadable
import com.minato.event.events.InventoryEvent
import com.minato.event.events.PlayerEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.material.ContainerSelection
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.StackSelection.Companion.select
import com.minato.interaction.material.container.MaterialContainer
import com.minato.interaction.material.container.containers.ChestContainer
import com.minato.interaction.material.container.containers.EnderChestContainer
import com.minato.util.BlockUtils.blockEntity
import com.minato.util.ReflectionUtils
import com.minato.util.extension.containerStacks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot

// ToDo: Make this a Configurable to save container caches. Should use a cached region based storage system.
@Suppress("unused")
object ContainerHandler : Loadable {
    private val containers: List<MaterialContainer>
        get() = compileContainers + runtimeContainers

    private val compileContainers = ReflectionUtils.getInstances<MaterialContainer>()
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

    context(automatedSafeContext: AutomatedSafeContext)
    fun StackSelection.findContainersWithMaterial(
	    containerSelection: ContainerSelection = automatedSafeContext.inventoryConfig.containerSelection,
    ): List<MaterialContainer> =
        containers()
            .filter { containerSelection.matches(it) }
            .filter { it.materialAvailable(this) >= count }
            .sortedWith(automatedSafeContext.inventoryConfig.providerPriority.materialComparator(this))

    context(automatedSafeContext: AutomatedSafeContext)
    fun StackSelection.findContainerWithSpace(
        containerSelection: ContainerSelection = automatedSafeContext.inventoryConfig.containerSelection
    ): MaterialContainer? = findContainersWithSpace(containerSelection).firstOrNull()

    context(automatedSafeContext: AutomatedSafeContext)
    fun StackSelection.findContainersWithSpace(
        containerSelection: ContainerSelection = automatedSafeContext.inventoryConfig.containerSelection
    ): List<MaterialContainer> =
        containers()
            .filter { containerSelection.matches(it) }
            .filter { it.spaceAvailable(this) >= count }
            .sortedWith(automatedSafeContext.inventoryConfig.providerPriority.spaceComparator(this))

    context(automatedSafeContext: AutomatedSafeContext)
    fun StackSelection.findSlotWithMaterial(
        containerSelection: ContainerSelection = automatedSafeContext.inventoryConfig.containerSelection
    ) = findSlotsWithMaterial(containerSelection).firstOrNull()

    context(automatedSafeContext: AutomatedSafeContext)
    fun StackSelection.findSlotsWithMaterial(
        containerSelection: ContainerSelection = automatedSafeContext.inventoryConfig.containerSelection
    ): List<Slot> =
        findContainersWithMaterial(containerSelection)
            .flatMap { filterSlots(it.slots) }


    context(automatedSafeContext: AutomatedSafeContext)
    fun findDisposable() = containers().find { container ->
        automatedSafeContext.inventoryConfig.disposables.any { container.materialAvailable(it.asItem().select()) > 0 }
    }

    class NoContainerFound(selection: StackSelection) : Exception("No container found matching $selection")
}