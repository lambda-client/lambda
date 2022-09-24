package com.lambda.client.gui.rgui.component

import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.module.modules.client.CustomFont
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.setting.settings.impl.other.BindSetting
import com.lambda.client.util.Bind.Companion.minMouseIndex
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.Vec2f
import org.lwjgl.input.Keyboard

class BindButton(
    private val setting: BindSetting
) : Slider(setting.name, 0.0, setting.description, setting.visibility) {

    override val isBold
        get() = setting.isModified && ClickGUI.showModifiedInBold

    override val renderProgress: Double = 0.0

    override fun onRelease(mousePos: Vec2f, buttonId: Int) {
        super.onRelease(mousePos, buttonId)
        if (listening && buttonId >= minMouseIndex) {
            setting.value.apply {
                setMouseBind(buttonId + 1)
            }
        }
        listening = !listening
    }

    override fun onKeyInput(keyCode: Int, keyState: Boolean) {
        super.onKeyInput(keyCode, keyState)
        if (listening && !keyState) {
            setting.value.apply {
                if (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) clear()
                else setBind(keyCode)
                listening = false
            }
        }
    }

    override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onRender(vertexHelper, absolutePos)

        val valueText = if (listening) "Listening"
        else setting.value.toString()

        protectedWidth = FontRenderAdapter.getStringWidth(valueText, 0.75f).toDouble()
        val posX = (renderWidth - protectedWidth - 2.0f).toFloat()
        val posY = renderHeight - 2.0f - FontRenderAdapter.getFontHeight(0.75f)
        FontRenderAdapter.drawString(valueText, posX, posY, color = GuiColors.text, drawShadow = CustomFont.shadow, scale = 0.75f)
    }
}