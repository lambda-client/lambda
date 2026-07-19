
package com.minato.module.modules.movement.elytrafly

import com.minato.config.ConfigBlock
import com.minato.context.Automated
import com.minato.context.SafeContext
import com.minato.event.Muteable
import com.minato.event.events.PacketEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.handlers.GlideHandler.ELYTRA_SELECTION
import com.minato.interaction.managers.hotbar.HotbarRequest
import com.minato.interaction.managers.inventory.InventoryManager
import com.minato.interaction.managers.inventory.InventoryRequest
import com.minato.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.minato.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.minato.module.modules.movement.elytrafly.ElytraFly.fakeFly
import com.minato.threading.runSafe
import com.minato.util.CommunicationUtils.logError
import com.minato.util.player.SlotUtils.armorSlots
import com.minato.util.player.SlotUtils.hotbarAndInventorySlots
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

		val chestSlot = player.armorSlots.getOrNull(1) ?: return false

		if (elytraInHotbar) {
			val hotbarRequest = HotbarRequest(
				elytraSlot.index,
				ElytraFly,
				keepTicks = 0,
				nowOrNothing = true
			).submit()
			if (!hotbarRequest.done) return false
		}
		fun InventoryRequest.InvRequestBuilder.swapChest() {
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

	fun SafeContext.findElytra(): Slot? =
		ELYTRA_SELECTION.filterSlots(player.hotbarAndInventorySlots).minByOrNull { it.index }

	protected fun SafeContext.startFly() {
		player.setFlag(Entity.GLIDING_FLAG_INDEX, true)
		startFlyPacket()
		fakeGliding = true
	}

	protected fun SafeContext.startFlyPacket() =
		connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))

	open fun isGliding() = runSafe { player.getFlag(Entity.GLIDING_FLAG_INDEX) || (fakeFly && fakeGliding) } == true
}