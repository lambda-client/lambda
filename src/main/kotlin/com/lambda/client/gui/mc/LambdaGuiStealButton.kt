package com.lambda.client.gui.mc

import com.lambda.client.module.modules.player.ChestStealer
import com.lambda.client.util.Wrapper
import net.minecraft.client.gui.GuiButton

class LambdaGuiStealButton(x: Int, y: Int) :
    GuiButton(696969, x, y, 50, 20, "Steal") {
    override fun mouseReleased(mouseX: Int, mouseY: Int) {
        if (ChestStealer.mode == ChestStealer.Mode.MANUAL) {
            ChestStealer.stealing = false
            playPressSound(Wrapper.minecraft.soundHandler)
        }
        super.mouseReleased(mouseX, mouseY)
    }
}