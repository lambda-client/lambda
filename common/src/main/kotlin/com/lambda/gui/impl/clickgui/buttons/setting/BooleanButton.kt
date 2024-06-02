package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundManager.playSoundRandomly
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Rect
import com.lambda.util.math.Rect.Companion.inv
import com.lambda.util.math.Vec2d

class BooleanButton(
    setting: BooleanSetting,
    owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>
) : SettingButton<Boolean, BooleanSetting>(setting, owner) {
    private var active by animation.exp(0.0, 1.0, 0.6, ::value)
    private val zoomAnimation get() = lerp(2.0, 0.0, showAnimation)

    private val checkboxRect get() = Rect(rect.rightTop - Vec2d(rect.size.y * 1.75, 0.0), rect.rightBottom)
        .shrink(1.0 + zoomAnimation)

    private val knobStart get() = Rect.basedOn(checkboxRect.leftTop, Vec2d.ONE * checkboxRect.size.y)
    private val knobEnd get() = Rect.basedOn(checkboxRect.rightBottom, Vec2d.ONE * checkboxRect.size.y * -1.0).inv()
    private val checkboxKnob get() = lerp(knobStart, knobEnd, active).shrink(1.0 + zoomAnimation + interactAnimation)

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        if (e is GuiEvent.Render) {
            // Checkbox Background
            renderer.filled.build(
                rect = checkboxRect,
                roundRadius = checkboxRect.size.y,
                color = GuiSettings.mainColor.multAlpha(showAnimation * (0.2 + active * 0.2)),
                shade = GuiSettings.shade
            )

            // Checkbox Knob
            renderer.filled.build(
                rect = checkboxKnob,
                roundRadius = checkboxKnob.size.y,
                color = GuiSettings.backgroundColor.multAlpha(showAnimation),
                shade = GuiSettings.shadeBackground
            )
        }
    }

    override fun performClickAction(e: GuiEvent.MouseClick) {
        if (e.button != Mouse.Button.Left) return
        value = !value

        val sound = if (value) LambdaSound.BOOLEAN_SETTING_ON else LambdaSound.BOOLEAN_SETTING_OFF
        val pitch = if (value) 1.0 else 0.9
        playSoundRandomly(sound.event, pitch)
    }
}
