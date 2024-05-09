package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Rect
import com.lambda.util.math.Rect.Companion.inv
import com.lambda.util.math.Vec2d

class BooleanButton(
    setting: BooleanSetting,
    owner: ChildLayer.Drawable<*>
) : SettingButton<Boolean, BooleanSetting>(setting, owner) {
    override val text = setting.name
    private var active by animation.exp(0.0, 1.0, 0.5, ::value)

    private val checkboxRect get() = Rect(rect.rightTop - Vec2d(rect.size.y * 1.75, 0.0), rect.rightBottom)
        .shrink(2.0 - showAnimation)

    private val knobStart get() = Rect.basedOn(checkboxRect.leftTop, Vec2d.ONE * checkboxRect.size.y)
    private val knobEnd get() = Rect.basedOn(checkboxRect.rightBottom, Vec2d.ONE * checkboxRect.size.y * -1.0).inv()
    private val checkboxKnob get() = lerp(knobStart, knobEnd, active)
        .shrink(2.0 - showAnimation + interactAnimation)

    init {
        // Checkbox Background
        renderer.filled {
            position = checkboxRect
            roundRadius = checkboxRect.size.y
            color(GuiSettings.mainColor.setAlpha(showAnimation))
        }

        // Checkbox Knob
        renderer.filled {
            position = checkboxKnob
            roundRadius = checkboxKnob.size.y
            color(GuiSettings.backgroundColor.setAlpha(showAnimation))
        }
    }

    override fun performClickAction(e: GuiEvent.MouseClick) {
        if (e.button == Mouse.Button.Left && hovered) value = !value
    }
}