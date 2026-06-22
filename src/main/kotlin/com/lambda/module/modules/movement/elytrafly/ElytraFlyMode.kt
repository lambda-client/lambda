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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.inventory.InventoryRequest
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.lambda.module.modules.movement.elytrafly.ElytraFly.fakeFly
import com.lambda.threading.runSafe
import com.lambda.util.CommunicationUtils.logError
import com.lambda.util.player.SlotUtils.armorSlots
import com.lambda.util.player.SlotUtils.hotbarSlots
import com.lambda.util.player.SlotUtils.inventorySlots
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.screen.slot.Slot

abstract class ElytraFlyMode(
	val flyMode: FlyMode
) : Muteable, Automated by ElytraFly, ConfigBlock {
	override val isMuted get() = !isEnabled
	val isEnabled get() = ElytraFly.isEnabled && ElytraFly.mode == flyMode

	val onEnableListeners = mutableListOf<SafeContext.() -> Unit>()
	val onDisableListeners = mutableListOf<SafeContext.() -> Unit>()
	val onFlagListeners = mutableListOf<SafeContext.() -> Unit>()

	var fakeGliding = false

	init{
		listen<TickEvent.Pre>({ 100 }) {
			fakeGliding = fakeFly && fakeGliding && player.canGlide()
		}
	}

	protected fun onEnable(callback: SafeContext.() -> Unit) { onEnableListeners.add(callback) }
	protected fun onDisable(callback: SafeContext.() -> Unit) { onDisableListeners.add(callback) }

	protected fun onFlag(callback: SafeContext.() -> Unit) { onFlagListeners.add(callback) }

	fun SafeContext.flyOrFakeFly(onFly: (SafeContext.() -> Unit)? = null) {
		if (!fakeFly) {
			startFly()
			onFly?.invoke(this)
			return
		}

		player.inventory.equipment.get(EquipmentSlot.CHEST).let { chestStack ->
			if (chestStack.item == Items.ELYTRA) {
				logError("Fake Fly requires that you don't have an elytra equipped")
				ElytraFly.disable()
				return
			}
		}

		val elytraSlot = findElytra() ?: run {
			logError("Fake Fly requires an elytra in your inventory, preferably in your hotbar.")
			ElytraFly.disable()
			return
		}
		val elytraInHotbar = elytraSlot.index in 0..8

		val chestSlot = player.armorSlots[1]

		fun InventoryRequest.InvRequestBuilder.swapChest() {
			if (elytraInHotbar) swap(chestSlot.id, elytraSlot.index)
			else {
				moveSlot(elytraSlot.id, chestSlot.id)
				if (!chestSlot.stack.isEmpty) pickup(elytraSlot.id)
			}
		}

		inventoryRequest {
			swapChest()
			action { startFly(); onFly?.invoke(this) }
			swapChest()
		}.submit()
	}

	fun SafeContext.findElytra(): Slot? =
		selectStack {
			isItem(Items.ELYTRA)
				.and { it.damage < it.maxDamage }
		}.run {
			filterSlots(player.hotbarSlots)
				.firstOrNull()
				?: filterSlots(player.inventorySlots)
					.firstOrNull()
		}

	protected fun SafeContext.startFly() {
		player.setFlag(Entity.GLIDING_FLAG_INDEX, true)
		startFlyPacket()
		fakeGliding = true
	}

	protected fun SafeContext.startFlyPacket() =
		connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))

	open fun isGliding() = runSafe { player.getFlag(Entity.GLIDING_FLAG_INDEX) || (fakeFly && fakeGliding) } == true
}