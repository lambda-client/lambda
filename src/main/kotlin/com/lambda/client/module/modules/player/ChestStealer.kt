package com.lambda.client.module.modules.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.items.*
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiEnchantment
import net.minecraft.client.gui.GuiMerchant
import net.minecraft.client.gui.GuiRepair
import net.minecraft.client.gui.inventory.*
import net.minecraft.init.Items
import net.minecraft.inventory.ContainerShulkerBox
import net.minecraft.item.ItemShulkerBox
import net.minecraftforge.fml.common.gameevent.TickEvent

object ChestStealer : Module(
    name = "ChestStealer",
    description = "Automatically steal or store items from containers",
    category = Category.PLAYER
) {
    val mode by setting("Mode", Mode.TOGGLE)
    private val movingMode by setting("Moving Mode", MovingMode.QUICK_MOVE)
    private val ignoreEjectItem by setting("Ignores Eject Item", false, description = "Ignore AutoEject items in InventoryManager")
    private val delay by setting("Delay", 5, 0..20, 1, description = "Move stack delay", unit = " ticks")
    private val onlyShulkers by setting("Only Shulkers", false, description = "Only move shulker boxes")

    enum class Mode {
        ALWAYS, TOGGLE, MANUAL
    }

    private enum class MovingMode {
        QUICK_MOVE, PICKUP, THROW
    }

    private enum class ContainerMode(val offset: Int) {
        STEAL(36), STORE(0)
    }

    var stealing = false
    var storing = false
    val timer = TickTimer(TimeUnit.TICKS)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            stealing = if (isContainerOpen() && (stealing || mode == Mode.ALWAYS)) {
                stealOrStore(getStealingSlot(), ContainerMode.STEAL)
            } else {
                false
            }

            storing = if (isContainerOpen() && (storing || mode == Mode.ALWAYS)) {
                stealOrStore(getStoringSlot(), ContainerMode.STORE)
            } else {
                false
            }
        }
    }

    private fun SafeClientEvent.canSteal(): Boolean {
        return getStealingSlot() != null
    }

    private fun SafeClientEvent.canStore(): Boolean {
        return getStoringSlot() != null
    }

    private fun SafeClientEvent.isContainerOpen(): Boolean {
        return player.openContainer != null
            && isValidGui()
    }

    fun isValidGui(): Boolean {
        return mc.currentScreen !is GuiEnchantment
            && mc.currentScreen !is GuiMerchant
            && mc.currentScreen !is GuiRepair
            && mc.currentScreen !is GuiBeacon
            && mc.currentScreen !is GuiCrafting
            && mc.currentScreen !is GuiContainerCreative
            && mc.currentScreen !is GuiInventory
    }

    @JvmStatic
    fun updateButton(button: GuiButton, left: Int, size: Int, top: Int) {
        runSafe {
            if (isEnabled && isContainerOpen()) {
                if (button.id == 696969) {
                    val name = if (stealing) "Stop" else "Steal"

                    button.x = left + size + 2
                    button.y = top + 2
                    button.enabled = canSteal() and !storing
                    button.visible = true
                    button.displayString = name
                } else if (button.id == 420420) {
                    val name = if (storing) "Stop" else "Store"

                    button.x = left + size + 2
                    button.y = top + 24
                    button.enabled = canStore() and !stealing
                    button.visible = true
                    button.displayString = name
                }
            } else {
                button.visible = false
            }
        }
    }

    private fun SafeClientEvent.stealOrStore(slot: Int?, containerMode: ContainerMode): Boolean {
        if (slot == null) return false

        val size = getContainerSlotSize()
        val rangeStart = if (containerMode == ContainerMode.STEAL) size else 0
        val slotTo = player.openContainer.getSlots(rangeStart until size + containerMode.offset).firstEmpty()
            ?: return false
        val windowID = player.openContainer.windowId

        if (timer.tick(delay) || (NoGhostItems.syncMode != NoGhostItems.SyncMode.PLAYER && NoGhostItems.isEnabled)) {
            when (movingMode) {
                MovingMode.QUICK_MOVE -> quickMoveSlot(this@ChestStealer, windowID, slot)
                MovingMode.PICKUP -> moveToSlot(this@ChestStealer, windowID, slot, slotTo.slotNumber)
                MovingMode.THROW -> throwAllInSlot(this@ChestStealer, windowID, slot)
            }
        }

        return true
    }

    private fun SafeClientEvent.getStealingSlot(): Int? {
        val container = player.openContainer.inventory

        for (slot in 0 until getContainerSlotSize()) {
            val item = container[slot].item
            if (item == Items.AIR) continue
            if (ignoreEjectItem && InventoryManager.ejectList.contains(item.registryName.toString())) continue
            if (!onlyShulkers || item is ItemShulkerBox) {
                return slot
            }
        }

        return null
    }

    private fun SafeClientEvent.getStoringSlot(): Int? {
        val container = player.openContainer.inventory
        val size = getContainerSlotSize()

        for (slot in size until size + 36) {
            val item = container[slot].item
            if (item == Items.AIR) continue
            if (player.openContainer is ContainerShulkerBox && item is ItemShulkerBox) continue
            if (!onlyShulkers || item is ItemShulkerBox) {
                return slot
            }
        }

        return null
    }

    private fun SafeClientEvent.getContainerSlotSize(): Int {
        if (mc.currentScreen !is GuiContainer) return 0
        return player.openContainer.inventorySlots.size - 36
    }
}
