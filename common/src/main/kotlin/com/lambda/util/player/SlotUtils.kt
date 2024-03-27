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
    val ClientPlayerEntity.offhand: ItemStack get() = inventory.offHand.first()

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