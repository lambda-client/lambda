package com.lambda.client.module.modules.misc

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraftforge.fml.common.gameevent.TickEvent

object AntiBreak : Module(
    name = "AntiBreak",
    description = "Prevents items from breaking by switching to another item in the hotbar",
    category = Category.MISC
) {
    init {
        safeListener<TickEvent.ClientTickEvent> {
            val currentItem = player.inventory.getCurrentItem();
            if (currentItem.maxDamage != 0 && currentItem.itemDamage >= currentItem.maxDamage)
                trySwitchItem(player)
        }
    }

    // TODO: Should probably look alongside all the slots in the inventory, not just the hotbar.
    private fun trySwitchItem(player: EntityPlayerSP) {
        var selectedItemIndex = player.inventory.currentItem;
        for (i in 0..8) {
            val x = player.inventory.getStackInSlot(i);
            if (x.maxDamage == 0) {
                selectedItemIndex = i;
                break
            }
            else if (x.itemDamage < x.maxDamage)
                selectedItemIndex = i
        }

        if (player.inventory.currentItem != selectedItemIndex) {
            MessageSendHelper.sendChatMessage("$chatName Switching current item to prevent breaking it.")
            player.inventory.currentItem = selectedItemIndex;
        }
    }
}