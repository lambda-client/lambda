package com.lambda.client.module.modules.player

import com.lambda.client.commons.extension.ceilToInt
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.mixin.extension.syncCurrentPlayItem
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.process.PauseProcess.pauseBaritone
import com.lambda.client.process.PauseProcess.unpauseBaritone
import com.lambda.client.setting.settings.impl.collection.CollectionSetting
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.items.*
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Enchantments
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.gameevent.TickEvent

object InventoryManager : Module(
    name = "InventoryManager",
    description = "Manages your inventory automatically",
    category = Category.PLAYER
) {
    private val defaultEjectList = linkedSetOf(
        "minecraft:grass",
        "minecraft:dirt",
        "minecraft:netherrack",
        "minecraft:gravel",
        "minecraft:sand",
        "minecraft:stone",
        "minecraft:cobblestone"
    )

    private val autoRefill by setting("Auto Refill", true)
    private val buildingMode by setting("Building Mode", false, { autoRefill })
    var buildingBlockID by setting("Building Block ID", 0, 0..1000, 1, { false })
    private val refillThreshold by setting("Refill Threshold", 16, 1..63, 1, { autoRefill })
    private val itemSaver by setting("Item Saver", false)
    private val duraThreshold by setting("Durability Threshold", 5, 1..50, 1, { itemSaver })
    private val autoEject by setting("Auto Eject", false)
    private val fullOnly by setting("Only At Full", false, { autoEject })
    private val pauseMovement by setting("Pause Movement", true)
    private val delay by setting("Delay Ticks", 1, 0..20, 1, unit = " ticks")
    private val helpMend by setting("Help Mend", false, description = "Helps mending items by replacing the offhand item with low HP items of the same type")
    val ejectList = setting(CollectionSetting("Eject List", defaultEjectList))

    enum class State {
        IDLE, SAVING_ITEM, HELPING_MEND, REFILLING_BUILDING, REFILLING, EJECTING
    }

    private var currentState = State.IDLE
    private var isBaritonePaused = false
    private val timer = TickTimer(TimeUnit.TICKS)

    override fun isActive(): Boolean {
        return isEnabled && currentState != State.IDLE
    }

    init {
        onDisable {
            isBaritonePaused = false
            unpauseBaritone()
        }

        safeListener<PlayerTravelEvent> {
            if (player.isSpectator || !pauseMovement || !isBaritonePaused) return@safeListener
            player.setVelocity(0.0, player.motionY, 0.0)
            it.cancel()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START || player.isSpectator || mc.currentScreen is GuiContainer) return@safeListener

            if (!timer.tick(delay) && !(NoGhostItems.syncMode != NoGhostItems.SyncMode.PLAYER && NoGhostItems.isEnabled)) return@safeListener

            setState()

            when (currentState) {
                State.SAVING_ITEM -> saveItem()
                State.HELPING_MEND -> helpMend()
                State.REFILLING_BUILDING -> refillBuilding()
                State.REFILLING -> refill()
                State.EJECTING -> eject()
                State.IDLE -> removeHoldingItem(this@InventoryManager)
            }

            playerController.syncCurrentPlayItem()
        }
    }

    private fun SafeClientEvent.setState() {
        currentState = when {
            saveItemCheck() -> State.SAVING_ITEM
            helpMendCheck() -> State.HELPING_MEND
            refillBuildingCheck() -> State.REFILLING_BUILDING
            refillCheck() -> State.REFILLING
            ejectCheck() -> State.EJECTING
            else -> State.IDLE
        }

        isBaritonePaused = if (currentState != State.IDLE && pauseMovement) {
            pauseBaritone()
            true
        } else {
            unpauseBaritone()
            false
        }
    }

    /* State checks */
    private fun SafeClientEvent.saveItemCheck(): Boolean {
        return itemSaver && checkDamage(player.heldItemMainhand)
    }

    private fun SafeClientEvent.helpMendCheck() : Boolean {
        return helpMend
            && (player.heldItemOffhand.itemDamage == 0
                || EnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING, player.heldItemOffhand) == 0)
    }

    private fun SafeClientEvent.refillBuildingCheck(): Boolean {
        if (!autoRefill || !buildingMode || buildingBlockID == 0) return false

        val totalCount = player.inventorySlots.countID(buildingBlockID)
        val hotbarCount = player.hotbarSlots.countID(buildingBlockID)

        return totalCount >= refillThreshold
            && (hotbarCount < refillThreshold
            || (getRefillableSlotBuilding() != null && currentState == State.REFILLING_BUILDING))
    }

    private fun SafeClientEvent.refillCheck(): Boolean {
        return autoRefill && getRefillableSlot() != null
    }

    private fun SafeClientEvent.ejectCheck(): Boolean {
        return autoEject && ejectList.isNotEmpty()
            && (!fullOnly || player.inventorySlots.firstEmpty() == null)
            && getEjectSlot() != null
    }
    /* End of state checks */

    /* Tasks */
    private fun SafeClientEvent.saveItem() {
        val currentSlot = player.inventory.currentItem
        val itemStack = player.heldItemMainhand

        val undamagedItem = getUndamagedItem(itemStack.item.id)
        val emptySlot = player.inventorySlots.firstEmpty()

        when {
            autoRefill && undamagedItem != null -> {
                moveToHotbar(this@InventoryManager, undamagedItem.slotNumber, currentSlot)
            }
            emptySlot != null -> {
                moveToHotbar(this@InventoryManager, emptySlot.slotNumber, currentSlot)
            }
            else -> {
                player.dropItem(false)
            }
        }
    }

    private fun SafeClientEvent.helpMend() {
        player.inventorySlots.filterByStack {
            it.item == player.heldItemOffhand.item
                && EnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING, it) != 0
                && it.itemDamage != 0
        }.firstOrNull()?.let {
            MessageSendHelper.sendChatMessage("$chatName Switching offhand to another item (Help Mend).")
            moveToSlot(this@InventoryManager, it, player.offhandSlot)
        }
    }

    private fun SafeClientEvent.refillBuilding() {
        player.storageSlots.firstID(buildingBlockID)?.let {
            quickMoveSlot(this@InventoryManager, it)
        }
    }

    private fun SafeClientEvent.refill() {
        val slotTo = getRefillableSlot() ?: return
        val slotFrom = getCompatibleStack(slotTo.stack) ?: return

        moveToSlot(this@InventoryManager, slotFrom, slotTo)
    }

    private fun SafeClientEvent.eject() {
        getEjectSlot()?.let {
            throwAllInSlot(this@InventoryManager, it)
        }
    }
    /* End of tasks */

    /**
     * Finds undamaged item with given ID in inventory, and return its slot
     *
     * @return Full inventory slot if undamaged item found, else return null
     */
    private fun SafeClientEvent.getUndamagedItem(itemID: Int) =
        player.storageSlots.firstID(itemID) {
            !checkDamage(it)
        }

    private fun checkDamage(itemStack: ItemStack) =
        itemStack.isItemStackDamageable
            && itemStack.itemDamage > itemStack.maxDamage * (1.0f - duraThreshold / 100.0f)

    private fun SafeClientEvent.getRefillableSlotBuilding(): Slot? {
        if (player.storageSlots.firstID(buildingBlockID) == null) return null

        return player.hotbarSlots.firstID(buildingBlockID) {
            it.isStackable && it.count < it.maxStackSize
        }
    }

    private fun SafeClientEvent.getRefillableSlot(): Slot? {
        return player.hotbarSlots.firstByStack {
            !it.isEmpty
                && (!buildingMode || it.item.id != buildingBlockID)
                && (!autoEject || !ejectList.contains(it.item.registryName.toString()))
                && it.isStackable
                && it.count < (it.maxStackSize / 64.0f * refillThreshold).ceilToInt()
                && getCompatibleStack(it) != null
        }
    }

    private fun SafeClientEvent.getCompatibleStack(stack: ItemStack): Slot? {
        return player.storageSlots.firstByStack {
            stack.isItemEqual(it) && ItemStack.areItemStackTagsEqual(stack, it)
        }
    }

    private fun SafeClientEvent.getEjectSlot(): Slot? {
        return player.inventorySlots.firstByStack {
            !it.isEmpty
                && (!buildingMode || it.item.id != buildingBlockID)
                && ejectList.contains(it.item.registryName.toString())
        }
    }
}