package com.lambda.client.gui.mc

import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiOptionButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.resources.I18n

class LambdaGuiIncompat : GuiScreen() {

    override fun initGui() {
        buttonList.clear()
        // I used the minecraft "Out of Memory" class as a reference, turns out it has exactly the buttons I need, with internationalization support too
        buttonList.add(GuiOptionButton(0, width / 2 - 155, height / 4 + 120, I18n.format("gui.toTitle", *arrayOfNulls(0))))
        buttonList.add(GuiOptionButton(1, width / 2 - 155 + 160, height / 4 + 120, I18n.format("menu.quit", *arrayOfNulls(0))))
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 0) {
            mc.displayGuiScreen(GuiMainMenu())
        } else if (button.id == 1) {
            mc.shutdown()
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        drawCenteredString(fontRenderer, "Found KAMI Blue!", width / 2, height / 4 - 60 + 20, 16777215)
        drawString(fontRenderer, "It appears you are using KAMI Blue alongside Lambda Client.", width / 2 - 140, height / 4 - 60 + 60 + 0, 10526880)
        drawString(fontRenderer, "Lambda Client is a continued version of KAMI Blue,", width / 2 - 140, height / 4 - 60 + 60 + 18, 10526880)
        drawString(fontRenderer, "and is not compatible as a result.", width / 2 - 140, height / 4 - 60 + 60 + 27, 10526880)
        drawString(fontRenderer, "It is not recommended to use both clients", width / 2 - 140, height / 4 - 60 + 60 + 45, 10526880)
        drawString(fontRenderer, "together, since many modules will override each other.", width / 2 - 140, height / 4 - 60 + 60 + 54, 10526880)
        drawString(fontRenderer, "You may continue, but it may cause serious issues,", width / 2 - 140, height / 4 - 60 + 60 + 63, 10526880)
        drawString(fontRenderer, "and support will not be provided to dual users.", width / 2 - 140, height / 4 - 60 + 60 + 72, 10526880)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }
}