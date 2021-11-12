package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.plugin.PluginManager
import com.lambda.client.util.math.Vec2f
import java.awt.Desktop
import java.io.File

object ImportPluginButton : BooleanSlider("Import...", 0.0, "Import plugins to lambda") {
    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        if (buttonId == 0) Desktop.getDesktop().open(File(PluginManager.pluginPath))
    }

    override fun onRelease(mousePos: Vec2f, buttonId: Int) {
        super.onRelease(mousePos, buttonId)
        if (buttonId == 1) Desktop.getDesktop().open(File(PluginManager.pluginPath))
    }
}