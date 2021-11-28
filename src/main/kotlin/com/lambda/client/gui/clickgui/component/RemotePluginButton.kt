package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.module.modules.client.CustomFont
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.Vec2f

class RemotePluginButton(
    pluginName: String,
    val description: String,
    val version: String,
    val downloadUrl: String,
    val fileName: String
) : BooleanSlider(pluginName, 0.0, description) {
    var update = false

    override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onRender(vertexHelper, absolutePos)
        var details = version.replace("v", "")
        var color = if (value == 1.0) GuiColors.backGround else GuiColors.text
        if (update) {
            details = "$details ▲"
            color = ColorHolder(0, 255, 0)
        }
        FontRenderAdapter.drawString(
            details,
            width - 1.5f - FontRenderAdapter.getStringWidth(details, customFont = CustomFont.isEnabled),
            1.0f,
            CustomFont.shadow,
            color = color
        )
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        if (buttonId == 0) {
            LambdaClickGui.downloadPlugin(this)
            visible = false
            update = false
            LambdaClickGui.pluginWindow.children
                .firstOrNull { name == it.name }
                ?.let {
                    LambdaClickGui.pluginWindow.remove(it)
                }
            LambdaClickGui.disabledRemotes.add(this)
        }
    }
}