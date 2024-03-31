package com.lambda.interaction.material

import com.lambda.context.SafeContext
import com.lambda.event.events.InteractionEvent
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.*
import com.lambda.module.modules.client.TaskFlow
import com.lambda.util.Communication.info
import com.lambda.util.item.ItemUtils
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.primitives.extension.containerStacks
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandlerType
import java.util.TreeSet

// ToDo: Make this a Configurable to save container caches. Should use a cached region based storage system.
object ContainerManager {
    // ToDo: Maybe use reflection to get all containers?
    val container = TreeSet<MaterialContainer>().apply {
        add(CreativeContainer)
        add(EnderChestContainer)
        add(HotbarContainer)
        add(InventoryContainer)
        add(MainHandContainer)
        add(OffHandContainer)
    }

    private var lastInteractedBlockEntity: BlockEntity? = null

    init {
        listener<InteractionEvent.Block> {
            lastInteractedBlockEntity = world.getBlockEntity(it.blockHitResult.blockPos)
        }

        listener<ScreenHandlerEvent.Close<GenericContainerScreenHandler>> { event ->
            val handler = event.screen

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
                        }?.update(stacks) ?: container.add(ChestContainer(stacks, block.pos))
                }
            }
            lastInteractedBlockEntity = null
        }
    }

    fun findContainer(
        block: (MaterialContainer) -> Boolean
    ): MaterialContainer? = container.find(block)

    fun findContainerWithSelection(
        selection: StackSelection
    ): MaterialContainer? =
        container.find { it.available(selection) >= selection.count }

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

//    fun SafeContext.nextDisposable() = player.combined.firstOrNull { it.item in TaskFlow.disposables }

    class NoContainerFound(selection: StackSelection): Exception("No container found matching $selection")
}