package com.lambda.client.gui.mc

import com.lambda.client.plugin.PluginLoader
import com.lambda.client.plugin.api.Plugin
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiListExtended.IGuiListEntry
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation

class LambdaPluginListEntry(val owner: LambdaGuiPluginManager, val pluginData: LambdaPluginSelectionList.PluginData, val plugin: Plugin? = null, val loader: PluginLoader? = null): IGuiListEntry {
    val mc: Minecraft = Minecraft.getMinecraft()
    private val unknownPlugin = ResourceLocation("textures/misc/unknown_server.png")

    override fun updatePosition(slotIndex: Int, x: Int, y: Int, partialTicks: Float) {
        //
    }

    override fun drawEntry(slotIndex: Int, x: Int, y: Int, listWidth: Int, slotHeight: Int, mouseX: Int, mouseY: Int, isSelected: Boolean, partialTicks: Float) {
        val fr = mc.fontRenderer
        fr.drawString(pluginData.name, x + 32 + 3, y + 1, 16777215)
        var description = ""
        plugin?.let {
            description = it.description
        }
        loader?.let {
            description = it.info.description
        }
        if (pluginData.repoDescription != "") {
            description = pluginData.repoDescription
        }
        fr.drawString(description, x + 32 + 3, y + 1 + fr.FONT_HEIGHT * 1, 16777215)
        fr.drawString(pluginData.pluginState.toString(), x + 32 + 3, y + 1 + fr.FONT_HEIGHT * 2, 16777215)
        drawPluginIcon(x, y, unknownPlugin)
    }

    override fun mousePressed(slotIndex: Int, mouseX: Int, mouseY: Int, mouseEvent: Int, relativeX: Int, relativeY: Int): Boolean {
        owner.selectPlugin(slotIndex)

        return false
    }

    override fun mouseReleased(slotIndex: Int, x: Int, y: Int, mouseEvent: Int, relativeX: Int, relativeY: Int) {
        //
    }

    private fun drawPluginIcon(x: Int, y: Int, resourceLocation: ResourceLocation?) {
        resourceLocation?.let {
            mc.textureManager.bindTexture(it)
            GlStateManager.enableBlend()
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0f, 0.0f, 32, 32, 32.0f, 32.0f)
            GlStateManager.disableBlend()
        }
    }

}