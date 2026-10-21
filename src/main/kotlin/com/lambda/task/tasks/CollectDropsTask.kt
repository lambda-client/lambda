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

package com.lambda.task.tasks

import baritone.api.pathing.goals.GoalBlock
import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.container.identity
import com.lambda.interaction.container.selection.ContainerSelection
import com.lambda.interaction.container.selection.StackSelection
import com.lambda.interaction.container.selection.stackSelection
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.interaction.handler.handlers.ContainerHandler.findSlot
import com.lambda.interaction.handler.handlers.ContainerHandler.stacksAvailable
import com.lambda.interaction.manager.managers.inventory.inventoryRequest
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.threading.runSafeAutomated
import com.lambda.util.player.SlotUtils.hotbarAndInventorySlots
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap

@Ta5kBuilder
context(automated: Automated)
fun collectDrops(drops: MutableSet<ItemEntity>) = CollectDropsTask(drops, automated)

data class PickedUpItem(
	val stack: ItemStack,
	val slot: SlotIdentity?
)

class CollectDropsTask @Ta5kBuilder internal constructor(
	private val drops: MutableSet<ItemEntity>,
	automated: Automated
) : Task<List<PickedUpItem>>(), Automated by automated {
	override val name get() = "Collecting ${drops.size} item drop(s)"

	private val pickups = mutableListOf<PickedUpItem>()
	private var lastSeen: Map<Slot, ItemStack>? = null
	private var lastSeenSync: Int? = null
	private val attributed = mutableMapOf<ItemEntity, Int>()
	private val closingAge = mutableMapOf<ItemEntity, Int>()
	private val dropStacks = mutableMapOf<ItemEntity, ItemStack>()
	private val packets = ConcurrentHashMap<Int, Int>()
	private val lost = mutableMapOf<ItemEntity, Int?>()
	private var selfId = -1
	private var lastGoal: BlockPos? = null

	init {
		onCompletion { BaritoneHandler.cancel() }

		listen<PacketEvent.Receive.Pre> { event ->
			val packet = event.packet as? ItemPickupAnimationS2CPacket ?: return@listen
			packets[packet.entityId] = packet.collectorEntityId
		}

		listen<TickEvent.Pre> {
			runSafeAutomated {
				pollNextItem()
			}
		}
	}

	private fun AutomatedSafeContext.pollNextItem() {
		val live = liveStacks()
		val syncId = player.currentScreenHandler.syncId
		selfId = player.id
		drops.forEach { stackOf(it) }
		val previous = lastSeen
		if (previous != null && lastSeenSync == syncId) {
			attributeGrowth(previous, live)
		}
		ageClosing()
		lastSeen = live
		lastSeenSync = syncId

		val gone = drops.filter { !world.entities.contains(it) }
		gone.forEach { drop ->
			if (isTakenByOther(drop)) lost[drop] = packets[drop.id]
			else if (remainderOf(drop) > 0) closingAge.putIfAbsent(drop, 0)
		}
		if (gone.isNotEmpty()) {
			drops.removeAll(gone.toSet())
			BaritoneHandler.cancel()
		}

		val target = drops.firstOrNull()
		if (target == null) {
			if (closingAge.isEmpty()) {
				BaritoneHandler.cancel()
				if (lost.isEmpty()) success(pickups.toList())
				else failure(describeLost())
			}
			return
		}

		val emptySlotAvailable =
			stacksAvailable(
				stackSelection(StackSelection.UNBOUNDED) {
					isEmpty()
				},
				ContainerSelection.HOTBAR_AND_INVENTORY
			)
		if (!emptySlotAvailable) {
			val slotToThrow =
				findSlot(
					stackSelection { ofAnyItems(inventoryConfig.disposables) },
					ContainerSelection.HOTBAR_AND_INVENTORY,
					sorted = false
				) ?: run {
					failure("Inventory is full and no disposable items to throw")
					return
				}

			inventoryRequest {
				throwStack(slotToThrow.id)
			}.submit()
			return
		}

		if (target.blockPos != lastGoal) {
			lastGoal = target.blockPos
			BaritoneHandler.setGoalAndPath(GoalBlock(target.blockPos))
		}
	}

	private fun AutomatedSafeContext.liveStacks() =
		player.hotbarAndInventorySlots.associateWith { it.stack.copy() }

	private fun stackOf(drop: ItemEntity): ItemStack {
		val live = drop.stack
		val snap = dropStacks[drop]
		if (live.isEmpty) return snap ?: ItemStack.EMPTY
		if (snap == null || !ItemStack.areItemsAndComponentsEqual(live, snap) || live.count > snap.count) {
			dropStacks[drop] = live.copy()
		}
		return dropStacks[drop] ?: ItemStack.EMPTY
	}

	private fun remainderOf(drop: ItemEntity) = stackOf(drop).count - (attributed[drop] ?: 0)

	private fun attributeGrowth(before: Map<Slot, ItemStack>, live: Map<Slot, ItemStack>) {
		live.forEach { (slot, now) ->
			if (now.isEmpty) return@forEach
			val was = before[slot]?.takeIf { ItemStack.areItemsAndComponentsEqual(it, now) }?.count ?: 0
			if (now.count <= was) return@forEach
			consumeGrowth(now, now.count - was, slot)
		}
	}

	private fun isTakenByOther(drop: ItemEntity) = packets[drop.id]?.let { it != selfId } == true

	private fun consumeGrowth(stack: ItemStack, count: Int, slot: Slot) {
		var left = count
		for (drop in closingAge.keys.toList()) {
			if (left <= 0) break
			left -= takeFrom(drop, stack, left, slot)
		}
		if (left <= 0) return
		drops.forEach { drop ->
			if (left <= 0) return
			left -= takeFrom(drop, stack, left, slot)
		}
	}

	private fun takeFrom(drop: ItemEntity, stack: ItemStack, left: Int, slot: Slot): Int {
		if (isTakenByOther(drop)) return 0
		if (!matchesDrop(stack, stackOf(drop))) return 0
		val take = minOf(left, remainderOf(drop))
		if (take <= 0) return 0
		pickups += PickedUpItem(stack.copyWithCount(take), slot.identity())
		attributed[drop] = (attributed[drop] ?: 0) + take
		if (remainderOf(drop) <= 0) closingAge.remove(drop)
		return take
	}

	private fun matchesDrop(stack: ItemStack, snap: ItemStack): Boolean {
		if (stack.isEmpty || snap.isEmpty) return false
		return stack.item == snap.item && stack.name.string == snap.name.string
	}

	private fun ageClosing() {
		closingAge.entries.toList().forEach { (drop, age) ->
			if (age < CLOSE_GRACE_TICKS) closingAge[drop] = age + 1
			else {
				val collector = packets.remove(drop.id)
				val left = remainderOf(drop)
				if (collector == selfId) {
					if (left > 0) pickups += PickedUpItem(stackOf(drop).copyWithCount(left), null)
					attributed.remove(drop)
				} else {
					lost[drop] = collector
				}
				closingAge.remove(drop)
			}
		}
	}

	private fun AutomatedSafeContext.describeLost(): String {
		val parts = lost.map { (drop, collector) ->
			val what = stackOf(drop).let { "${remainderOf(drop)}x ${it.name.string}" }
			val who = collector?.let { id -> world.getEntityById(id)?.name?.string } ?: "unknown causes"
			"$what lost to $who"
		}
		return "Failed to collect ${lost.size} drop(s): ${parts.joinToString("; ")}"
	}

	private companion object {
		const val CLOSE_GRACE_TICKS = 20
	}
}
