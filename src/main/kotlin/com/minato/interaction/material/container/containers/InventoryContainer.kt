
package com.minato.interaction.material.container.containers

import com.minato.Minato.mc
import com.minato.context.SafeContext
import com.minato.interaction.material.container.MaterialContainer
import com.minato.util.player.SlotUtils.inventorySlots
import com.minato.util.player.SlotUtils.inventoryStacks
import com.minato.util.text.buildText
import com.minato.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

object InventoryContainer : MaterialContainer(Rank.Inventory) {
    context(safeContext: SafeContext)
    override val slots: List<Slot>
        get() = safeContext.player.inventorySlots
    override var stacks: List<ItemStack>
        get() = mc.player?.inventoryStacks ?: emptyList()
        set(_) {}

    override val description = buildText { literal("Inventory") }
}
