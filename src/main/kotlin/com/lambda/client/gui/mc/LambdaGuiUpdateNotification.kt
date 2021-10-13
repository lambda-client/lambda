package com.lambda.client.gui.mc

import com.google.gson.JsonParser
import com.lambda.client.LambdaMod
import com.lambda.client.util.WebUtils
import com.lambda.client.util.color.ColorConverter
import com.lambda.client.util.threads.mainScope
import com.lambda.commons.utils.ConnectionUtils
import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiScreen
import net.minecraft.util.text.TextFormatting
import java.io.IOException

class LambdaGuiUpdateNotification : GuiScreen() {

    private val message = "A newer release of Lambda is available ($latest)."

    override fun initGui() {
        super.initGui()
        buttonList.add(GuiButton(0, width / 2 - 100, 200, "Download Latest (Recommended)"))
        buttonList.add(GuiButton(1, width / 2 - 100, 230, "${TextFormatting.RED}Use Current Version"))
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()
        drawCenteredString(fontRenderer, title, width / 2, 80, ColorConverter.rgbToHex(155, 144, 255))
        drawCenteredString(fontRenderer, message, width / 2, 110, ColorConverter.rgbToHex(255, 255, 255))

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 0) WebUtils.openWebLink(LambdaMod.WEBSITE_LINK + "/download")

        mc.displayGuiScreen(GuiMainMenu())
    }

    companion object {
        private const val title = "Lambda Update"

        private var latest: String? = null // latest version (null if no internet or exception occurred)
        private var isLatest = false

        @JvmStatic
        fun updateCheck() {
            mainScope.launch {
                try {
                    LambdaMod.LOG.info("Attempting Lambda update check...")

                    val parser = JsonParser()
                    val rawJson = ConnectionUtils.requestRawJsonFrom(LambdaMod.DOWNLOADS_API) {
                        throw it
                    }

                    latest = parser.parse(rawJson).asJsonObject.getAsJsonObject("stable")["name"].asString
                    isLatest = latest.equals(LambdaMod.VERSION_MAJOR)

                    if (!isLatest) {
                        LambdaMod.LOG.warn("You are running an outdated version of Lambda.\nCurrent: ${LambdaMod.VERSION_MAJOR}\nLatest: $latest")
                    } else {
                        LambdaMod.LOG.info("Your Lambda (" + LambdaMod.VERSION_MAJOR + ") is up-to-date with the latest stable release.")
                    }
                } catch (e: IOException) {
                    LambdaMod.LOG.error("An exception was thrown during the update check.", e)
                }
            }
        }
    }
}