package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.OnlineTimeManager

internal object OnlineTime: LabelHud(
    name = "OnlineTime",
    category = Category.MISC,
    description = "Displays how long you have been online"
) {
    override fun SafeClientEvent.updateText() {
        OnlineTimeManager.getOnlineTime()?.let { onlineTime ->
            displayText.add("Online:", secondaryColor)
            displayText.add(onlineTime.toString(), primaryColor)
        }
    }
}