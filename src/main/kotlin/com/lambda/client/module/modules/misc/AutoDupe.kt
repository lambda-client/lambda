package com.lambda.client.module.modules.misc

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.inventory.GuiInventory
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.inventory.ClickType
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.crafting.CraftingManager
import net.minecraft.item.crafting.IRecipe
import net.minecraft.network.play.client.CPacketPlaceRecipe
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.gameevent.TickEvent


internal object AutoDupe : Module(
    name = "AutoDupe",
    category = Category.MISC,
    description = "Automatically do the 5b5t crafting dupe"
) {

    private val cancelGUI by setting("Cancel GUI", true)
    private val rotate by setting("Rotate", true)
    private var hiddenInfo by setting("How-to", false, {false})

    private var currentWaitPhase = WaitPhase.NONE
    private var startTimeStamp = 0L
    private var countBefore = 0
    private var idBefore = 0
    private var slotBefore = 0
    private var inProgress = false
    private var lastClickStamp = System.currentTimeMillis()
    private var recipeLocation: ResourceLocation = ResourceLocation("wooden_button")
        //The only way this would be null is if your game is *FUCKED*
    private var pktRecipe: IRecipe = CraftingManager.REGISTRY.getObject(recipeLocation)!!


init {
    onEnable {
        val check = checkForPlanks()
        if (check == -1) abort("Planks were not found in inventory.") else currentWaitPhase = WaitPhase.DROP

        if (!hiddenInfo) {
            MessageSendHelper.sendChatMessage("To do the dupe: have wooden planks in your inventory, hold the item you wish to dupe, and toggle the module. wait until the item is picked back up.")
            hiddenInfo = true
        }
    }

    onDisable {
        if (cancelGUI) mc.displayGuiScreen(null)
    }

    safeListener<TickEvent.ClientTickEvent> {
        if (currentWaitPhase == WaitPhase.NONE) disable()

        if (currentWaitPhase == WaitPhase.DROP) {

            if (mc.player.inventory.getCurrentItem().isEmpty) abort("You need to hold an item.")

            if (System.currentTimeMillis() - startTimeStamp < 120L) {
                if (!mc.player.recipeBook.isGuiOpen) mc.player.recipeBook.isGuiOpen = true
            }
            if (rotate) mc.player.rotationPitch = 180f
            idBefore = Item.getIdFromItem(mc.player.inventory.getCurrentItem().item)
            countBefore = countItem(idBefore)
            slotBefore = mc.player.inventory.currentItem
            throwAllInSlot(slotBefore + 36)
            if (!cancelGUI) mc.displayGuiScreen(GuiInventory(mc.player as EntityPlayer) as GuiScreen)
            if (!mc.player.recipeBook.isGuiOpen) abort("Failed to open Recipe Book. Try opening it manually.")
            currentWaitPhase = WaitPhase.PICKUP
        }

        else if (currentWaitPhase == WaitPhase.PICKUP) {
            if (System.currentTimeMillis() - lastClickStamp < 300L) disable()
            lastClickStamp = System.currentTimeMillis()
        }
    }
}

    private fun countItem(itemId: Int): Int {
        val itemList: ArrayList<Int> = getSlots(itemId)
        var currentCount = 0
        val iterator: Iterator<Int> = itemList.iterator()
        while (iterator.hasNext()) {
            val i = iterator.next()
            currentCount += mc.player.inventory.getStackInSlot(i).count
        }
        return currentCount
    }

    private fun getSlots(itemID: Int): ArrayList<Int> {
        val slots = ArrayList<Int>()
        for (i in 0..8) {
            if (Item.getIdFromItem(mc.player.inventory.getStackInSlot(i).item) == itemID) slots.add(Integer.valueOf(i))
        }
        return slots
    }

    private fun throwAllInSlot(slot: Int) {
        if (inProgress) return
        val thread: Thread = object : Thread() {
            override fun run() {
                inProgress = true
                mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slot, 1, ClickType.THROW, mc.player as EntityPlayer)
                try {
                    sleep(1000)
                    mc.player.connection.sendPacket(CPacketPlaceRecipe(0, pktRecipe, false))
                } catch (e: InterruptedException) {
                    abort("Tossing was interrupted.")
                }
                inProgress = false
            }
        }
        thread.start()
    }

    private fun checkForPlanks(): Int {
        for (i in 0..35) {
            val stack = mc.player.inventory.getStackInSlot(i)
            if (stack.item is ItemBlock) {
                val block = (stack.item as ItemBlock).block
                if (block == Blocks.PLANKS) {
                    return i
                }
            }
        }
        return -1
    }

    private fun abort(msg: String) {
        MessageSendHelper.sendErrorMessage(msg)
        currentWaitPhase = WaitPhase.NONE //this acts as 'disable()'
        mc.displayGuiScreen(null)
    }

    enum class WaitPhase {
        NONE, DROP, PICKUP
    }
}