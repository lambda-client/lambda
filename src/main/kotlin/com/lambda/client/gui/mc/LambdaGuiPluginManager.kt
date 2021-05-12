package com.lambda.client.gui.mc

import com.lambda.client.plugin.PluginManager
import com.lambda.client.plugin.PluginManager.getLoaders
import com.lambda.client.plugin.PluginManager.load
import com.lambda.client.plugin.PluginManager.loadedPlugins
import com.lambda.client.plugin.PluginManager.unload
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import java.awt.Desktop
import java.io.File

class LambdaGuiPluginManager(private val previousScreen: GuiScreen): GuiScreen() {
    private var lineSpace = 0
    private var renderTime = 0
    private var availablePlugins = getLoaders().sortedBy { it.name }
    private val offset = 2

    override fun initGui() {
        super.initGui()
        lineSpace = fontRenderer.FONT_HEIGHT * 3
        updateGui()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (renderTime % 500 == 0) updateGui()

        drawDefaultBackground()
        drawCenteredString(fontRenderer, "Plugin Manager", width / 2, 50, 0xFFFFFF)

        if (availablePlugins.isEmpty()) {
            drawCenteredString(fontRenderer, "No plugins in directory .minecraft/lambda/plugins found.", width / 2, 50 + lineSpace * 2, 0x808080)
        } else {
            availablePlugins.forEachIndexed { index, pluginLoader ->
                val color = if (loadedPlugins.containsName(pluginLoader.name)) {
                    0xffffff
                } else {
                    0xaaaaaa
                }
                drawCenteredString(fontRenderer, pluginLoader.name + " v" + pluginLoader.info.version + " by " + pluginLoader.info.authors[0], width / 2, 50 + lineSpace * (index + 2), color)
            }
        }

        renderTime++

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        when {
            button.id == 0 -> mc.displayGuiScreen(previousScreen)
            button.id == 1 -> Desktop.getDesktop().open(File(PluginManager.pluginPath))
            button.id < 1000 -> {
                availablePlugins.forEachIndexed { pluginIndex, pluginLoader ->
                    if (loadedPlugins.containsName(pluginLoader.name)) {
                        if (pluginIndex == button.id - offset) {
                            loadedPlugins[pluginLoader.name]?.let { unload(it) }
                            updateGui()
                        }
                    }
                }
            }
            else -> {
                availablePlugins.forEachIndexed { pluginIndex, pluginLoader ->
                    if (pluginIndex == button.id - 1000 - offset) {
                        load(pluginLoader)
                        updateGui()
                    }
                }
            }
        }
    }

    private fun updateGui() {
        buttonList.clear()
        availablePlugins = getLoaders().sortedBy { it.name }

        buttonList.add(GuiButton(0, width / 2 - 50, height - 50, 100, 20, "Back"))
        buttonList.add(GuiButton(1, width / 2 - 200, height - 50, 130, 20, "Open Plugins Folder"))

        availablePlugins.forEachIndexed { index, pluginLoader ->
            if (loadedPlugins.containsName(pluginLoader.name)) {
                buttonList.add(GuiButton(offset + index, width / 2 + 150, 50 + lineSpace * (index + 2) - 7, 50, 20,"Unload"))
                if (!pluginLoader.info.hotReload) buttonList.firstOrNull { it.id == 1 + index }?.let { it.enabled = false }
            } else {
                buttonList.add(GuiButton(1000 + offset + index, width / 2 + 150, 50 + lineSpace * (index + 2) - 7, 50, 20,"Load"))
                if (!pluginLoader.info.hotReload) buttonList.firstOrNull { it.id == 1001 + index }?.let { it.enabled = false }
            }
        }
    }
}