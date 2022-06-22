package com.lambda.client.util.items

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.PlayerInventoryManager
import com.lambda.client.manager.managers.PlayerInventoryManager.addInventoryTask
import com.lambda.client.module.AbstractModule
import com.lambda.client.module.modules.player.NoGhostItems
import com.lambda.client.util.threads.onMainThreadSafe
import kotlinx.coroutines.runBlocking
import net.minecraft.block.Block
import net.minecraft.init.Items
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketClickWindow

/**
 * Try to swap selected hotbar slot to [I] that matches with [predicateItem]
 *
 * Or move an item from storage slot to an empty slot or slot that matches [predicateSlot]
 * or slot 0 if none
 */
inline fun <reified I : Block> SafeClientEvent.swapToBlockOrMove(
    owner: AbstractModule,
    predicateItem: (ItemStack) -> Boolean = { true },
    predicateSlot: (ItemStack) -> Boolean = { true }
): Boolean {
    return if (swapToBlock<I>(predicateItem)) {
        true
    } else {
        player.storageSlots.firstBlock<I, Slot>(predicateItem)?.let {
            moveToHotbar(owner, it, predicateSlot)
            true
        } ?: false
    }
}

/**
 * Try to swap selected hotbar slot to [block] that matches with [predicateItem]
 *
 * Or move an item from storage slot to an empty slot or slot that matches [predicateSlot]
 * or slot 0 if none
 */
fun SafeClientEvent.swapToBlockOrMove(
    owner: AbstractModule,
    block: Block,
    predicateItem: (ItemStack) -> Boolean = { true },
    predicateSlot: (ItemStack) -> Boolean = { true }
): Boolean {
    return if (swapToBlock(block, predicateItem)) {
        true
    } else {
        player.storageSlots.firstBlock(block, predicateItem)?.let {
            moveToHotbar(owner, it, predicateSlot)
            true
        } ?: false
    }
}

/**
 * Try to swap selected hotbar slot to [I] that matches with [predicateItem]
 *
 * Or move an item from storage slot to an empty slot or slot that matches [predicateSlot]
 * or slot 0 if none
 */
inline fun <reified I : Item> SafeClientEvent.swapToItemOrMove(
    owner: AbstractModule,
    predicateItem: (ItemStack) -> Boolean = { true },
    predicateSlot: (ItemStack) -> Boolean = { true }
): Boolean {
    return if (swapToItem<I>(predicateItem)) {
        true
    } else {
        player.storageSlots.firstItem<I, Slot>(predicateItem)?.let {
            moveToHotbar(owner, it, predicateSlot)
            true
        } ?: false
    }
}

/**
 * Try to swap selected hotbar slot to [item] that matches with [predicateItem]
 *
 * Or move an item from storage slot to an empty slot or slot that matches [predicateSlot]
 * or slot 0 if none
 */
fun SafeClientEvent.swapToItemOrMove(
    owner: AbstractModule,
    item: Item,
    predicateItem: (ItemStack) -> Boolean = { true },
    predicateSlot: (ItemStack) -> Boolean = { true }
): Boolean {
    return if (swapToItem(item, predicateItem)) {
        true
    } else {
        player.storageSlots.firstItem(item, predicateItem)?.let {
            moveToHotbar(owner, it, predicateSlot)
            true
        } ?: false
    }
}

/**
 * Try to swap selected hotbar slot to item with [itemID] that matches with [predicateItem]
 *
 * Or move an item from storage slot to an empty slot or slot that matches [predicateSlot]
 * or slot 0 if none
 */
fun SafeClientEvent.swapToItemOrMove(
    owner: AbstractModule,
    itemID: Int,
    predicateItem: (ItemStack) -> Boolean = { true },
    predicateSlot: (ItemStack) -> Boolean = { true }
): Boolean {
    return if (swapToID(itemID, predicateItem)) {
        true
    } else {
        player.storageSlots.firstID(itemID, predicateItem)?.let {
            moveToHotbar(owner, it, predicateSlot)
            true
        } ?: false
    }
}

/**
 * Try to swap selected hotbar slot to [I] that matches with [predicate]
 */
inline fun <reified I : Block> SafeClientEvent.swapToBlock(predicate: (ItemStack) -> Boolean = { true }): Boolean {
    return player.hotbarSlots.firstBlock<I, HotbarSlot>(predicate)?.let {
        swapToSlot(it)
        true
    } ?: false
}

/**
 * Try to swap selected hotbar slot to [block] that matches with [predicate]
 */
fun SafeClientEvent.swapToBlock(block: Block, predicate: (ItemStack) -> Boolean = { true }): Boolean {
    return player.hotbarSlots.firstBlock(block, predicate)?.let {
        swapToSlot(it)
        true
    } ?: false
}

/**
 * Try to swap selected hotbar slot to [I] that matches with [predicate]
 */
inline fun <reified I : Item> SafeClientEvent.swapToItem(predicate: (ItemStack) -> Boolean = { true }): Boolean {
    return player.hotbarSlots.firstItem<I, HotbarSlot>(predicate)?.let {
        swapToSlot(it)
        true
    } ?: false
}

/**
 * Try to swap selected hotbar slot to [item] that matches with [predicate]
 */
fun SafeClientEvent.swapToItem(item: Item, predicate: (ItemStack) -> Boolean = { true }): Boolean {
    return player.hotbarSlots.firstItem(item, predicate)?.let {
        swapToSlot(it)
        true
    } ?: false
}

/**
 * Try to swap selected hotbar slot to item with [itemID] that matches with [predicate]
 */
fun SafeClientEvent.swapToID(itemID: Int, predicate: (ItemStack) -> Boolean = { true }): Boolean {
    return player.hotbarSlots.firstID(itemID, predicate)?.let {
        swapToSlot(it)
        true
    } ?: false
}

/**
 * Swap the selected hotbar slot to [hotbarSlot]
 */
fun SafeClientEvent.swapToSlot(hotbarSlot: HotbarSlot) {
    swapToSlot(hotbarSlot.hotbarSlot)
}

/**
 * Swap the selected hotbar slot to [slot]
 */
fun SafeClientEvent.swapToSlot(slot: Int) {
    if (slot !in 0..8) return
    player.inventory.currentItem = slot
    playerController.updateController()
}

/**
 * Swaps the item in [slotFrom] with the first empty hotbar slot
 * or matches with [predicate] or slot 0 if none of those found
 */
inline fun SafeClientEvent.moveToHotbar(owner: AbstractModule, slotFrom: Slot, predicate: (ItemStack) -> Boolean) {
    moveToHotbar(owner, slotFrom.slotNumber, predicate)
}

/**
 * Swaps the item in [slotFrom] with the first empty hotbar slot
 * or matches with [predicate] or slot 0 if none of those found
 */
inline fun SafeClientEvent.moveToHotbar(owner: AbstractModule, slotFrom: Int, predicate: (ItemStack) -> Boolean) {
    val hotbarSlots = player.hotbarSlots
    val slotTo = hotbarSlots.firstItem(Items.AIR)?.hotbarSlot
        ?: hotbarSlots.firstByStack(predicate)?.hotbarSlot ?: 0

    moveToHotbar(owner, slotFrom, slotTo)
}

/**
 * Swaps the item in [slotFrom] with the hotbar slot [slotTo].
 */
fun SafeClientEvent.moveToHotbar(owner: AbstractModule, slotFrom: Slot, slotTo: HotbarSlot) {
    moveToHotbar(owner, 0, slotFrom, slotTo)
}

/**
 * Swaps the item in [slotFrom] with the hotbar slot [hotbarSlotTo].
 */
fun SafeClientEvent.moveToHotbar(owner: AbstractModule, windowId: Int, slotFrom: Slot, hotbarSlotTo: HotbarSlot) {
    moveToHotbar(owner, windowId, slotFrom.slotNumber, hotbarSlotTo.hotbarSlot)
}

/**
 * Swaps the item in [slotFrom] with the hotbar slot [hotbarSlotTo].
 */
fun SafeClientEvent.moveToHotbar(owner: AbstractModule, slotFrom: Int, hotbarSlotTo: Int) {
    moveToHotbar(owner, 0, slotFrom, hotbarSlotTo)
}

/**
 * Swaps the item in [slotFrom] with the hotbar slot [hotbarSlotTo].
 */
fun SafeClientEvent.moveToHotbar(owner: AbstractModule, windowId: Int, slotFrom: Int, hotbarSlotTo: Int) {
    // mouseButton is actually the hotbar
    swapToSlot(hotbarSlotTo)
    clickSlot(owner, windowId, slotFrom, hotbarSlotTo, type = ClickType.SWAP)
}

/**
 * Move the item in [slotFrom]  to [slotTo] in player inventory,
 * if [slotTo] contains an item, then move it to [slotFrom]
 */
fun SafeClientEvent.moveToSlot(owner: AbstractModule, slotFrom: Slot, slotTo: Slot) {
    moveToSlot(owner, 0, slotFrom.slotNumber, slotTo.slotNumber)
}

/**
 * Move the item in [slotFrom]  to [slotTo] in player inventory,
 * if [slotTo] contains an item, then move it to [slotFrom]
 */
fun SafeClientEvent.moveToSlot(owner: AbstractModule, slotFrom: Int, slotTo: Int) {
    moveToSlot(owner, 0, slotFrom, slotTo)
}

/**
 * Move the item in [slotFrom] to [slotTo] in [windowId],
 * if [slotTo] contains an item, then move it to [slotFrom]
 */
fun SafeClientEvent.moveToSlot(owner: AbstractModule, windowId: Int, slotFrom: Int, slotTo: Int) {
    clickSlot(owner, windowId, slotFrom, type = ClickType.PICKUP)
    clickSlot(owner, windowId, slotTo, type = ClickType.PICKUP)
    clickSlot(owner, windowId, slotFrom, type = ClickType.PICKUP)
}

/**
 * Move all the item that equals to the item in [slotTo] to [slotTo] in player inventory
 * Note: Not working
 */
fun SafeClientEvent.moveAllToSlot(owner: AbstractModule, slotTo: Int) {
    clickSlot(owner, slot = slotTo, type = ClickType.PICKUP_ALL)
    clickSlot(owner, slot = slotTo, type = ClickType.PICKUP)
}

/**
 * Quick move (Shift + Click) the item in [slot] in player inventory
 */
fun SafeClientEvent.quickMoveSlot(owner: AbstractModule, slot: Int) {
    quickMoveSlot(owner, 0, slot)
}

/**
 * Quick move (Shift + Click) the item in [slot] in specified [windowId]
 */
fun SafeClientEvent.quickMoveSlot(owner: AbstractModule, windowId: Int, slot: Int) {
    clickSlot(owner, windowId, slot, type = ClickType.QUICK_MOVE)
}

/**
 * Quick move (Shift + Click) the item in [slot] in player inventory
 */
fun SafeClientEvent.quickMoveSlot(owner: AbstractModule, slot: Slot) {
    quickMoveSlot(owner, 0, slot)
}

/**
 * Quick move (Shift + Click) the item in [slot] in specified [windowId]
 */
fun SafeClientEvent.quickMoveSlot(owner: AbstractModule, windowId: Int, slot: Slot) {
    clickSlot(owner, windowId, slot, type = ClickType.QUICK_MOVE)
}

/**
 * Throw all the item in [slot] in player inventory
 */
fun SafeClientEvent.throwAllInSlot(owner: AbstractModule, slot: Int) {
    throwAllInSlot(owner, 0, slot)
}

/**
 * Throw all the item in [slot] in specified [windowId]
 */
fun SafeClientEvent.throwAllInSlot(owner: AbstractModule, windowId: Int, slot: Int) {
    clickSlot(owner, windowId, slot, 1, ClickType.THROW)
}

/**
 * Throw all the item in [slot] in player inventory
 */
fun SafeClientEvent.throwAllInSlot(owner: AbstractModule, slot: Slot) {
    throwAllInSlot(owner, 0, slot)
}

/**
 * Throw all the item in [slot] in specified [windowId]
 */
fun SafeClientEvent.throwAllInSlot(owner: AbstractModule, windowId: Int, slot: Slot) {
    clickSlot(owner, windowId, slot, 1, ClickType.THROW)
}

/**
 * Put the item currently holding by mouse to somewhere or throw it
 */
fun SafeClientEvent.removeHoldingItem(owner: AbstractModule) {
    if (player.inventory.itemStack.isEmpty) return

    val slot = player.inventoryContainer.getSlots(9..45).firstItem(Items.AIR)?.slotNumber // Get empty slots in inventory and offhand
        ?: player.craftingSlots.firstItem(Items.AIR)?.slotNumber // Get empty slots in crafting slot
        ?: -999 // Throw on the ground

    clickSlot(owner, slot = slot, type = ClickType.PICKUP)
}

/**
 * Performs inventory clicking in specific window, slot, mouseButton, and click type
 */
fun SafeClientEvent.clickSlot(owner: AbstractModule, windowId: Int = 0, slot: Slot, mouseButton: Int = 0, type: ClickType) {
    clickSlot(owner, windowId, slot.slotNumber, mouseButton, type)
}

/**
 * Performs inventory clicking in specific window, slot, mouseButton, and click type
 */
fun SafeClientEvent.clickSlot(owner: AbstractModule, windowId: Int = 0, slot: Int, mouseButton: Int = 0, type: ClickType) {
    if (NoGhostItems.isEnabled && NoGhostItems.syncMode != NoGhostItems.SyncMode.PLAYER) {
        owner.addInventoryTask(
            PlayerInventoryManager.ClickInfo(windowId, slot, mouseButton, type)
        )
    } else {
        clickSlotUnsynced(windowId, slot, mouseButton, type)
    }
}

fun SafeClientEvent.clickSlotUnsynced(windowId: Int = 0, slot: Int, mouseButton: Int = 0, type: ClickType) {
    val container = if (windowId == 0) player.inventoryContainer else player.openContainer
    container ?: return

    val playerInventory = player.inventory ?: return
    val transactionID = container.getNextTransactionID(playerInventory)
    val itemStack = container.slotClick(slot, mouseButton, type, player)

    connection.sendPacket(CPacketClickWindow(windowId, slot, mouseButton, type, itemStack, transactionID))
    runBlocking {
        onMainThreadSafe { playerController.updateController() }
    }
}