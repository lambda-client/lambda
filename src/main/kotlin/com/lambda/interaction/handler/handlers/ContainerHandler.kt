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
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.ContainerHandler.filteredContainers
import com.lambda.interaction.inventory.ContainerMarker
import com.lambda.interaction.inventory.ContainerSelection
import com.lambda.interaction.inventory.StackSelection
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.PlacedContainer
import com.lambda.interaction.inventory.container.containers.external.ChestContainer
import com.lambda.interaction.inventory.container.containers.external.DoubleChestContainer
import com.lambda.interaction.inventory.container.containers.external.EnderChestContainer
import com.lambda.interaction.inventory.container.containers.external.PlacedShulkerBoxContainer
import com.lambda.interaction.inventory.container.containers.external.ShulkerBoxContainer
import com.lambda.interaction.inventory.select
import com.lambda.util.BlockUtils.blockEntity
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.ReflectionUtils.getInstances
import com.lambda.util.extension.containerStacks
import com.lambda.util.item.ItemStackUtils.shulkerBoxStacks
import com.lambda.util.item.ItemUtils.shulkerBoxes
import com.lambda.util.player.SlotUtils.typeSafe
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.block.enums.ChestType
import net.minecraft.inventory.Inventory
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos

@Suppress("unused")
object ContainerHandler : Loadable {
    private val containers: Sequence<Container>
        get() {
            fun Container.allNested(): Sequence<Container> =
                sequence {
                    val nested = storedContainers.values
                    yieldAll(nested)
                    nested.forEach { yieldAll(it.allNested()) }
                }
            val baseContainers = compileContainers.asSequence() +
                    placedContainers.values.asSequence().flatMap { it.values }
            return baseContainers + baseContainers.flatMap { it.allNested() }
        }

    val compileContainers = getInstances<Container>()
    val placedContainers = mutableMapOf<ChunkPos, MutableMap<BlockPos, PlacedContainer>>()

    context(automated: Automated)
    val filteredContainers
        get() = containers
            .filter { automated.inventoryConfig.containerSelection.matches(it) }
            .sorted()

    val allContainers
        get() = containers.sorted()

    var lastInteractedBlockEntity: BlockEntity? = null
    var pendingInteractedBlockEntity: BlockEntity? = null

    override fun load() = "Loaded ${compileContainers.size} containers"

    init {
        listen<PacketEvent.Send.Post> { event ->
            val packet = event.packet as? PlayerInteractBlockC2SPacket ?: return@listen
            val entity = blockEntity(packet.blockHitResult.blockPos)
            pendingInteractedBlockEntity = entity
            lastInteractedBlockEntity = entity
        }

        listen<InventoryEvent.Close> { event ->
            val sh = event.screenHandler
            onContainerUpdate(sh)
            if (sh.syncId != 0 && pendingInteractedBlockEntity == null) {
                lastInteractedBlockEntity = null
            }
        }

        listen<InventoryEvent.Open> {
            if (it.screenHandler.syncId != 0) {
                pendingInteractedBlockEntity = null
            }
        }

        listen<InventoryEvent.FullUpdate> { onContainerUpdate() }
        listen<InventoryEvent.SlotUpdate> { onContainerUpdate() }

        listen<WorldEvent.BlockUpdate.Server> { event ->
            val pos = event.pos
            val containersInChunk = placedContainers[ChunkPos(pos)] ?: return@listen
            val cachedContainer = containersInChunk[pos] ?: return@listen
            val currentState = blockState(cachedContainer.pos)
            if (currentState.block != event.newState.block) {
                containersInChunk.remove(pos)
            }
        }

        listen<WorldEvent.ChunkEvent.Load> { event ->
            val chunk = event.chunk
            val cachedContainers = placedContainers[chunk.pos] ?: return@listen
            cachedContainers.values.retainAll { container ->
                val matchingEntity = chunk.blockEntities[container.pos] ?: return@retainAll false
                blockState(container.pos).block == matchingEntity.cachedState.block
            }
        }
    }

    private fun SafeContext.onContainerUpdate(sh: ScreenHandler = player.currentScreenHandler) {
        val blockEntity = lastInteractedBlockEntity
        if (blockEntity != null) updatePlacedContainer(sh, blockEntity)
    }

    private fun updatePlacedContainer(
        sh: ScreenHandler,
        blockEntity: BlockEntity
    ) {
        val pos = blockEntity.pos
        when (blockEntity) {
            is EnderChestBlockEntity -> {
                if (sh.typeSafe != ScreenHandlerType.GENERIC_9X3) return
                EnderChestContainer.apply {
                    update(sh.containerStacks)
                    scanContainerContents()
                }
            }

            is ChestBlockEntity -> {
                val state = blockEntity.cachedState
                if (state.block !is ChestBlock) return

                val stacks = blockEntity.stacks
                val chestType = state.get(Properties.CHEST_TYPE) ?: return
                val chunkPos = ChunkPos(pos)

                if (chestType == ChestType.SINGLE) {
                    if (sh.typeSafe != ScreenHandlerType.GENERIC_9X3) return
                    (chunkPos.getPlacedContainer(pos) as? ChestContainer)
                        ?.update(stacks)
                        ?: run { chunkPos.setPlacedContainer(pos, ChestContainer(pos, stacks)) }
                } else {
                    if (sh.typeSafe != ScreenHandlerType.GENERIC_9X6) return
                    val facing = state.get(Properties.HORIZONTAL_FACING) ?: return
                    val otherPos =
                        when (chestType) {
                            ChestType.LEFT -> pos.offset(facing.rotateYClockwise())
                            ChestType.RIGHT -> pos.offset(facing.rotateYCounterclockwise())
                        }
                    val leftPos = if (chestType == ChestType.LEFT) pos else otherPos
                    val leftChunkPos = ChunkPos(leftPos)
                    val rightPos = if (chestType == ChestType.RIGHT) pos else otherPos
                    val rightChunkPos = ChunkPos(rightPos)

                    val existing = (leftChunkPos.getPlacedContainer(leftPos) as? DoubleChestContainer)
                        ?: (rightChunkPos.getPlacedContainer(rightPos) as? DoubleChestContainer)

                    if (existing != null) {
                        existing.update(stacks)
                        leftChunkPos.setPlacedContainer(leftPos, existing)
                        rightChunkPos.setPlacedContainer(rightPos, existing)
                    } else {
                        val doubleChest = DoubleChestContainer(pos, leftPos, rightPos, stacks)
                        leftChunkPos.setPlacedContainer(leftPos, doubleChest)
                        rightChunkPos.setPlacedContainer(rightPos, doubleChest)
                    }
                }

                chunkPos.getPlacedContainer(pos)?.scanContainerContents()
            }

            is ShulkerBoxBlockEntity -> {
                if (sh.typeSafe != ScreenHandlerType.SHULKER_BOX) return
                val stacks = blockEntity.stacks
                val chunkPos = ChunkPos(pos)
                (chunkPos.getPlacedContainer(pos) as? PlacedShulkerBoxContainer)
                    ?.update(stacks)
                    ?: run {
                        chunkPos.setPlacedContainer(
                            pos,
                            PlacedShulkerBoxContainer(pos, blockEntity.cachedState.block, null, stacks)
                        )
                    }

                chunkPos.getPlacedContainer(pos)?.scanContainerContents()
            }
        }
    }

    private fun Container.scanContainerContents() {
        slots.forEach { slot ->
            val stack = slot.stack
            val index = slot.index
            if (stack.item in shulkerBoxes) {
                storedContainers[index] =
                    ShulkerBoxContainer(
                        stack.name.string,
                        stack.item,
                        stack.shulkerBoxStacks,
                        this,
                        index
                    ).also { it.scanContainerContents() }
            } else storedContainers.remove(index)
        }
    }

    private fun getPlacedContainer(pos: BlockPos) = placedContainers[ChunkPos(pos)]?.get(pos)

    private fun ChunkPos.getPlacedContainer(pos: BlockPos) = placedContainers[this]?.get(pos)

    private fun setPlacedContainer(pos: BlockPos, container: PlacedContainer) =
        placedContainers.getOrPut(ChunkPos(pos)) { mutableMapOf() }.put(pos, container)

    private fun ChunkPos.setPlacedContainer(pos: BlockPos, container: PlacedContainer) =
        placedContainers.getOrPut(this) { mutableMapOf() }.put(pos, container)

    private val Inventory.stacks
        get() = iterator().asSequence().toList()
}

@ContainerMarker
context(automatedSafeContext: AutomatedSafeContext)
fun StackSelection.move(destination: Container) =
    with(automatedSafeContext) {
        findContainer(inventoryConfig.containerSelection)
            ?.move(this@move, destination)
            ?: false
    }

@ContainerMarker
context(_: Automated)
fun findContainer(predicate: (Container) -> Boolean) = filteredContainers.find(predicate)

@ContainerMarker
context(automated: Automated)
fun StackSelection.findContainer(
    containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
) = findContainers(containerSelection).firstOrNull()

@ContainerMarker
context(automated: Automated)
fun StackSelection.findContainers(
    containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
) = filteredContainers
    .filter { containerSelection.matches(it) }
    .filter { it.stackCount(this) >= count }
    .sortedWith(automated.inventoryConfig.accessPriority.materialComparator(this))

@ContainerMarker
context(automated: Automated)
fun StackSelection.findContainerWithSpace(
    containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
) = findContainersWithSpace(containerSelection).firstOrNull()

@ContainerMarker
context(automated: Automated)
fun StackSelection.findContainersWithSpace(
    containerSelection: ContainerSelection = automated.inventoryConfig.containerSelection
) = filteredContainers
    .filter { containerSelection.matches(it) }
    .filter { it.spaceAvailable(this) >= count }
    .sortedWith(automated.inventoryConfig.accessPriority.spaceComparator(this))

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