package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.module.modules.client.CustomFont
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.math.Vec2f

object DownloadPluginButton : BooleanSlider("Download...", 0.0, "Download plugins from Github") {
    override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onRender(vertexHelper, absolutePos)
        val updateCount = LambdaClickGui.remotePluginWindow.children
            .filterIsInstance<RemotePluginButton>()
            .count { it.update }

        if (updateCount > 0) {
            RenderUtils2D.drawCircleFilled(
                vertexHelper,
                Vec2d(width - (height / 2.1 * 2), height / 2.0),
                height / 2.5,
                0,
                GuiColors.primary
            )

            FontRenderAdapter.drawString(
                "$updateCount",
                width - (height / 2.1f * 2) - FontRenderAdapter.getStringWidth(updateCount.toString(), customFont = CustomFont.isEnabled) / 2,
                0.0f,
                CustomFont.shadow,
                color = GuiColors.backGround
            )
        }
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        if (buttonId == 0) LambdaClickGui.toggleRemotePluginWindow()
    }

    override fun onRelease(mousePos: Vec2f, buttonId: Int) {
        super.onRelease(mousePos, buttonId)
        if (buttonId == 1) LambdaClickGui.toggleRemotePluginWindow()
    }
}