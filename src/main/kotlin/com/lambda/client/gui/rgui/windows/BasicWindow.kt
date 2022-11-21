package com.lambda.client.gui.rgui.windows

import com.lambda.client.commons.interfaces.Nameable
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.setting.GuiConfig
import com.lambda.client.setting.configs.AbstractConfig
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.math.Vec2f

/**
 * Window with rectangle rendering
 */
open class BasicWindow(
    name: String,
    posX: Float,
    posY: Float,
    width: Float,
    height: Float,
    settingGroup: SettingGroup,
    config: AbstractConfig<out Nameable> = GuiConfig
) : CleanWindow(name, posX, posY, width, height, settingGroup, config) {

    override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onRender(vertexHelper, absolutePos)
        RenderUtils2D.drawRoundedRectFilled(
            vertexHelper,
            Vec2d(0.0, 0.0),
            Vec2f(renderWidth, renderHeight).toVec2d(),
            ClickGUI.radius,
            color = GuiColors.backGround
        )

        if (ClickGUI.windowOutline) {
            RenderUtils2D.drawRoundedRectOutline(
                vertexHelper,
                Vec2d(0.0, 0.0),
                Vec2f(renderWidth, renderHeight).toVec2d(),
                ClickGUI.radius,
                lineWidth = ClickGUI.outlineWidth,
                color = GuiColors.outline
            )
        }
    }

}