package com.lambda.client.gui.rgui.component

import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.setting.settings.impl.primitive.BooleanSetting
import com.lambda.client.util.math.Vec2f

class SettingButton(val setting: BooleanSetting) : BooleanSlider(setting.name, 0.0, setting.description, setting.visibility) {

    override val isBold
        get() = setting.isModified && ClickGUI.showModifiedInBold

    init {
        if (setting.value) value = 1.0
    }

    override fun onTick() {
        super.onTick()
        value = if (setting.value) 1.0 else 0.0
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        setting.value = !setting.value
    }
}