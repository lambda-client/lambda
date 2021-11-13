package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.module.modules.client.CustomFont
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.graphics.font.LambdaFontRenderer
import com.lambda.client.util.math.Vec2f

class RemotePluginButton(
    pluginName: String,
    val description: String,
    val authors: String,
    val version: String,
    val downloadUrl: String,
    val fileName: String
) : BooleanSlider(pluginName, 0.0, "$description by $authors") {
    override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onRender(vertexHelper, absolutePos)
        val details = "v${version}"
        val margin = if (CustomFont.isEnabled) 1.5f else 5.0f
        FontRenderAdapter.drawString(details,  LambdaClickGui.remotePluginWindow.width - margin - LambdaFontRenderer.getStringWidth(details), 1.0f, CustomFont.shadow, color = GuiColors.backGround)
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        if (buttonId == 0) LambdaClickGui.downloadPlugin(this)
    }
}