package com.lambda.client.gui.hudgui.elements.player

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.TimerManager

internal object TimerSpeed : LabelHud(
    name = "TimerSpeed",
    category = Category.PLAYER,
    description = "Client side timer speed"
) {

    override fun SafeClientEvent.updateText() {
        val timerSpeed = MathUtils.round(50.0f / TimerManager.tickLength, 2)

        displayText.add("%.2f".format(timerSpeed), primaryColor)
        displayText.add("x", secondaryColor)
    }

}