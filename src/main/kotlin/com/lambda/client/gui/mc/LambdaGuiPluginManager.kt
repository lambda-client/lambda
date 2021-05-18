package com.lambda.client.gui.mc

import com.lambda.client.plugin.PluginManager
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import java.awt.Desktop
import java.io.File
import java.io.IOException

class LambdaGuiPluginManager(private val previousScreen: GuiScreen): GuiScreen() {
    private lateinit var pluginListSelector: LambdaPluginSelectionList

    override fun initGui() {
        pluginListSelector = LambdaPluginSelectionList(this, mc, width, height, 32, height - 64, 36)
        pluginListSelector.collectPlugins()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        buttonList.clear()

        drawDefaultBackground()
        pluginListSelector.drawScreen(mouseX, mouseY, partialTicks)

        drawCenteredString(fontRenderer, "Plugin Manager", width / 2, 20, 16777215)
        buttonList.add(GuiButton(0, width / 2 - 50, height - 50, 100, 20, "Back"))
        buttonList.add(GuiButton(1, width / 2 - 200, height - 50, 130, 20, "Open Plugins Folder"))
//        buttonList.add(GuiButton(2, width / 2 + 120, height - 50, 130, 20, "Load"))

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun updateScreen() {
//        buttonList.firstOrNull { it.id == 2 }?.let {
//            it.displayString = (pluginListSelector.getListEntry(pluginListSelector.selectedSlotIndex) as LambdaPluginListEntry).pluginData.pluginState.toString()
//        }
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            0 -> mc.displayGuiScreen(previousScreen)
            1 -> Desktop.getDesktop().open(File(PluginManager.pluginPath))
        }
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        super.keyTyped(typedChar, keyCode)
    }

    @Throws(IOException::class)
    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        super.mouseClicked(mouseX, mouseY, mouseButton)
        pluginListSelector.mouseClicked(mouseX, mouseY, mouseButton)
    }

    @Throws(IOException::class)
    override fun handleMouseInput() {
        super.handleMouseInput()
        pluginListSelector.handleMouseInput()
    }

    fun selectPlugin(index: Int) {
        pluginListSelector.selectedSlotIndex = index
    }

}