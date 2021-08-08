package com.lambda.client.gui.mc

import com.lambda.client.plugin.PluginLoader
import com.lambda.client.plugin.api.Plugin
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiListExtended.IGuiListEntry
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation

class LambdaPluginListEntry(val owner: LambdaGuiPluginManager, val pluginData: LambdaPluginSelectionList.PluginData, val plugin: Plugin? = null, val loader: PluginLoader? = null) : IGuiListEntry {
    val mc: Minecraft = Minecraft.getMinecraft()
    private val unknownPlugin = ResourceLocation("textures/misc/unknown_server.png")

    var onlineVersion: String? = null

    override fun drawEntry(slotIndex: Int, x: Int, y: Int, listWidth: Int, slotHeight: Int, mouseX: Int, mouseY: Int, isSelected: Boolean, partialTicks: Float) {
        if (onlineVersion?.replace("v", "") != getVersion() && onlineVersion != null) {
            pluginData.pluginState = LambdaPluginSelectionList.PluginState.UPDATE
        }

        val fr = mc.fontRenderer
        fr.drawString(pluginData.name, x + 32 + 3, y + 1, 16777215)
        drawPluginIcon(x, y, unknownPlugin)
        var description = ""
        var version = ""
        var authors = ""
        if (pluginData.repoDescription != "") {
            description = pluginData.repoDescription
        }
        plugin?.let {
            description = it.description
            version = "v${it.version}"
            authors = "by ${it.authors.joinToString()}"
        }
        loader?.let {
            description = it.info.description
            version = "v${it.info.version}"
            authors = "by ${it.info.authors.joinToString()}"
        }
        val topRight = "$authors $version"
        fr.drawString(topRight, x + listWidth - fr.getStringWidth(topRight) - 5, y + 1, 0x808080)
        fr.drawString(description, x + 32 + 3, y + 2 + (fr.FONT_HEIGHT + 2) * 1, 0x808080)
        fr.drawString(pluginData.pluginState.displayName, x + 32 + 3, y + 2 + (fr.FONT_HEIGHT + 2) * 2, pluginData.pluginState.color)
    }

    override fun mousePressed(slotIndex: Int, mouseX: Int, mouseY: Int, mouseEvent: Int, relativeX: Int, relativeY: Int): Boolean {
        owner.selectPlugin(slotIndex)
        return false
    }

    override fun mouseReleased(slotIndex: Int, x: Int, y: Int, mouseEvent: Int, relativeX: Int, relativeY: Int) {
    }

    override fun updatePosition(slotIndex: Int, x: Int, y: Int, partialTicks: Float) {
    }

    private fun drawPluginIcon(x: Int, y: Int, resourceLocation: ResourceLocation?) {
        resourceLocation?.let {
            mc.textureManager.bindTexture(it)
            GlStateManager.enableBlend()
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0f, 0.0f, 32, 32, 32.0f, 32.0f)
            GlStateManager.disableBlend()
        }
    }

    private fun getVersion(): String {
        plugin?.let {
            return it.version
        }
        loader?.let {
            return it.info.version
        }
        return ""
    }

}