package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.util.Mouse

class BooleanButton(
    setting: BooleanSetting,
    owner: WindowComponent<*>
) : SettingButton<Boolean, BooleanSetting>(setting, owner) {
    override val text = setting.name
    override var activeAnimation by animation.exp(0.0, 1.0, 0.5, ::value)

    override fun performClickAction(e: GuiEvent.MouseClick) {
        if (e.button == Mouse.Button.Left && hovered) value = !value
    }
}