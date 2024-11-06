package com.lambda.newgui.impl.clickgui.settings

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.impl.clickgui.SettingLayout
import com.lambda.util.Mouse

class BooleanButton(
    owner: Layout,
    setting: BooleanSetting
) : SettingLayout<Boolean, BooleanSetting>(owner, setting) {
    init {
        titleBar.onMouseClick { button, action ->
            if (button == Mouse.Button.Left && action == Mouse.Action.Click) {
                setting.value = !setting.value
            }
        }
    }

    companion object {
        /**
         * Creates a [BooleanButton] - visual representation of the [BooleanSetting]
         */
        @UIBuilder
        fun Layout.booleanSetting(setting: BooleanSetting) =
            BooleanButton(this, setting).apply(children::add)
    }
}