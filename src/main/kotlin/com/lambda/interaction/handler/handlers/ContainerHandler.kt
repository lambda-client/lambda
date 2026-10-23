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

@file:Suppress("unused")

package com.lambda.interaction.handler.handlers

import com.lambda.Lambda
import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ContainerDslMarker
import com.lambda.interaction.container.ContainerSerializer
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.PlacedContainer
import com.lambda.interaction.container.containers.ArmorContainer
import com.lambda.interaction.container.containers.CursorContainer
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.containers.InventoryContainer
import com.lambda.interaction.container.containers.OffhandContainer
import com.lambda.interaction.container.containers.external.ChestContainer
import com.lambda.interaction.container.containers.external.DoubleChestContainer
import com.lambda.interaction.container.containers.external.EnderChestContainer
import com.lambda.interaction.container.containers.external.PlacedShulkerBoxContainer
import com.lambda.interaction.container.containers.external.ShulkerBoxContainer
import com.lambda.interaction.container.identity
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.containerSelection
import com.lambda.interaction.container.selection.mutate
import com.lambda.interaction.container.selection.stackSelection
import com.lambda.interaction.handler.handlers.ContainerHandler.findContainer
import com.lambda.util.BlockUtils.blockEntity
import com.lambda.util.ReflectionUtils.getInstances
import com.lambda.util.extension.containerStacks
import com.lambda.util.item.ItemStackUtils.count
import com.lambda.util.item.ItemStackUtils.shulkerBoxStacks
import com.lambda.util.item.ItemUtils.SHULKER_BOXES
import com.lambda.util.player.SlotUtils.typeSafe
import com.lambda.util.world.Dimension
import net.minecraft.block.ChestBlock
import net.minecraft.block.ShulkerBoxBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.block.enums.ChestType
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.state.property.Properties

@Suppress("unused")
object ContainerHandler : Loadable {
	val compileContainers = getInstances<Container>()
	var accessedPlacedContainer: PlacedContainer? = null
		internal set

	var lastInteractedBlockEntity: BlockEntity? = null
		private set
	var pendingInteractedBlockEntity: BlockEntity? = null
		private set
	private var interactedSyncId: Int = 0

	override fun load(): String {
		return "Loaded ${compileContainers.size} containers"
	}

	init {
		listen<WorldEvent.Join> {
			ContainerSerializer.prepareIndex()
			ContainerSerializer.loadEnderChest()
		}

		listenUnsafe<WorldEvent.Leave> {
			ContainerSerializer.reset()
			lastInteractedBlockEntity = null
			pendingInteractedBlockEntity = null
			interactedSyncId = 0
			accessedPlacedContainer = null
		}

		listen<PacketEvent.Send.Post> { event ->
			val packet = event.packet as? PlayerInteractBlockC2SPacket ?: return@listen
			pendingInteractedBlockEntity = blockEntity(packet.blockHitResult.blockPos)
		}

		listen<InventoryEvent.Close> { event ->
			val sh = event.screenHandler
			onContainerUpdate(sh)
			if (interactedSyncId == sh.syncId) {
				lastInteractedBlockEntity = null
				interactedSyncId = 0
			}
			accessedPlacedContainer = null
		}

		listen<InventoryEvent.Open> { event ->
			val sh = event.screenHandler
			if (sh.syncId == 0) return@listen
			val pending = pendingInteractedBlockEntity
			if (pending == null) {
				lastInteractedBlockEntity = null
				interactedSyncId = 0
			} else {
				lastInteractedBlockEntity = pending
				interactedSyncId = sh.syncId
			}
			pendingInteractedBlockEntity = null
		}

		listen<InventoryEvent.FullUpdate>(priority = { Int.MAX_VALUE }) { onContainerUpdate() }
		listen<PlayerEvent.SlotClick.Post> { onContainerUpdate() }

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
			try {
				ContainerSerializer.savedIn(chunk.pos).forEach { (pos, type) ->
					val entity = chunk.blockEntities[pos]
					val stillThere =
						when (type) {
							ContainerType.Chest -> entity is ChestBlockEntity
							ContainerType.PlacedShulkerBox -> entity is ShulkerBoxBlockEntity
							else -> entity is ChestBlockEntity || entity is ShulkerBoxBlockEntity
						}
					if (!stillThere) ContainerSerializer.removeContainer(pos)
				}
			} catch (e: Exception) {
				Lambda.LOG.warn("Failed to clean up ghost containers for chunk ${chunk.pos}", e)
			}
		}
	}

	@ContainerDslMarker
	context(_: Automated)
	fun findSlot(
		stackSelection: StackSelection = StackSelection.EVERYTHING,
		containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
		sorted: Boolean = true
	) = findSlots(stackSelection, containerSelection, sorted)
		?.firstOrNull()

	@ContainerDslMarker
	context(_: Automated)
	fun findSlots(
		stackSelection: StackSelection = StackSelection.EVERYTHING,
		containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
		sorted: Boolean = true
	) = findContainers(containerSelection, sorted)
		.flatMap { stackSelection.filter(it.slots, sorted) }
		.takeIf { it.isNotEmpty() && (stackSelection.isUnbounded || it.count(stackSelection.countEmptyAsOne) >= stackSelection.count) }
		?.takeUpTo(stackSelection.count) { it.stack.count(stackSelection.countEmptyAsOne) }

	@ContainerDslMarker
	context(_: Automated)
	fun slotsAvailable(
		stackSelection: StackSelection = StackSelection.EVERYTHING,
		containerSelection: ContainerSelection = ContainerSelection.ACCESSED
	) = findSlots(stackSelection, containerSelection, false)
		?.isNotEmpty() == true

	@ContainerDslMarker
	context(_: Automated)
	fun findStack(
		stackSelection: StackSelection = StackSelection.EVERYTHING,
		containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
		sorted: Boolean = true
	) = findStacks(stackSelection, containerSelection, sorted)
		?.firstOrNull()

	@ContainerDslMarker
	context(_: Automated)
	fun findStacks(
		stackSelection: StackSelection = StackSelection.EVERYTHING,
		containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
		sorted: Boolean = true
	) = findContainers(containerSelection, sorted)
		.flatMap { stackSelection.filter(it.stacks, sorted) }
		.takeIf { it.isNotEmpty() && (stackSelection.isUnbounded || it.count(stackSelection.countEmptyAsOne) >= stackSelection.count) }
		?.takeUpTo(stackSelection.count) { it.count(stackSelection.countEmptyAsOne) }

	@ContainerDslMarker
	context(_: Automated)
	fun stacksAvailable(
		stackSelection: StackSelection = StackSelection.EVERYTHING,
		containerSelection: ContainerSelection = ContainerSelection.ACCESSED
	) = findStacks(stackSelection, containerSelection, false)
		?.isNotEmpty() == true

	@ContainerDslMarker
	context(_: Automated)
	fun findContainer(
		containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
		sorted: Boolean = true
	) = findContainers(containerSelection, sorted)
		.firstOrNull()

	@ContainerDslMarker
	context(_: Automated)
	fun findContainers(
		containerSelection: ContainerSelection = ContainerSelection.ACCESSED,
		sorted: Boolean = true
	) = containerSelection
		.filter(searchContainers(containerSelection).asIterable(), sorted)

	@ContainerDslMarker
	context(_: Automated)
	fun containerAvailable(
		containerSelection: ContainerSelection= ContainerSelection.ACCESSED
	) = findContainer(containerSelection, false) != null

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

	context(_: Automated)
	private fun searchContainers(selection: ContainerSelection): Sequence<Container> {
		fun Container.allNested(): Sequence<Container> =
			sequence {
				yield(this@allNested)
				storedContainers.values.forEach { nested ->
					yieldAll(nested.allNested())
				}
			}

		val compiled = compileContainers.asSequence()
		val placedSeq = sequenceOf(accessedPlacedContainer).filterNotNull()
		val savedContainers =
			if (selection.dimensionWhitelist.isEmpty() && selection.dimensionBlacklist.isEmpty()) {
				ContainerSerializer.serializedContainers
			} else {
				val dimensions = selection.dimensionWhitelist.ifEmpty { Dimension.entries }
				ContainerSerializer.serializedByDimensions(
					*dimensions.filterNot { it in selection.dimensionBlacklist }.toTypedArray()
				)
			}

		val baseContainers =
			when (selection.scope) {
				ContainerSearchScope.Loaded -> compiled + placedSeq + selection.loadedContainers
				ContainerSearchScope.Accessed -> placedSeq + compiled.filter { it.isAccessed }
				ContainerSearchScope.Compiled -> compiled
				ContainerSearchScope.Player -> compiled - EnderChestContainer
				ContainerSearchScope.All -> compiled + placedSeq +
						savedContainers.filter { diskContainer ->
							val accessed = accessedPlacedContainer
							accessed == null || !diskContainer.haveMatchingInventories(accessed)
						}
			}

		return baseContainers
			.flatMap { it.allNested() }
			.distinct()
			.filter { it.isAccessible }
	}

	private fun <T> List<T>.takeUpTo(need: Int, countOf: (T) -> Int): List<T> {
		if (need <= 0) return this
		var sum = 0
		val taken = ArrayList<T>()
		for (item in this) {
			taken += item
			sum += countOf(item)
			if (sum >= need) break
		}
		return taken
	}

	private fun SafeContext.onContainerUpdate(sh: ScreenHandler = player.currentScreenHandler) {
		InventoryContainer.scanContainerContents()
		HotbarContainer.scanContainerContents()
		OffhandContainer.scanContainerContents()
		CursorContainer.scanContainerContents()
		val blockEntity = lastInteractedBlockEntity
		if (blockEntity != null && interactedSyncId == sh.syncId) {
			updatePlacedContainer(sh, blockEntity)
			return
		}

		ArmorContainer.scanContainerContents()
	}

	private fun SafeContext.updatePlacedContainer(
		sh: ScreenHandler,
		blockEntity: BlockEntity
	) {
		val pos = blockEntity.pos
		if (blockEntity(pos) !== blockEntity) return
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

				val stacks = sh.containerStacks
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
					if (existing != null && (existing.leftPos == leftPos || existing.rightPos == rightPos)) {
						existing.update(stacks)
					} else {
						accessedPlacedContainer = DoubleChestContainer(pos, leftPos, rightPos, stacks)
					}
				}

				accessedPlacedContainer?.let {
					it.scanContainerContents()
					ContainerSerializer.saveContainer(it)
					if (it is DoubleChestContainer) {
						val counterpartPos = if (it.pos == it.leftPos) it.rightPos else it.leftPos
						val counterpart = DoubleChestContainer(counterpartPos, it.leftPos, it.rightPos, it.stacks, it.stash)
						counterpart.storedContainers.putAll(it.storedContainers)
						ContainerSerializer.saveContainer(counterpart)
					}
				}
			}

			is ShulkerBoxBlockEntity -> {
				if (sh.typeSafe != ScreenHandlerType.SHULKER_BOX) return
				val stacks = sh.containerStacks
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
			val slotIdentity = slot.identity()
			if (stack.item in SHULKER_BOXES && slotIdentity != null) {
				storedContainers[index] =
					ShulkerBoxContainer(
						stack.name.string,
						stack.item,
						stack.shulkerBoxStacks,
						this,
						slotIdentity
					).also { it.scanContainerContents() }
			} else storedContainers.remove(index)
		}
	}
}

@ContainerDslMarker
context(_: AutomatedSafeContext)
fun StackSelection.moveStack(
	fromSelection: ContainerSelection = ContainerSelection.ACCESSED,
	toSelection: ContainerSelection = ContainerSelection.ACCESSED,
	replaceSelection: StackSelection = StackSelection.EVERYTHING
): Boolean {
	val fromContainer =
		findContainer(
			containerSelection(fromSelection.scope) {
				matches(fromSelection)
				hasStack(mutate(count.coerceAtMost(64)))
				isAccessed()
			}
		)
			?: return false

	val toContainer =
		findContainer(
			containerSelection(toSelection.scope) {
				matches(toSelection)
				hasStack(replaceSelection)
				isAccessed()
			}
		) ?: return false

	val (fromSlot, toSlot) = fromContainer.findMoveSlots(this, toContainer, replaceSelection)
	if (fromSlot == null || toSlot == null) return false

	return fromContainer.swap(fromSlot, toSlot, toContainer)
}

enum class ContainerSearchScope {
	Loaded,
	Accessed,
	Compiled,
	Player,
	All
}
