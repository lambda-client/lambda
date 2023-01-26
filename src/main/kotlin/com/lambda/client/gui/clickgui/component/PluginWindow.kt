package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.rgui.windows.ListWindow

class PluginWindow(
    cname: String,
    cPosX: Float,
    cPosY: Float,
) : ListWindow(cname, cPosX, cPosY, 120.0f, 200.0f, SettingGroup.CLICK_GUI, drawHandle = true) {
    override val minHeight: Float
        get() = 100.0f
}