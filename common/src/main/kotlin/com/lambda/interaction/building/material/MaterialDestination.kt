package com.lambda.task.flow.building.material

import net.minecraft.item.ItemStack
import net.minecraft.util.math.Box

sealed class MaterialDestination {
    data object MainHand : MaterialDestination()
    data object OffHand : MaterialDestination()
    data object Hotbar : MaterialDestination()
    data class InventorySlot(val slot: Int) : MaterialDestination()
    data object Inventory : MaterialDestination()
    data class ShulkerBox(val shulkerStack: ItemStack) : MaterialDestination()
    data object EnderChest : MaterialDestination()
    data class Stash(val pos: Box) : MaterialDestination()
}
