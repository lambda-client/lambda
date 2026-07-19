
package com.minato.interaction.material.container.containers

import com.minato.Minato.mc
import com.minato.context.SafeContext
import com.minato.interaction.managers.inventory.InventoryRequest
import com.minato.interaction.material.container.MaterialContainer
import com.minato.util.player.SlotUtils.offHandSlots
import com.minato.util.text.buildText
import com.minato.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

object OffHandContainer : MaterialContainer(Rank.OffHand) {
    context(safeContext: SafeContext)
    override val slots: List<Slot>
        get() = safeContext.player.offHandSlots
    override var stacks: List<ItemStack>
        get() = mc.player?.offHandStack?.let { listOf(it) } ?: emptyList()
        set(_) {}

    override val swapMethodPriority = 10

    override val description = buildText { literal("OffHand") }

    context(_: SafeContext)
    override fun InventoryRequest.InvRequestBuilder.transfer(fromHere: Slot, toSlot: Slot) {
        swap(toSlot.id, 40)
    }
}
