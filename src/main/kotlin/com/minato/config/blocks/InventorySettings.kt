
package com.minato.config.blocks

import com.minato.config.Config
import com.minato.config.ConfigBlock
import com.minato.config.Group
import com.minato.event.events.TickEvent.Companion.ALL_STAGES
import com.minato.util.item.ItemUtils

class InventorySettings(override val c: Config) : InventoryConfig, ConfigBlock {
    companion object {
        private const val CONTAINER_GROUP = "Container"
        private const val ACCESS_GROUP = "Access"
    }

    override val tickStageMask by c.setting("Inventory Stage Mask", ALL_STAGES.toSet(), description = "The sub-tick timing at which inventory actions are performed", displayClassName = true)
    @Group(CONTAINER_GROUP) override val disposables by c.setting("Disposables", ItemUtils.defaultDisposables, description = "Items that will be ignored when checking for a free slot")
    @Group(CONTAINER_GROUP) override val swapWithDisposables by c.setting("Swap With Disposables", true, "Swap items with disposable ones")
    @Group(CONTAINER_GROUP) override val providerPriority by c.setting("Provider Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when retrieving the item from")
    @Group(CONTAINER_GROUP) override val storePriority by c.setting("Store Priority", InventoryConfig.Priority.WithMinItems, "What container to prefer when storing the item to")

    @Group(ACCESS_GROUP) override val accessShulkerBoxes by c.setting("Access Shulker Boxes", false, "Allow access to the player's shulker boxes")
    @Group(ACCESS_GROUP) override val accessChests by c.setting("Access Chests", false, "Allow access to the player's normal chests")
    @Group(ACCESS_GROUP) override val accessEnderChest by c.setting("Access Ender Chest", false, "Allow access to the player's ender chest")
    @Group(ACCESS_GROUP) override val accessStashes by c.setting("Access Stashes", false, "Allow access to the player's stashes")
}