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

package com.lambda.interaction.managers.inventory

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.Request
import com.lambda.util.PacketUtils.sendPacket
import com.lambda.util.player.SlotUtils.clickSlot
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.screen.sync.ItemStackHash
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

/**
 * A private constructor is used to enforce use of the [InvRequestDsl] builder.
 *
 * @property actions A list of inventory actions to be performed in the request.
 * @property settleForLess A flag indicating whether to settle for partial completion of the request.
 * @property mustPerform A flag indicating whether the request must be performed regardless of conditions as long as the tick stage is valid.
 */
class InventoryRequest private constructor(
	val actions: List<InventoryAction>,
	val settleForLess: Boolean,
	val mustPerform: Boolean,
	automated: Automated,
	override val nowOrNothing: Boolean = false,
	val onComplete: (SafeContext.() -> Unit)?
) : Request(), Automated by automated {
	override val requestId = ++requestCount
	override val tickStageMask get() = inventoryConfig.tickStageMask
	override var done = false

	override fun submit(queueIfMismatchedStage: Boolean) =
		InventoryManager.request(this, queueIfMismatchedStage)

	@DslMarker
	private annotation class InvRequestDsl

	@InvRequestDsl
	class InvRequestBuilder(val settleForLess: Boolean, val mustPerform: Boolean) {
		val actions = mutableListOf<InventoryAction>()
		var onComplete: (SafeContext.() -> Unit)? = null

		@InvRequestDsl
		fun click(slotId: Int, button: Int, actionType: SlotActionType) {
			InventoryAction.Inventory { clickSlot(slotId, button, actionType) }.addToActions()
		}

		@InvRequestDsl
		fun resyncInventory() {
			InventoryAction.Inventory {
				connection.sendPacket {
					val sh = player.currentScreenHandler
					ClickSlotC2SPacket(
						sh.syncId,
						-1, -1, 0,
						SlotActionType.CLONE,
						Int2ObjectOpenHashMap<ItemStackHash>(),
						ItemStackHash.fromItemStack(sh.cursorStack, connection.componentHasher)
					)
				}
			}.addToActions()
		}

		@InvRequestDsl
		fun pickFromInventory(slotId: Int) {
			InventoryAction.Inventory {
				clickSlot(slotId, player.inventory.selectedSlot, SlotActionType.SWAP)
			}.addToActions()
		}

		@InvRequestDsl
		fun dropItemInHand(entireStack: Boolean = true) {
			InventoryAction.Inventory { player.dropSelectedItem(entireStack) }.addToActions()
		}

		@InvRequestDsl
		fun swapHands() {
			InventoryAction.Player {
				val offhandStack = player.getStackInHand(Hand.OFF_HAND)
				player.setStackInHand(Hand.OFF_HAND, player.getStackInHand(Hand.MAIN_HAND))
				player.setStackInHand(Hand.MAIN_HAND, offhandStack)
				connection.sendPacket(
					PlayerActionC2SPacket(
						PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
						BlockPos.ORIGIN,
						Direction.DOWN
					)
				)
			}.addToActions()
		}

		@InvRequestDsl
		fun clickCreativeStack(stack: ItemStack, slotId: Int) {
			InventoryAction.Inventory { interaction.clickCreativeStack(stack, slotId) }.addToActions()
		}

		@InvRequestDsl
		fun pickup(slotId: Int, button: Int = 0) = click(slotId, button, SlotActionType.PICKUP)

		// Quick move action (Shift-click)
		@InvRequestDsl
		fun quickMove(slotId: Int) = click(slotId, 0, SlotActionType.QUICK_MOVE)

		@InvRequestDsl
		fun swap(slotId: Int, hotbarSlot: Int) = click(slotId, hotbarSlot, SlotActionType.SWAP)

		// Clone action (Creative mode)
		@InvRequestDsl
		fun clone(slotId: Int) = click(slotId, 2, SlotActionType.CLONE)

		// Throw stack or single item
		@InvRequestDsl
		fun throwStack(slotId: Int) = click(slotId, 1, SlotActionType.THROW)

		@InvRequestDsl
		fun throwSingle(slotId: Int) = click(slotId, 0, SlotActionType.THROW)

		// Quick craft action
		@InvRequestDsl
		fun quickCraftStart(slotId: Int) = click(slotId, 0, SlotActionType.QUICK_CRAFT)

		@InvRequestDsl
		fun quickCraftDrag(slotId: Int) = click(slotId, 1, SlotActionType.QUICK_CRAFT)

		@InvRequestDsl
		fun quickCraftEnd(slotId: Int) = click(slotId, 2, SlotActionType.QUICK_CRAFT)

		// Pickup all items (double-click)
		@InvRequestDsl
		fun pickupAll(slotId: Int) = click(slotId, 0, SlotActionType.PICKUP_ALL)

		// Helper function: Move items from one slot to another
		@InvRequestDsl
		fun moveSlot(fromSlotId: Int, toSlotId: Int, button: Int = 0) {
			pickup(fromSlotId, button)
			pickup(toSlotId, button)
		}

		// Helper function: Split a stack into two
		@InvRequestDsl
		fun splitStack(slotId: Int, targetSlotId: Int) {
			pickup(slotId, 1) // Pickup half the stack
			pickup(targetSlotId, 0) // Place it in the target slot
		}

		// Helper function: Merge stacks
		@InvRequestDsl
		fun mergeStacks(sourceSlotId: Int, targetSlotId: Int) {
			pickup(sourceSlotId, 0)
			pickup(targetSlotId, 0)
		}

		@InvRequestDsl
		fun action(action: SafeContext.() -> Unit) {
			InventoryAction.Other(action).addToActions()
		}

		@InvRequestDsl
		fun onComplete(callback: SafeContext.() -> Unit) {
			onComplete = callback
		}

		@InvRequestDsl
		private fun InventoryAction.addToActions() {
			actions.add(this)
		}
	}

	companion object {
		var requestCount = 0

		@InvRequestDsl
		fun Automated.inventoryRequest(settleForLess: Boolean = false, mustPerform: Boolean = false, builder: InvRequestBuilder.() -> Unit) =
			InvRequestBuilder(settleForLess, mustPerform).apply(builder).build()

		@InvRequestDsl
		context(automated: Automated)
		private fun InvRequestBuilder.build() = InventoryRequest(actions, settleForLess, mustPerform, automated, onComplete = onComplete)
	}
}
