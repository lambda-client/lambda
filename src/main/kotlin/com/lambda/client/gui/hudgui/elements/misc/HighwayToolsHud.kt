package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud

internal object HighwayToolsHud : LabelHud(
    name = "HighwayToolsHud",
    category = Category.MISC,
    description = "Hud for the HighwayTools module"
) {


    override fun SafeClientEvent.updateText() {

    }
}