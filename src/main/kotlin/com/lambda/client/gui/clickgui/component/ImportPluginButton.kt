package com.lambda.client.gui.clickgui.component

import com.lambda.client.gui.rgui.component.BooleanSlider
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.math.Vec2f

object ImportPluginButton : BooleanSlider("Import...", 0.0, "Import plugins to Lambda") {
    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)
        if (buttonId == 0) FolderUtils.openFolder(FolderUtils.pluginFolder)
    }

    override fun onRelease(mousePos: Vec2f, buttonId: Int) {
        super.onRelease(mousePos, buttonId)
        if (buttonId == 1) FolderUtils.openFolder(FolderUtils.pluginFolder)
    }
}