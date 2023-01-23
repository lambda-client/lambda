package com.lambda.client.module.modules.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.PlayerInventoryManager
import com.lambda.client.manager.managers.PlayerInventoryManager.addInventoryTask
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TaskState
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.items.removeHoldingItem
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.init.Items
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemArmor
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.gameevent.TickEvent

object AutoArmor : Module(
    name = "AutoArmor",
    description = "Automatically equips armor",
    category = Category.COMBAT,
    modulePriority = 500
) {
    private val allowElytra by setting("Allow Elytra", false, description = "If activated it will not replace an equipped elytra with a chestplate")

    init {
        safeListener<TickEvent.ClientTickEvent> {
            // store slots and values of best armor pieces, initialize with currently equipped armor
            // Pair<Slot, Value>
            val bestArmors = Array(4) { -1 to getArmorValue(player.inventory.armorInventory[it]) }

            // search inventory for better armor
            for (slot in 9..44) {
                val itemStack = player.inventoryContainer.inventory[slot]
                val item = itemStack.item
                if (item !is ItemArmor) continue

                val armorType = item.armorType.index

                // Skip if allowElytra is activated, item is chestplate, and we have elytra equipped
                if (allowElytra && armorType == 2 && player.inventory.armorInventory[2].item == Items.ELYTRA) continue
                val armorValue = getArmorValue(itemStack)

                if (armorValue > bestArmors[armorType].second) {
                    bestArmors[armorType] = slot to armorValue
                }
            }

            // equip better armor
            equipArmor(bestArmors)
        }
    }

    private fun getArmorValue(itemStack: ItemStack): Float {
        val item = itemStack.item
        return if (item !is ItemArmor) -1f
        else item.damageReduceAmount * getProtectionModifier(itemStack)
    }

    private fun getProtectionModifier(itemStack: ItemStack): Float {
        for (i in 0 until itemStack.enchantmentTagList.tagCount()) {
            val id = itemStack.enchantmentTagList.getCompoundTagAt(i).getShort("id").toInt()
            val level = itemStack.enchantmentTagList.getCompoundTagAt(i).getShort("lvl").toInt()

            if (id != 0) continue
            return 1f + 0.04f * level
        }
        return 1f
    }

    private fun SafeClientEvent.equipArmor(bestArmors: Array<Pair<Int, Float>>) {
        for ((index, pair) in bestArmors.withIndex()) {
            if (pair.first == -1) continue // Skip if we didn't find a better armor
            if (player.inventoryContainer.inventory[8 - index].isEmpty) {
                addInventoryTask(
                    PlayerInventoryManager.ClickInfo(0, pair.first, type = ClickType.QUICK_MOVE) // Move the new one into armor slot
                )
            } else {
                addInventoryTask(
                    PlayerInventoryManager.ClickInfo(0, 8 - index, type = ClickType.PICKUP), // Pick up the old armor from armor slot
                    PlayerInventoryManager.ClickInfo(0, pair.first, type = ClickType.QUICK_MOVE), // Move the new one into armor slot
                    PlayerInventoryManager.ClickInfo(0, pair.first, type = ClickType.PICKUP) // Put the old one into the empty slot
                )
            }

            break // Don't move more than one at once
        }
    }
}