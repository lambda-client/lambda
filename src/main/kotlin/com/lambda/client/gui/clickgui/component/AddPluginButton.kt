package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.util.math.Vec2f

object AddPluginButton : BooleanSlider("Add plugin...", 0.0, "Download plugins from github") {
    override fun onTick() {
        super.onTick()
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        if (buttonId == 0) LambdaClickGui.toggleRemotePluginWindow()
    }

    override fun onRelease(mousePos: Vec2f, buttonId: Int) {
        super.onRelease(mousePos, buttonId)
        if (buttonId == 1) LambdaClickGui.toggleRemotePluginWindow()
    }
}