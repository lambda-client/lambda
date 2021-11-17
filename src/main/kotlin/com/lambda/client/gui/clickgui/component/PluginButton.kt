package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.module.modules.client.CustomFont
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.plugin.PluginLoader
import com.lambda.client.plugin.PluginManager
import com.lambda.client.plugin.api.Plugin
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.graphics.font.LambdaFontRenderer
import com.lambda.client.util.math.Vec2f
import java.io.File

class PluginButton(var plugin: Plugin, var file: File) : BooleanSlider(plugin.name, 0.0, "${plugin.description} by ${plugin.authors.joinToString()}") {
    var isLoaded = true

    init {
        if (isLoaded) value = 1.0
    }

    override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onRender(vertexHelper, absolutePos)
        val details = "v${plugin.version}"
        val margin = if (CustomFont.isEnabled) 1.5f else 5.0f
        val color = if (value == 1.0) GuiColors.backGround else GuiColors.text
        FontRenderAdapter.drawString(details, LambdaClickGui.pluginWindow.width - margin - LambdaFontRenderer.getStringWidth(details), 1.0f, CustomFont.shadow, color = color)
    }

    override fun onTick() {
        super.onTick()
        value = if (isLoaded) 1.0 else 0.0
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        if (buttonId == 0) {
            if (isLoaded) {
                PluginManager.unload(plugin)
                isLoaded = false
            } else {
                PluginManager.load(PluginLoader(file))
                PluginManager.loadedPlugins[plugin.name]?.let {
                    plugin = it
                }
                isLoaded = true
            }
        }
    }
}