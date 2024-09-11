package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.lerp
import com.lambda.util.math.Vec2d
import com.lambda.util.math.transform
import com.lambda.util.extension.displayValue
import com.lambda.util.math.setAlpha
import java.awt.Color

class EnumSlider<T : Enum<T>>(
    setting: EnumSetting<T>,
    owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>,
) : Slider<T, EnumSetting<T>>(setting, owner) {
    private val values = setting.enumValues
    private val enumSize = values.size

    override val progress get() = transform(value.ordinal.toDouble(), 0.0, enumSize - 1.0, 0.0, 1.0)
    private var valueSetByDrag = false

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        if (e is GuiEvent.Render) {
            // Enum entry name
            renderer.font.apply {
                val text = value.displayValue
                val progress = 1.0 - activeAnimation
                val scale = lerp(progress, 0.5, 1.0)
                val width = getWidth(text, scale)
                val position = Vec2d(rect.right, rect.center.y) - Vec2d(ClickGui.windowPadding + width, 0.0)
                val color = Color.WHITE.setAlpha(lerp(showAnimation, 0.0, progress))

                build(text, position, color, scale)
            }
        }
    }

    override fun setValueByProgress(progress: Double) {
        val entryIndex = (progress * enumSize).floorToInt().coerceIn(0, enumSize - 1)
        value = values[entryIndex]
        valueSetByDrag = true
    }

    override fun onPress(e: GuiEvent.MouseClick) {
        valueSetByDrag = false
    }

    override fun onRelease(e: GuiEvent.MouseClick) {
        if (valueSetByDrag) return
        playClickSound()
        setting.next()
    }
}
