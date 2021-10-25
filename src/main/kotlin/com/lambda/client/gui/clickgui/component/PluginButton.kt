package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.plugin.api.Plugin
import com.lambda.client.util.math.Vec2f

class PluginButton(val plugin: Plugin) : BooleanSlider(plugin.name, 0.0, plugin.description) {
    init {
        if (plugin.isRegistered) value = 1.0
    }

    override fun onTick() {
        super.onTick()
        value = if (plugin.isRegistered) 1.0 else 0.0
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        if (buttonId == 0) if (plugin.isRegistered) plugin.unregister() else plugin.register()
    }

    override fun onRelease(mousePos: Vec2f, buttonId: Int) {
        super.onRelease(mousePos, buttonId)
//        if (buttonId == 1) LambdaClickGui.displayPluginSettingWindow(plugin)
    }
}