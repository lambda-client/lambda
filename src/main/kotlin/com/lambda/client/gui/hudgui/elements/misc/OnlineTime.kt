package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.OnlineTimeManager
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal object OnlineTime: LabelHud(
    name = "OnlineTime",
    category = Category.MISC,
    description = "Displays how long you have been online"
) {
    override fun SafeClientEvent.updateText() {
        val onlineTime = OnlineTimeManager.getOnlineTime().toDouble(DurationUnit.SECONDS).roundToInt()
        displayText.add("Online:", secondaryColor)
        displayText.add(onlineTime.toDuration(DurationUnit.SECONDS).toString(), primaryColor)
    }
}