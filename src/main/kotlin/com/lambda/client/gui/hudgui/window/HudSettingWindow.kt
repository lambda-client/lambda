package com.lambda.client.gui.hudgui.window

import com.lambda.client.gui.hudgui.AbstractHudElement
import com.lambda.client.gui.rgui.windows.SettingWindow
import com.lambda.client.setting.settings.AbstractSetting

class HudSettingWindow(
    hudElement: AbstractHudElement,
    posX: Float,
    posY: Float
) : SettingWindow<AbstractHudElement>(hudElement.name, hudElement, posX, posY, SettingGroup.NONE) {

    override fun getSettingList(): List<AbstractSetting<*>> {
        return element.settingList
    }

}