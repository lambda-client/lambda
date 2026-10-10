/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.interaction.container.containers

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder
import com.lambda.util.player.SlotUtils.hotbarSlots
import com.lambda.util.player.SlotUtils.hotbarStacks
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.screen.slot.Slot

object HotbarContainer : Container(ContainerType.Hotbar) {
    override val slots: List<Slot>
        get() = mc.player?.hotbarSlots ?: emptyList()
    override var stacks
        get() = mc.player?.hotbarStacks ?: emptyList()
        set(_) {}

    override val swapMethodPriority = 9

    override val description = buildText { literal("Hotbar") }

    context(safeContext: SafeContext)
    override fun InvRequestBuilder.swap(fromHere: Slot, toSlot: Slot) {
        swapWithHotbar(toSlot.id, safeContext.player.hotbarSlots.indexOf(fromHere))
    }
}
