package com.lambda.client.gui.mc

import com.lambda.client.plugin.PluginLoader
import com.lambda.client.plugin.PluginManager.getLoaders
import com.lambda.client.plugin.PluginManager.load
import com.lambda.client.plugin.PluginManager.loadedPlugins
import com.lambda.client.plugin.PluginManager.unload
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import java.util.*

class LambdaGuiPluginManager(private val previousScreen: GuiScreen): GuiScreen() {
    private var lineSpace = 0
    private var timee = 0
    private var availablePlugins = getLoaders()

    override fun initGui() {
        super.initGui()
        lineSpace = fontRenderer.FONT_HEIGHT * 3
        updateGui()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (timee % 100 == 0) updateGui()

        drawBackground(0)
        drawCenteredString(fontRenderer, "Plugin Manager", width / 2, 50, 0xFFFFFF)

        if (loadedPlugins.isEmpty()) {
            drawCenteredString(fontRenderer, "No plugins loaded.", width / 2, 50 + lineSpace * 2, 0x808080)
        } else {
            availablePlugins.forEachIndexed { index, pluginLoader ->
                drawCenteredString(fontRenderer, pluginLoader.name, width / 2, 50 + lineSpace * (index + 2), 0xffffff)
            }
        }

        timee++

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        when {
            button.id == 0 -> mc.displayGuiScreen(previousScreen)
            button.id < 1000 -> {
                loadedPlugins.forEachIndexed { pluginIndex, plugin ->
                    if (pluginIndex == button.id - 1) {
                        unload(plugin)
                        updateGui()
                    }
                }
            }
            else -> {
                availablePlugins.forEachIndexed { pluginIndex, pluginLoader ->
                    if (pluginIndex == button.id - 1000) {
                        load(pluginLoader)
                        updateGui()
                    }
                }
            }
        }
    }

    private fun updateGui() {
        buttonList.clear()
        buttonList.add(GuiButton(0, width / 2 - 100, height - 50, "Back"))
        getLoaders().forEachIndexed { index, pluginLoader ->
            if (loadedPlugins.containsName(pluginLoader.name)) {
                buttonList.add(GuiButton(1 + index, width / 2 + 100, 50 + lineSpace * (index + 2) - 7, 50, 20,"Unload"))
            } else {
                buttonList.add(GuiButton(1000 + index, width / 2 + 100, 50 + lineSpace * (index + 2) - 7, 50, 20,"Load"))
            }
        }
    }
}