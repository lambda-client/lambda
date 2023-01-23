package com.lambda.client.module.modules.combat

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.firstItem
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.items.swapToSlot
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Items
import net.minecraft.util.EnumHand
import net.minecraft.util.math.RayTraceResult.Type
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Mouse

object MidClickPearl : Module(
    name = "MidClickPearl",
    description = "Throws a pearl upon middle clicking in the air",
    category = Category.COMBAT
) {
    private var prevSlot = -1
    private var startTime = -1L

    init {
        safeListener<InputEvent.MouseInputEvent> {
            val objectMouseOver = mc.objectMouseOver ?: return@safeListener
            if (objectMouseOver.typeOfHit == Type.BLOCK) return@safeListener

            if (startTime == -1L && Mouse.getEventButton() == 2 && Mouse.getEventButtonState()) {
                val pearlSlot = player.hotbarSlots.firstItem(Items.ENDER_PEARL)

                if (pearlSlot != null) {
                    prevSlot = player.inventory.currentItem
                    swapToSlot(pearlSlot)
                    startTime = 0L
                } else {
                    sendChatMessage("No Ender Pearl was found in hotbar!")
                    startTime = System.currentTimeMillis() + 1000L
                }
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (startTime == 0L && player.getCooledAttackStrength(0f) >= 1f) {
                playerController.processRightClick(player, world, EnumHand.MAIN_HAND)
                startTime = System.currentTimeMillis()
            } else if (startTime > 0L) {
                if (prevSlot != -1) {
                    swapToSlot(prevSlot)
                    prevSlot = -1
                }
                if (System.currentTimeMillis() - startTime > 2000L) startTime = -1L
            }
        }
    }
}
