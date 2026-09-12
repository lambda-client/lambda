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

import com.lambda.Lambda
import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ContainerDslMarker
import com.lambda.interaction.container.ContainerSerializer
import com.lambda.interaction.container.PlacedContainer
import com.lambda.interaction.container.containers.external.ChestContainer
import com.lambda.interaction.container.containers.external.DoubleChestContainer
import com.lambda.interaction.container.containers.external.EnderChestContainer
import com.lambda.interaction.container.containers.external.PlacedShulkerBoxContainer
import com.lambda.interaction.container.containers.external.ShulkerBoxContainer
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.ContainerSelectionBuilder.Companion.containerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.mutate
import com.lambda.interaction.container.selection.StackSelectionBuilder.Companion.stackSelection
import com.lambda.util.BlockUtils.blockEntity
import com.lambda.util.FolderRegistry
import com.lambda.util.ReflectionUtils.getInstances
import com.lambda.util.extension.containerStacks
import com.lambda.util.item.ItemStackUtils.count
import com.lambda.util.item.ItemStackUtils.shulkerBoxStacks
import com.lambda.util.item.ItemUtils.SHULKER_BOXES
import com.lambda.util.player.SlotUtils.typeSafe
import net.minecraft.block.ChestBlock
import net.minecraft.block.ShulkerBoxBlock
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
import java.nio.file.Files

@Suppress("unused")
object ContainerHandler : Loadable {
	val compileContainers = getInstances<Container>()
	var accessedPlacedContainer: PlacedContainer? = null
		private set

	var lastInteractedBlockEntity: BlockEntity? = null
	var pendingInteractedBlockEntity: BlockEntity? = null

	override fun load(): String {
		ContainerSerializer.loadEnderChest()
		return "Loaded ${compileContainers.size} containers"
	}

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
			accessedPlacedContainer = null
		}

		listen<InventoryEvent.Open> {
			if (it.screenHandler.syncId != 0) {
				pendingInteractedBlockEntity = null
			}
		}

		listen<InventoryEvent.FullUpdate> { onContainerUpdate() }
		listen<InventoryEvent.SlotUpdate> { onContainerUpdate() }

		listen<WorldEvent.BlockUpdate.Client> { event ->
			val oldBlock = event.oldState.block
			if (oldBlock is ChestBlock || oldBlock is ShulkerBoxBlock) {
				if (event.newState.block != oldBlock) {
					ContainerSerializer.removeContainer(event.pos)
				}
			}
		}

		listen<WorldEvent.ChunkEvent.Load> { event ->
			val chunk = event.chunk
			val prefix = "${chunk.pos.x}_${chunk.pos.z}_"
			
			try {
				val dir = FolderRegistry.containers
				if (Files.exists(dir)) {
					Files.newDirectoryStream(dir, "$prefix*.json").use { stream ->
						stream.forEach { path ->
							val parts = path.fileName.toString().removeSuffix(".json").split('_')
							if (parts.size == 5) {
								val x = parts[2].toIntOrNull() ?: return@forEach
								val y = parts[3].toIntOrNull() ?: return@forEach
								val z = parts[4].toIntOrNull() ?: return@forEach
								val pos = BlockPos(x, y, z)
								val entity = chunk.blockEntities[pos]
								if (entity !is ChestBlockEntity && entity !is ShulkerBoxBlockEntity) {
									Files.deleteIfExists(path)
								}
							}
						}
					}
				}
			} catch (e: Exception) {
				Lambda.LOG.warn("Failed to clean up ghost containers for chunk ${chunk.pos}", e)
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
				ContainerSerializer.saveEnderChest()
			}

			is ChestBlockEntity -> {
				val state = blockEntity.cachedState
				if (state.block !is ChestBlock) return

				val stacks = blockEntity.stacks()
				val chestType = state.get(Properties.CHEST_TYPE) ?: return

				if (chestType == ChestType.SINGLE) {
					if (sh.typeSafe != ScreenHandlerType.GENERIC_9X3) return
					(accessedPlacedContainer as? ChestContainer)
						?.update(stacks)
						?: run { accessedPlacedContainer = ChestContainer(pos, stacks) }
				} else {
					if (sh.typeSafe != ScreenHandlerType.GENERIC_9X6) return
					val facing = state.get(Properties.HORIZONTAL_FACING) ?: return
					val otherPos =
						when (chestType) {
							ChestType.LEFT -> pos.offset(facing.rotateYClockwise())
							ChestType.RIGHT -> pos.offset(facing.rotateYCounterclockwise())
						}
					val leftPos = if (chestType == ChestType.LEFT) pos else otherPos
					val rightPos = if (chestType == ChestType.RIGHT) pos else otherPos

					val existing = accessedPlacedContainer as? DoubleChestContainer
					if (existing != null && existing.pos == pos) {
						existing.update(stacks)
					} else {
						accessedPlacedContainer = DoubleChestContainer(pos, leftPos, rightPos, stacks)
					}
				}

				accessedPlacedContainer?.let {
					it.scanContainerContents()
					ContainerSerializer.saveContainer(it)
				}
			}

			is ShulkerBoxBlockEntity -> {
				if (sh.typeSafe != ScreenHandlerType.SHULKER_BOX) return
				val stacks = blockEntity.stacks()
				(accessedPlacedContainer as? PlacedShulkerBoxContainer)
					?.update(stacks)
					?: run {
						accessedPlacedContainer = PlacedShulkerBoxContainer(pos, blockEntity.cachedState.block, null, stacks)
					}

				accessedPlacedContainer?.let {
					it.scanContainerContents()
					ContainerSerializer.saveContainer(it)
				}
			}
		}
	}

	private fun Container.scanContainerContents() {
		slots.forEach { slot ->
			val stack = slot.stack
			val index = slot.index
			if (stack.item in SHULKER_BOXES) {
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

	private fun Inventory.stacks() = iterator().asSequence().toList()
}

@ContainerDslMarker
context(_: AutomatedSafeContext)
fun StackSelection.move(
	fromSelection: ContainerSelection = ContainerSelection.ACCESSED,
	toSelection: ContainerSelection = ContainerSelection.ACCESSED,
	replaceSelection: StackSelection = StackSelection.ANYTHING
): Boolean {
	val fromContainer =
		findContainer(
			containerSelection {
				matches(fromSelection)
				hasStack(mutate(count.coerceAtMost(64)))
				isAccessed()
			}
		)
		?: return false

	val toContainer =
		findContainer(
			containerSelection {
				matches(toSelection)
				hasStack(replaceSelection)
				isAccessed()
			}
		)
		?: return false

	val (fromSlot, toSlot) = fromContainer.findMoveSlots(this, toContainer, replaceSelection)
	if (fromSlot == null || toSlot == null) return false

	return fromContainer.swap(fromSlot, toSlot, toContainer)
}

@ContainerDslMarker
context(automated: Automated)
fun findSlot(
	stackSelection: StackSelection = StackSelection.ANYTHING,
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
	sorted: Boolean = true
) = findSlots(stackSelection, containerSelection, sorted)
	.firstOrNull()

@ContainerDslMarker
context(automated: Automated)
fun findSlots(
	stackSelection: StackSelection = StackSelection.ANYTHING,
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
	sorted: Boolean = true
) = searchContainers(containerSelection)
	.filter { containerSelection.matches(it) }
	.let {
		if (sorted) it.sorted()
		else it
	}
	.mapNotNull { container ->
		val slots = stackSelection.filter(container.slots)
		if (slots.count >= stackSelection.count) slots
		else null
	}
	.flatten()

@ContainerDslMarker
context(automated: Automated)
fun findStack(
	stackSelection: StackSelection = StackSelection.ANYTHING,
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
	sorted: Boolean = true
) = findStacks(stackSelection, containerSelection, sorted)
	.firstOrNull()

@ContainerDslMarker
context(automated: Automated)
fun findStacks(
	stackSelection: StackSelection = StackSelection.ANYTHING,
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
	sorted: Boolean = true
) = searchContainers(containerSelection)
	.filter { containerSelection.matches(it) }
	.let {
		if (sorted) it.sorted()
		else it
	}
	.mapNotNull { container ->
		val stacks = stackSelection.filter(container.stacks)
		if (stacks.count >= stackSelection.count) stacks
		else null
	}
	.flatten()

@ContainerDslMarker
context(_: Automated)
fun findContainer(
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
	sorted: Boolean = true
) = findContainers(containerSelection, sorted)
	.firstOrNull()

@ContainerDslMarker
context(automated: Automated)
fun findContainers(
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
	sorted: Boolean = true
) = searchContainers(containerSelection)
	.filter { containerSelection.matches(it) }
	.let {
		if (sorted) it.sorted()
		else it
	}

@ContainerDslMarker
context(_: Automated)
fun findContainerWithDisposable(
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
	sorted: Boolean = true
) = findContainersWithDisposable(containerSelection, sorted)
	.firstOrNull()

@ContainerDslMarker
context(automated: Automated)
fun findContainersWithDisposable(
	containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
	sorted: Boolean = true
) =
	with(automated) {
		findContainers(
			containerSelection(containerSelection.scope) {
				matches(containerSelection)
				hasStack(
					stackSelection(1) { ofAnyItems(inventoryConfig.disposables) }
				)
			},
			sorted
		)
	}

context(automated: Automated)
private fun searchContainers(
	selection: ContainerSelection
): Sequence<Container> {
	fun Container.allNested(): Sequence<Container> =
		sequence {
			yield(this@allNested)
			storedContainers.values.forEach { nested ->
				yieldAll(nested.allNested())
			}
		}

	val compiled = ContainerHandler.compileContainers.asSequence()
	val placedSeq = sequenceOf(ContainerHandler.accessedPlacedContainer).filterNotNull()

	val baseContainers =
		when (selection.scope) {
			ContainerSearchScope.Player -> compiled - EnderChestContainer
			ContainerSearchScope.Compiled -> compiled
			ContainerSearchScope.Loaded -> compiled + placedSeq + selection.loadedContainers
			ContainerSearchScope.Accessed -> placedSeq + compiled.filter { it.isAccessed }
			ContainerSearchScope.All -> compiled + placedSeq +
					ContainerSerializer.serializedContainers.filter { diskContainer ->
						diskContainer.pos != ContainerHandler.accessedPlacedContainer?.pos
					}
		}

	return baseContainers
		.flatMap { it.allNested() }
		.filter { automated.inventoryConfig.containerSelection.matches(it) }
}

enum class ContainerSearchScope {
	Accessed,
	Compiled,
	Loaded,
	Player,
	All
}