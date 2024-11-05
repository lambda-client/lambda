package com.lambda.newgui.impl.clickgui.settings

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.core.TextField.Companion.textField
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout

class BooleanButton(
    owner: Layout,
    val setting: BooleanSetting
) : Layout(owner, true, true) {
    init {
        overrideSize(owner::renderWidth, NewCGui::settingsHeight)

        textField {
            text = setting.name
            scale = NewCGui.fontScale * 0.95
            offsetX = NewCGui.fontOffset
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