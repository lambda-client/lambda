
package com.minato.interaction.material.container.containers

import com.minato.Minato.mc
import com.minato.context.SafeContext
import com.minato.interaction.managers.inventory.InventoryRequest
import com.minato.interaction.material.container.MaterialContainer
import com.minato.util.player.SlotUtils.hotbarSlots
import com.minato.util.player.SlotUtils.hotbarStacks
import com.minato.util.text.buildText
import com.minato.util.text.literal
import net.minecraft.screen.slot.Slot

object HotbarContainer : MaterialContainer(Rank.Hotbar) {
    context(safeContext: SafeContext)
    override val slots: List<Slot>
        get() = safeContext.player.hotbarSlots
    override var stacks
        get() = mc.player?.hotbarStacks ?: emptyList()
        set(_) {}

    override val swapMethodPriority = 9

    override val description = buildText { literal("Hotbar") }

    context(safeContext: SafeContext)
    override fun InventoryRequest.InvRequestBuilder.transfer(fromHere: Slot, toSlot: Slot) {
        swap(toSlot.id, safeContext.player.hotbarSlots.indexOf(fromHere))
    }
}
