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

package com.lambda.interaction.manager.managers.inventory

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.Lambda.LOG
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.PacketLimitHandler.canSendPackets
import com.lambda.interaction.handler.handlers.PacketLimitHandler.sentPackets
import com.lambda.interaction.handler.handlers.PacketType
import com.lambda.interaction.manager.Manager
import com.lambda.interaction.manager.managers.inventory.InventoryManager.processActiveRequest
import com.lambda.module.modules.client.Client
import com.lambda.module.modules.client.Client.verboseDebug
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeAutomated
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.screen.ScreenHandler

/**
 * Manager designed to handle inventory actions. One of the key features being the inventory change detection to
 * avoid accepting old information from the server in cases where ping is high. This helps to prevent desync.
 *
 * Every local prediction (clicks through this manager, plus anything else the client predicts such as block
 * placement, which is picked up by the tick diff in [indexInventoryChanges]) is recorded in the [filter] as an
 * ordered per-slot queue. Server answers that match a recording are echoes of our own actions and are dropped;
 * anything else is new information and is applied. See [InventorySyncFilter] for the exact rules.
 */
object InventoryManager : Manager<InventoryRequest>(
	1,
	onOpen = { processActiveRequest() }
) {
	private var activeRequest: InventoryRequest? = null
	private var actions = mutableListOf<InventoryAction>()

	private val filter =
		InventorySyncFilter<ItemStack>(
			{ a, b -> ItemStack.areEqual(a, b) },
			{ it.copy() },
			Client.desyncTimeout * 50L
		)

	private var actionsThisTick = 0

	override fun load(): String {
		super.load()

        listen<TickEvent.Post>({ Int.MIN_VALUE }) {
            if (Client.avoidInventoryDesync) indexInventoryChanges()
            actionsThisTick = 0
            activeRequest = null
            actions = mutableListOf()
        }

		listen<PlayerEvent.SlotClick.Post> {
			if (Client.avoidInventoryDesync) indexInventoryChanges()
		}

		listen<PacketEvent.Send.Post> { event ->
			if (event.packet is CloseHandledScreenC2SPacket) {
				onSetScreenHandler(player.playerScreenHandler)
			}
		}

		listen<InventoryEvent.Close> {
			onSetScreenHandler(player.currentScreenHandler)
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
		if (activeRequest != null) {
			request.failureReason = InvRequestFailureReason.Preoccupied
			return
		}

		val playerActionCount = request.actions.count { it is InventoryAction.Player }
		val inventoryActionCount = request.actions.count { it is InventoryAction.Inventory }
		val canPerformAllPlayerActions = canSendPackets(playerActionCount, PacketType.PlayerAction)
		val canPerformAllInventoryActions = canSendPackets(inventoryActionCount, PacketType.Inventory)
		if ((!canPerformAllPlayerActions || !canPerformAllInventoryActions) &&
			!request.settleForLess &&
			!request.mustPerform
		) {
			request.failureReason = InvRequestFailureReason.PacketLimit
			return
		}

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
		filter.maxAgeMs = Client.desyncTimeout * 50L
	}

	/**
	 * Attempts to perform as many actions as possible from the [actions] collection. If
	 * [actions] is empty, the request is set to done, and the onComplete callback is invoked.
	 * The [activeRequest] is then set to null.
	 */
	private fun SafeContext.processActiveRequest() {
		val active = activeRequest ?: return
		active.runSafeAutomated {
			if (tickStage !in active.inventoryConfig.tickStageMask && active.nowOrNothing) return
			val iterator = actions.iterator()
			while (iterator.hasNext()) {
				val action = iterator.next()
				if (action is InventoryAction.Player && !canSendPackets(1, PacketType.PlayerAction)) break
				else if (action is InventoryAction.Inventory && !canSendPackets(1, PacketType.Inventory)) break
				action.action(this)
				if (action is InventoryAction.Player) sentPackets(1, PacketType.PlayerAction)
				else if (action is InventoryAction.Inventory) sentPackets(1, PacketType.Inventory)
				if (Client.avoidInventoryDesync) indexInventoryChanges()
				actionsThisTick++
				iterator.remove()
			}

			if (actions.isEmpty()) {
				active.done = true
				active.onComplete?.invoke(this)
				activeRequest = null
			} else {
				active.failureReason = InvRequestFailureReason.PacketLimit
			}

			if (actionsThisTick > 0) activeThisTick = true
		}
	}

	/**
	 * Diffs the live screen against the last snapshot and records every changed slot in the [filter]
	 * in the order it happened. This runs after every performed action and at the end of every tick,
	 * so predictions made outside this manager (placing blocks, eating, picking items up) are recorded
	 * too, as long as their server echo arrives later than the next recording point.
	 *
	 * The cursor is tracked the same way: every click sends a mismatched revision (-1), so the server
	 * answers with a full [InventoryS2CPacket] carrying the cursor, and with rapid clicks that cursor
	 * is routinely stale by the time it arrives. Merging it like a slot keeps a resurrected cursor
	 * from breaking the transfer tasks that read it every tick.
	 *
	 * Note the click path sends a mismatched revision (-1), so the server answers a click with a full
	 * [InventoryS2CPacket]; that packet is merged per slot in [onInventoryUpdate], which is why each
	 * change is recorded exactly once here.
	 */
	fun SafeContext.indexInventoryChanges() {
		val current = player.currentScreenHandler
		val recorded = filter.observeLocal(
			current.syncId,
			current.slots.associate { it.id to it.stack },
			current.cursorStack
		)
		if (verboseDebug && recorded > 0) LOG.info("[desync] recorded $recorded change(s) on sync=${current.syncId}")
	}

	private fun snapshotHandler(handler: ScreenHandler) {
		filter.snapshot(
			handler.syncId,
			handler.slots.associate { it.id to it.stack },
			handler.cursorStack
		)
	}

	/**
	 * A modified version of the minecraft onScreenHandlerSlotUpdate method
	 *
	 * @see net.minecraft.client.network.ClientPlayNetworkHandler.onScreenHandlerSlotUpdate
	 */
	@JvmStatic
	fun onSlotUpdate(packet: ScreenHandlerSlotUpdateS2CPacket, original: Operation<Void>) {
		runSafe {
			val itemStack = packet.stack
			mc.tutorialManager.onSlotUpdate(itemStack)

			val packetScreenHandler =
				when (packet.syncId) {
					0 -> player.playerScreenHandler
					player.currentScreenHandler.syncId -> player.currentScreenHandler
					else -> {
						if (verboseDebug) LOG.info("[desync] single ignored: packet sync=${packet.syncId} slot=${packet.slot} current=${player.currentScreenHandler.syncId} stack=${packet.stack}")
						original.call(packet)
						return
					}
				}
			val current = packetScreenHandler.slots.getOrNull(packet.slot)?.stack
			if (current == null) {
				original.call(packet)
				return
			}
			if (!mc.isOnThread || !Client.avoidInventoryDesync) {
				original.call(packet)
				snapshotHandler(packetScreenHandler)
				return
			}

			val bl = (mc.currentScreen as? CreativeInventoryScreen)?.let {
				!it.isInventoryTabSelected
			} ?: false

			val apply = filter.onSingle(packet.syncId, packet.slot, itemStack, current)

			if (packet.syncId == 0) {
				if (PlayerScreenHandler.isInHotbar(packet.slot) && !itemStack.isEmpty) {
					val itemStack2 = player.playerScreenHandler.getSlot(packet.slot).stack
					if (itemStack2.isEmpty || itemStack2.count < itemStack.count) {
						itemStack.bobbingAnimationTime = 5
					}
				}

				if (!apply) player.playerScreenHandler.revision = packet.revision
				else player.playerScreenHandler.setStackInSlot(packet.slot, packet.revision, itemStack)
			} else if (packet.syncId == player.currentScreenHandler.syncId && (packet.syncId != 0 || !bl)) {
				if (!apply) player.currentScreenHandler.revision = packet.revision
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

	/**
	 * A modified version of the minecraft onSetCursorItem method. Cursor-only updates bypass
	 * the full packets, so without this every one of them is applied blindly — including ones
	 * the server generated before our latest click (see [InventorySyncFilter.onCursor]).
	 *
	 * @see net.minecraft.client.network.ClientPlayNetworkHandler.onSetCursorItem
	 */
	@JvmStatic
	fun onCursorUpdate(packet: SetCursorItemS2CPacket, original: Operation<Void>) {
		runSafe {
			val stack = packet.contents()
			mc.tutorialManager.onSlotUpdate(stack)
			if (!mc.isOnThread || !Client.avoidInventoryDesync) {
				original.call(packet)
				snapshotHandler(player.currentScreenHandler)
				return
			}
			val handler = player.currentScreenHandler
			val drop = !filter.onCursor(handler.syncId, stack, handler.cursorStack)
			if (verboseDebug) {
				LOG.info("[desync] cursor single sync=${handler.syncId} incoming=$stack client=${handler.cursorStack} apply=${!drop} pendings=${filter.pendingCount(handler.syncId, InventoryLedger.CURSOR_SLOT_ID)}")
			}
			if (drop) return
			original.call(packet)
			return
		}
		original.call(packet)
	}

	/**
	 * A modified version of the minecraft onInventory method
	 *
	 * @see net.minecraft.client.network.ClientPlayNetworkHandler.onInventory
	 */
	@JvmStatic
	fun onInventoryUpdate(packet: InventoryS2CPacket, original: Operation<Void>) {
		runSafe {
			val packetScreenHandler =
				when (packet.syncId) {
					0 -> player.playerScreenHandler
					player.currentScreenHandler.syncId -> player.currentScreenHandler
					else -> {
						if (verboseDebug) LOG.info("[desync] full ignored: packet sync=${packet.syncId} current=${player.currentScreenHandler.syncId}")
						original.call(packet)
						return
					}
				}
			if (!mc.isOnThread || !Client.avoidInventoryDesync) {
				original.call(packet)
				snapshotHandler(packetScreenHandler)
				return
			}
			val clientStacks = packetScreenHandler.slots.map { it.stack }
			if (clientStacks.size != packet.contents.size) {
				original.call(packet)
				snapshotHandler(packetScreenHandler)
				return
			}
			val result =
				filter.onFull(
					packet.syncId,
					packet.contents,
					clientStacks,
					packet.cursorStack(),
					packetScreenHandler.cursorStack
				)
			val merged =
				packet.contents.mapIndexed { index, incomingStack ->
					if (result.apply[index]) incomingStack else clientStacks[index]
				}
			val mergedCursor =
				if (result.applyCursor) packet.cursorStack()
				else packetScreenHandler.cursorStack
			if (verboseDebug && !ItemStack.areEqual(packet.cursorStack(), packetScreenHandler.cursorStack)) {
				LOG.info("[desync] full sync=${packet.syncId} cursor incoming=${packet.cursorStack()} client=${packetScreenHandler.cursorStack} apply=${result.applyCursor} pendings=${filter.pendingCount(packet.syncId, InventoryLedger.CURSOR_SLOT_ID)}")
			}
			packetScreenHandler.updateSlotStacks(packet.revision(), merged, mergedCursor)
			return
		}
		original.call(packet)
	}

	/**
	 * Anchors tracking to a screen's live state: drops pendings that cannot belong to a fresh
	 * screen id, and photographs the current truth. Called on opens, closes and respawns.
	 */
	@JvmStatic
	fun onSetScreenHandler(screenHandler: ScreenHandler) {
		if (screenHandler.syncId != 0) filter.clear(screenHandler.syncId)
		filter.snapshot(
			screenHandler.syncId,
			screenHandler.slots.associate { it.id to it.stack },
			screenHandler.cursorStack
		)
	}
}
