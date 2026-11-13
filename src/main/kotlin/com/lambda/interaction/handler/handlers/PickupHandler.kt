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

import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.EventFlow.post
import com.lambda.event.events.EntityEvent
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PickupSlot
import com.lambda.event.events.PickupSlotKind
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.manager.managers.inventory.InventoryManager
import com.lambda.util.item.ItemStackUtils.itemAndComponentsEqual
import com.lambda.util.world.fastEntitySearch
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack

@Suppress("unused")
object PickupHandler : Loadable {
	private const val SETTLE_TICKS = 5
	private const val MERGE_RANGE = 2.0
	private const val PICKUP_REACH = 1.5
	private const val PARTIAL_SOURCE_TICKS = 40

	private class Pending(val source: ItemEntity, val stack: ItemStack) {
		val slots = linkedMapOf<SlotIdentity, PickupSlot>()
		var ticksLeft = SETTLE_TICKS
		var age = 0
		val landed get() = slots.values.sumOf { it.amount }
		val settled get() = landed >= stack.count || ticksLeft <= 0

		fun land(slot: SlotIdentity, after: ItemStack, left: Int, opened: Boolean): Int {
			if (!stack.itemAndComponentsEqual(after)) return 0
			val take = minOf(left, stack.count - landed)
			if (take <= 0) return 0
			val existing = slots[slot]
			val kind = existing?.kind
				?: if (opened) PickupSlotKind.Empty else PickupSlotKind.Merged
			slots[slot] = PickupSlot(after.copy(), (existing?.amount ?: 0) + take, kind)
			if (existing == null) InventoryEvent.PickupLanding(source, slot, after.copy(), kind).post()
			return take
		}
	}

	private var inventory = emptyMap<SlotIdentity, ItemStack>()
	private val pending = mutableListOf<Pending>()
	private val removed = mutableListOf<ItemEntity>()
	private val partialSources = mutableMapOf<ItemEntity, Int>()

	override fun load() = "Loaded pickup events"

	init {
		listen<TickEvent.Pre>({ Int.MAX_VALUE }) {
			settle()
			val awaitingAnswers = InventoryManager.awaitingClickAnswers
			pending.forEach {
				if (!awaitingAnswers) it.ticksLeft--
				it.age++
			}
			emitSettled()
			classifyRemovals()
			if (!awaitingAnswers) {
				partialSources.replaceAll { _, age -> age + 1 }
				partialSources.values.removeIf { it > PARTIAL_SOURCE_TICKS }
			}
		}

		listen<TickEvent.Post>({ Int.MIN_VALUE }) { inventory = snapshot() }

		listen<PlayerEvent.SlotClick>({ Int.MIN_VALUE }) { settle() }
		listen<PlayerEvent.SlotClick.Post>({ Int.MIN_VALUE }) { inventory = snapshot() }

		listen<EntityEvent.ItemPickupAnimation> { event ->
			val item = event.item
			if (event.collector != player) {
				val left = (item.stack.count - event.amount).coerceAtLeast(0)
				InventoryEvent.PickupLost(item, item.stack.copyWithCount(item.stack.count - left), left, event.collector).post()
				return@listen
			}
			settle()
			item.stack.count = event.amount
			pending += Pending(item, item.stack.copy())
		}

		listen<EntityEvent.Removal> { event ->
			val item = event.entity as? ItemEntity ?: return@listen
			if (!item.stack.isEmpty) removed += item
		}

		listenUnsafe<WorldEvent.Join> { reset() }
		listenUnsafe<WorldEvent.Leave> { reset() }
	}

	private fun SafeContext.snapshot(): Map<SlotIdentity, ItemStack> =
		(0 until PlayerInventory.MAIN_SIZE)
			.plus(PlayerInventory.OFF_HAND_SLOT)
			.associate { SlotIdentity.Player(it) to player.inventory.getStack(it).copy() }

	private fun SafeContext.settle() {
		val now = snapshot()
		val reach = player.boundingBox.expand(PICKUP_REACH)
		fastEntitySearch<ItemEntity>(PICKUP_REACH + 2) { !it.isRemoved && it.boundingBox.intersects(reach) }
			.forEach { partialSources[it] = 0 }
		now.forEach { (slot, after) ->
			val before = inventory[slot] ?: return@forEach
			val grown =
				when {
					after.isEmpty -> 0
					before.isEmpty -> after.count
					before.itemAndComponentsEqual(after) -> after.count - before.count
					else -> 0
				}
			var left = grown
			fun Pending.landHere() = land(slot, after, left, before.isEmpty && left == grown)

			pending.forEach { if (left > 0) left -= it.landHere() }
			if (left > 0) partialSourcesOf(after).forEach { source ->
				val pickup = Pending(source, source.stack.copy()).apply { ticksLeft = 0 }
				val taken = pickup.landHere()
				if (taken <= 0) return@forEach
				pending += pickup
				source.stack.decrement(taken)
				left -= taken
			}
		}
		inventory = now
	}

	private fun SafeContext.partialSourcesOf(stack: ItemStack): List<ItemEntity> =
		partialSources.entries
			.filter { it.key.stack.itemAndComponentsEqual(stack) }
			.sortedWith(compareBy({ it.value }, { it.key.squaredDistanceTo(player) }))
			.map { it.key }

	private fun emitSettled() {
		val settled = pending.filter { it.settled }
		pending.removeAll(settled)
		settled.forEach {
			val remaining = if (it.source.isRemoved) 0 else it.source.stack.count
			InventoryEvent.Pickup(it.source, it.stack, it.slots.toMap(), remaining, it.age).post()
		}
	}

	private fun SafeContext.classifyRemovals() {
		val drops = removed.toList()
		removed.clear()
		drops.forEach { source ->
			val target =
				fastEntitySearch<ItemEntity>(MERGE_RANGE, source.blockPos) {
					it !== source && !it.isRemoved && it.stack.itemAndComponentsEqual(source.stack)
				}.minByOrNull { it.squaredDistanceTo(source) }

			if (target != null) InventoryEvent.PickupMerged(source, target).post()
			else InventoryEvent.PickupLost(source, source.stack.copy(), 0, null).post()
		}
	}

	private fun reset() {
		inventory = emptyMap()
		pending.clear()
		removed.clear()
		partialSources.clear()
	}
}
