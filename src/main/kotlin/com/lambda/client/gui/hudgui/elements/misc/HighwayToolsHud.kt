package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.module.modules.misc.HighwayTools.gatherStatistics

internal object HighwayToolsHud : LabelHud(
    name = "HighwayToolsHud",
    category = Category.MISC,
    description = "Hud for HighwayTools module"
) {
    override fun SafeClientEvent.updateText() {
        gatherStatistics(displayText)
    }
}