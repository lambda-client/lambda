package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Vec2d
import com.lambda.util.math.transform
import java.awt.Color
import kotlin.math.floor

class EnumSlider <T : Enum<T>> (
    setting: EnumSetting<T>,
    owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>
) : Slider<T, EnumSetting<T>>(setting, owner) {
    private val values = setting.enumValues
    private val enumSize = values.size

    override val progress get() = if (dragProgress != -1.0) dragProgress else
        transform(value.ordinal.toDouble(), 0.0, enumSize - 1.0, 0.0, 1.0)

    private var dragProgress = -1.0

    override fun setValueByProgress(progress: Double) {
        val entryIndex = floor(progress * enumSize).toInt().coerceIn(0, enumSize - 1)
        value = values[entryIndex]
        dragProgress = progress
    }

    override fun onPress(e: GuiEvent.MouseClick) {}

    override fun onRelease(e: GuiEvent.MouseClick) {
        if (dragProgress == -1.0) setting.next()
        dragProgress = -1.0
    }

    init {
        renderer.font {
            text = setting.displayValue

            val progress = 1.0 - activeAnimation
            scale = lerp(0.5, 1.0, progress)
            position = Vec2d(rect.right, rect.center.y) - Vec2d(ClickGui.windowPadding + stringWidth, 0.0)
            color = Color.WHITE.setAlpha(lerp(0.0, progress, showAnimation))
        }
    }
}