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

import com.lambda.config.AutomationConfig.Companion.DEFAULT
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.Manager
import com.lambda.interaction.managers.inventory.InventoryManager.actions
import com.lambda.interaction.managers.inventory.InventoryManager.activeRequest
import com.lambda.interaction.managers.inventory.InventoryManager.alteredSlots
import com.lambda.interaction.managers.inventory.InventoryManager.processActiveRequest
import com.lambda.threading.runSafe
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.item.ItemStackUtils.equal
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

/**
 * Manager designed to handle inventory actions. One of the key features being the inventory change detection to
 * avoid accepting old information from the server in cases where ping is high. This helps to prevent desync.
 */
object InventoryManager : Manager<InventoryRequest>(
	1,
	onOpen = { processActiveRequest() }
) {
	private var activeRequest: InventoryRequest? = null
	private var actions = mutableListOf<InventoryAction>()

	private var slots = listOf<ItemStack>()
	private var alteredSlots = LimitedDecayQueue<InventoryChange>(Int.MAX_VALUE, DEFAULT.desyncTimeout * 50L)
	private var alteredPlayerSlots = LimitedDecayQueue<InventoryChange>(Int.MAX_VALUE, DEFAULT.desyncTimeout * 50L)

	var screenHandler: ScreenHandler? = null
		set(value) {
			if (value != null) {
				alteredSlots.clear()
				slots = getStacks(value.slots)
			}
			field = value
		}

	private var maxActionsThisSecond = 0
	private var actionsThisSecond = 0
	private var secondCounter = 0
	private var actionsThisTick = 0

	override fun load(): String {
		super.load()

        listen<TickEvent.Post>({ Int.MIN_VALUE }) {
            if (DEFAULT.avoidDesync) indexInventoryChanges()
            if (++secondCounter >= 20) {
                secondCounter = 0
                actionsThisSecond = 0
            }
            actionsThisTick = 0
            activeRequest = null
            actions = mutableListOf()
        }

		listen<PacketEvent.Send.Post> { event ->
			if (event.packet is CloseHandledScreenC2SPacket) {
				screenHandler = player.playerScreenHandler
			}
		}

		return "Loaded Inventory Manager"
	}

	/**
	 * Attempts to accept the request and perform the actions. If, for example, the tick stage isn't valid, or
	 * not all the actions can be performed and [InventoryRequest.settleForLess] is set to false, the request is rejected.
	 * All checks aside from tick stage are ignored if the request has [InventoryRequest.mustPerform] set to true.
	 * This is typically used in dangerous situations where typical rules are worth breaking. For example, if the player
	 * needs to equip a totem of undying.
	 */
	override fun AutomatedSafeContext.handleRequest(request: InventoryRequest) {
		if (activeRequest != null) return

		val inventoryActionCount = request.actions.count { it is InventoryAction.Inventory }
		if (inventoryActionCount > request.inventoryConfig.actionsPerSecond - actionsThisSecond &&
			!request.settleForLess &&
			!request.mustPerform) return

		if (request.fresh) populateFrom(request)

		processActiveRequest()
		if (request.nowOrNothing) {
			activeRequest = null
			actions = mutableListOf()
		}
	}

	private fun populateFrom(request: InventoryRequest) {
		activeRequest = request
		actions = request.actions.toMutableList()
		maxActionsThisSecond = request.inventoryConfig.actionsPerSecond
		alteredSlots.setDecayTime(DEFAULT.desyncTimeout * 50L)
		alteredPlayerSlots.setDecayTime(DEFAULT.desyncTimeout * 50L)
	}

	/**
	 * Attempts to perform as many actions as possible from the [actions] collection. If
	 * [actions] is empty, the request is set to done, and the onComplete callback is invoked.
	 * The [activeRequest] is then set to null.
	 */
	private fun SafeContext.processActiveRequest() {
		activeRequest?.let { active ->
			if (tickStage !in active.inventoryConfig.tickStageMask && active.nowOrNothing) return
			val iterator = actions.iterator()
			while (iterator.hasNext()) {
				val action = iterator.next()
				if (action is InventoryAction.Inventory && actionsThisSecond + 1 > maxActionsThisSecond && !active.mustPerform)
					break
				action.action(this)
				if (DEFAULT.avoidDesync) indexInventoryChanges()
				actionsThisTick++
				actionsThisSecond++
				iterator.remove()
			}

			if (actions.isEmpty()) {
				active.done = true
				active.onComplete?.invoke(this)
				activeRequest = null
			}

			if (actionsThisTick > 0) activeThisTick = true
		}
	}

	/**
	 * Detects changes in item stacks between now and the last time slots were cached and
	 * adds them all to the [alteredSlots] collection where they can be compared to
	 * incoming [InventoryS2CPacket] and [ScreenHandlerSlotUpdateS2CPacket] packets to decide whether to
	 * block certain updates.
	 */
	private fun SafeContext.indexInventoryChanges() {
		if (player.currentScreenHandler.syncId != screenHandler?.syncId) return
		val changes = screenHandler?.slots
			?.filter { !it.stack.equal(slots[it.id]) }
			?.map { InventoryChange(it.id, slots[it.id], it.stack.copy()) }
			?: emptyList()
		if (player.currentScreenHandler.syncId == 0) alteredPlayerSlots.addAll(changes)
		else alteredSlots.addAll(changes)
		slots = getStacks(player.currentScreenHandler.slots)
	}

	private fun getStacks(slots: Collection<Slot>) = slots.map { it.stack.copy() }

	/**
	 * A modified version of the minecraft onInventory method
	 *
	 * @see net.minecraft.client.network.ClientPlayNetworkHandler.onInventory
	 */
	@JvmStatic
	fun onInventoryUpdate(packet: InventoryS2CPacket, original: Operation<Void>){
		runSafe {
			if (!mc.isOnThread || !DEFAULT.avoidDesync) {
				original.call(packet)
				return
			}
			val packetScreenHandler =
				when (packet.syncId) {
					0 -> player.playerScreenHandler
					player.currentScreenHandler.syncId -> player.currentScreenHandler
					else -> return@runSafe
				}
			val alteredContents = mutableListOf<ItemStack>()
			val alteredSlots = if (packet.syncId == 0) alteredPlayerSlots else alteredSlots
			packet.contents.forEachIndexed { index, incomingStack ->
				val matches = alteredSlots.removeIf { cached ->
					incomingStack.equal(cached.after)
				}
				if (matches) alteredContents.add(packetScreenHandler.slots[index].stack)
				else alteredContents.add(incomingStack)
			}
			packetScreenHandler.updateSlotStacks(packet.revision(), alteredContents, packet.cursorStack())
			return
		}
		original.call(packet)
	}

	/**
	 * A modified version of the minecraft onScreenHandlerSlotUpdate method
	 *
	 * @see net.minecraft.client.network.ClientPlayNetworkHandler.onScreenHandlerSlotUpdate
	 */
	@JvmStatic
	fun onSlotUpdate(packet: ScreenHandlerSlotUpdateS2CPacket, original: Operation<Void>) {
		runSafe {
			if (!mc.isOnThread || !DEFAULT.avoidDesync) {
				original.call(packet)
				return
			}
			val itemStack = packet.stack
			mc.tutorialManager.onSlotUpdate(itemStack)

			val bl = (mc.currentScreen as? CreativeInventoryScreen)?.let {
				!it.isInventoryTabSelected
			} ?: false

			val alteredSlots = if (packet.syncId == 0) alteredPlayerSlots else alteredSlots
			val matches = alteredSlots.removeIf {
				it.syncId == packet.slot && it.after.equal(itemStack)
			}

			if (packet.syncId == 0) {
				if (PlayerScreenHandler.isInHotbar(packet.slot) && !itemStack.isEmpty) {
					val itemStack2 = player.playerScreenHandler.getSlot(packet.slot).stack
					if (itemStack2.isEmpty || itemStack2.count < itemStack.count) {
						itemStack.bobbingAnimationTime = 5
					}
				}

				if (matches) player.playerScreenHandler.revision = packet.revision
				else player.playerScreenHandler.setStackInSlot(packet.slot, packet.revision, itemStack)
			} else if (packet.syncId == player.currentScreenHandler.syncId && (packet.syncId != 0 || !bl)) {
				if (matches) player.currentScreenHandler.revision = packet.revision
				else player.currentScreenHandler.setStackInSlot(packet.slot, packet.revision, itemStack)
			}

			if (mc.currentScreen is CreativeInventoryScreen) {
				player.playerScreenHandler.setReceivedStack(packet.slot, itemStack)
				player.playerScreenHandler.sendContentUpdates()
			}
			return
		}
		original.call(packet)
	}

	@JvmStatic
	fun onSetScreenHandler(screenHandler: ScreenHandler) {
		this.screenHandler = screenHandler
	}

	private data class InventoryChange(
		val syncId: Int,
		val before: ItemStack,
		val after: ItemStack
	)
}
