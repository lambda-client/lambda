package com.lambda.client.gui.mc

import com.lambda.client.manager.managers.AltManager
import com.lambda.client.util.WebUtils
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMultiplayer
import net.minecraft.client.gui.GuiScreen

class LambdaGuiAltManager(private val parent: GuiMultiplayer) : GuiScreen() {
    private var msg = ""
    private var url: String? = null

    override fun initGui() {
        addButton(GuiButton(0, 0, height - 20, 50, 20, "Back"))
        addButton(GuiButton(1, width / 2 - 100, height / 2, "Login"))
        addButton(GuiButton(2, width / 2 - 100, height / 2 + 350, "Cancel"))

        runBlocking { refreshButtons() }
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(parent)
            1 -> defaultScope.launch(Dispatchers.IO) {
                AltManager.login(null, this@LambdaGuiAltManager::showMessage) {
                    url = null
                    msg = "Logged in as: $it"
                    refreshButtons()
                }
            }
            2 -> {
                url = null
                msg = ""
                AltManager.cancel()
            }
            else -> defaultScope.launch(Dispatchers.IO) {
                AltManager.login(buttonList[button.id].displayString, null) {
                    msg = "Logged in as: $it"
                }
            }
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun drawDefaultBackground() {
        super.drawDefaultBackground()

        drawCenteredString(mc.fontRenderer, msg, width / 2, height / 2 + 500, 0xFFFFFF)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        super.mouseClicked(mouseX, mouseY, mouseButton)
        val halfWidth = mc.fontRenderer.getStringWidth(msg)
        if (mouseX !in (width / 2 - halfWidth)..(width / 2 + halfWidth)) {
            return
        }
        if (mouseY !in (height / 2 + 500 - mc.fontRenderer.FONT_HEIGHT / 2)..(height / 2 + 500 + mc.fontRenderer.FONT_HEIGHT / 2)) {
            return
        }

        if (mouseButton != 0) {
            return
        }

        WebUtils.openWebLink(url ?: return)
    }

    override fun onGuiClosed() {
        AltManager.cancel()
    }

    private fun showMessage(code: String, url: String) {
        msg = "To log in enter the code \"$code\" at $url"
        setClipboardString(code)
        this.url = url
    }

    private suspend fun refreshButtons() {
        onMainThread {
            var offset = 0
            var index = 3
            buttonList.removeIf { it.id >= index }
            AltManager.getAccounts().forEach {
                addButton(GuiButton(index++, width - 100, offset, 100, 20, it.name))
                offset += 25
            }
        }
    }
}