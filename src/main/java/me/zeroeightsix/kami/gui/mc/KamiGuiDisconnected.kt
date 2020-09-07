package me.zeroeightsix.kami.gui.mc

import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiScreen

class KamiGuiDisconnected(private val reason: String) : GuiScreen() {
    override fun initGui() {
        super.initGui()
        buttonList.add(GuiButton(0, width / 2 - 100, 200, "Okay"))
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)
        drawCenteredString(fontRenderer, "[AutoLog] Logged because of:", width / 2, 80, 10260478)
        drawCenteredString(fontRenderer, reason, width / 2, 94, 16777215)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {}
    override fun actionPerformed(button: GuiButton) {
        mc.displayGuiScreen(GuiMainMenu())
    }
}
