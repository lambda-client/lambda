package com.lambda.client.gui.mc

import com.lambda.client.module.modules.combat.AutoDisconnect
import com.lambda.client.util.threads.mainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import java.time.LocalTime

class LambdaGuiDisconnected(
    private val reason: Array<String>,
    private val screen: GuiScreen,
    private val disable: Boolean,
    private val logoutTime: LocalTime
) : GuiScreen() {

    override fun initGui() {
        super.initGui()

        buttonList.add(GuiButton(0, width / 2 - 100, 200, "Okay"))
        if (!disable) {
            buttonList.add(GuiButton(1, width / 2 - 100, 220, "Disable AutoDisconnect"))
        } else {
            disable()
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)
        drawCenteredString(fontRenderer, "[AutoDisconnect] Disconnected because:", width / 2, 80, 0x9B90FF)

        reason.forEachIndexed { index, reason ->
            drawCenteredString(fontRenderer, reason, width / 2, 94 + (14 * index), 0xFFFFFF)
        }

        drawCenteredString(fontRenderer, "Logged out at: $logoutTime", width / 2, 140, 0xFFFFFFF)

        if (!disable) drawCenteredString(fontRenderer, "Disabled AutoDisconnect", width / 2, 224, 0xDE413C)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {}

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(screen)
            1 -> {
                disable()
                buttonList.remove(button)
            }
        }
    }

    private fun disable() {
        mainScope.launch {
            delay(250L)
            AutoDisconnect.disable()
        }
    }

}
