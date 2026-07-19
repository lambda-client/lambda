
package com.minato.interaction.material.container.containers

import com.minato.Minato.mc
import com.minato.context.SafeContext
import com.minato.interaction.managers.inventory.InventoryRequest
import com.minato.interaction.material.container.MaterialContainer
import com.minato.util.player.SlotUtils.mainHandSlots
import com.minato.util.text.buildText
import com.minato.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

object MainHandContainer : MaterialContainer(Rank.MainHand) {
    context(safeContext: SafeContext)
    override val slots: List<Slot>
        get() = safeContext.player.mainHandSlots
    override var stacks: List<ItemStack>
        get() = mc.player?.mainHandStack?.let { listOf(it) } ?: emptyList()
        set(_) {}

    override val swapMethodPriority = 10

    override val description = buildText { literal("MainHand") }

    context(safeContext: SafeContext)
    override fun InventoryRequest.InvRequestBuilder.transfer(fromHere: Slot, toSlot: Slot) {
        swap(toSlot.id, safeContext.player.inventory.selectedSlot)
    }
}
