package com.lambda.client.module.modules.combat

import com.lambda.client.event.events.GuiEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.event.listener.listener
import net.minecraft.client.gui.GuiGameOver

object AntiDeathScreen : Module(
    name = "AntiDeathScreen",
    description = "Fixes random death screen glitches",
    category = Category.COMBAT
) {
    init {
        listener<GuiEvent.Displayed> {
            if (it.screen !is GuiGameOver) return@listener
            if (mc.player.health > 0) {
                mc.player.respawnPlayer()
                mc.displayGuiScreen(null)
            }
        }
    }
}