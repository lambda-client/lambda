/*
 * Copyright 2024 Lambda
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

package com.lambda.util.player

import com.lambda.context.SafeContext
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.SlotActionType

object SlotUtils {
    val ClientPlayerEntity.hotbar: List<ItemStack> get() = inventory.main.subList(0, 9)
    val ClientPlayerEntity.storage: List<ItemStack> get() = inventory.main.subList(9, 36)
    val ClientPlayerEntity.hotbarAndStorage: List<ItemStack> get() = inventory.main.subList(0, 36)
    val ClientPlayerEntity.combined: List<ItemStack> get() = inventory.main + inventory.armor + inventory.offHand
    val ClientPlayerEntity.offhand: ItemStack get() = offHandStack

    fun SafeContext.clickSlot(
        slotId: Int,
        button: Int,
        actionType: SlotActionType,
    ) {
        val syncId = player.currentScreenHandler?.syncId ?: return

        interaction.clickSlot(
            syncId,
            slotId,
            button,
            actionType,
            player,
        )
    }

}
