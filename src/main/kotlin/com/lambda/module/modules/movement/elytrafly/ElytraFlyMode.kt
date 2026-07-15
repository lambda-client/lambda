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

package com.lambda.module.modules.movement.elytrafly

import com.lambda.config.ConfigBlock
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.event.Muteable
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.GlideHandler.ELYTRA_SELECTION
import com.lambda.interaction.inventory.container.containers.ArmorContainer
import com.lambda.interaction.inventory.container.containers.HotbarAndInventoryContainer
import com.lambda.interaction.manager.managers.hotbar.HotbarRequestBuilder.Companion.hotbarRequest
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.interaction.manager.managers.inventory.InventoryManager
import com.lambda.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.lambda.module.modules.movement.elytrafly.ElytraFly.fakeFly
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.logError
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.screen.slot.Slot
import net.minecraft.util.Hand

abstract class ElytraFlyMode(
	val flyMode: FlyMode
) : Muteable, Automated by ElytraFly, ConfigBlock {
	override val isMuted get() = !isEnabled
	val isEnabled get() = ElytraFly.isEnabled && ElytraFly.mode == flyMode

	val onEnableListeners = mutableListOf<SafeContext.() -> Unit>()
	val onDisableListeners = mutableListOf<SafeContext.() -> Unit>()
	val onFlagListeners = mutableListOf<SafeContext.() -> Unit>()

	var fakeGliding = false

	init {
		listen<TickEvent.Pre>({ 100 }) {
			fakeGliding = fakeFly && fakeGliding && player.canGlide()
		}

		listen<PacketEvent.Receive.Pre>({ 100 }) { event ->
			if (event.packet !is InventoryS2CPacket && event.packet !is ScreenHandlerSlotUpdateS2CPacket) return@listen
			if (fakeGliding) event.cancel()
		}
	}

	protected fun onEnable(callback: SafeContext.() -> Unit) { onEnableListeners.add(callback) }
	protected fun onDisable(callback: SafeContext.() -> Unit) { onDisableListeners.add(callback) }

	protected fun onFlag(callback: SafeContext.() -> Unit) { onFlagListeners.add(callback) }

	fun SafeContext.flyOrFakeFly(): Boolean {
		if (!fakeFly) {
			startFly()
			return true
		}

		player.inventory.equipment.get(EquipmentSlot.CHEST).let { chestStack ->
			if (chestStack.item == Items.ELYTRA) {
				logError("Fake Fly requires that you don't have an elytra equipped")
				ElytraFly.disable()
				return false
			}
		}

		val elytraSlot = findElytra() ?: run {
			logError("Fake Fly requires an elytra in your inventory, preferably in your hotbar.")
			ElytraFly.disable()
			return false
		}
		val elytraInHotbar = elytraSlot.index in 0..8

		val chestSlot = ArmorContainer.slots.getOrNull(1) ?: return false

		if (elytraInHotbar) {
			val hotbarRequest =
				hotbarRequest(elytraSlot.index) {
					keepTicks(0)
				}.submit()
			if (!hotbarRequest.done) return false
		}
		fun InvRequestBuilder.swapChest() {
			if (elytraInHotbar) {
				interaction.interactItem(player, Hand.MAIN_HAND)
				InventoryManager.indexInventoryChanges()
			} else {
				moveSlot(elytraSlot.id, chestSlot.id)
				if (!chestSlot.stack.isEmpty) pickup(elytraSlot.id)
			}
		}

		val inventoryRequest = inventoryRequest {
			swapChest()
			action { startFly() }
			swapChest()
		}.submit(false)

		return inventoryRequest.done
	}

	open fun interrupt() {}

	fun findElytra(): Slot? = ELYTRA_SELECTION.filter(HotbarAndInventoryContainer.slots).minByOrNull { it.index }

	protected fun SafeContext.startFly() {
		player.setFlag(Entity.GLIDING_FLAG_INDEX, true)
		startFlyPacket()
		fakeGliding = true
	}

	protected fun SafeContext.startFlyPacket() =
		connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))

	open fun isGliding() = runSafe { player.getFlag(Entity.GLIDING_FLAG_INDEX) || (fakeFly && fakeGliding) } == true
}