package com.lambda.client.gui.mc

import com.lambda.client.plugin.PluginManager
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import java.util.*

class LambdaGuiPluginManager(private val previousScreen: GuiScreen): GuiScreen() {

    override fun initGui() {
        super.initGui()
        buttonList.add(GuiButton(0, width / 2 - 100, height - 50, "Back"))
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)
//        drawCenteredString(fontRenderer, "Plugin Manager", width / 2, 80, 0x9B90FF)
        val pluginList = TreeSet<String>()
        PluginManager.loadedPlugins.forEach {
            pluginList.add(it.config.name)
        }
        drawList("Loaded Plugins:", pluginList)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(previousScreen)
        }
    }

    private fun drawList(title: String, list: Set<String>) {
        drawCenteredString(fontRenderer, title, width / 2, 50, 0xFFFFFF)

        list.forEachIndexed { index, s ->
            drawCenteredString(fontRenderer, s, width / 2, 50 + (fontRenderer.FONT_HEIGHT + 2) * (index + 1), 0x808080)
        }
    }
}