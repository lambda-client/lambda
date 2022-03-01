package com.lambda.client.gui.hudgui.elements.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud

internal object Velocity : LabelHud(
    name = "Velocity",
    description = "Shows your velocity",
    category = Category.PLAYER
) {
    private val newlines by setting("Vertical", true)
    private val showVelocity by setting("Show \"Velocity: \"", true)
    override fun SafeClientEvent.updateText() {
        if (newlines) {
            if (showVelocity) displayText.addLine("Velocity: ", primaryColor)
            displayText.addLine("X " + String.format("%.3f", player.motionX), secondaryColor)
            displayText.addLine("Y " + String.format("%.3f", player.motionY), secondaryColor)
            displayText.add("Z " + String.format("%.3f", player.motionZ), secondaryColor)
        } else {
            if (showVelocity) displayText.add("Velocity: ", primaryColor)
            displayText.addLine("X " + String.format("%.3f", player.motionX) + ", Y " + String.format("%.3f", player.motionY) + ", Z " + String.format("%.3f", player.motionZ), secondaryColor)
        }
    }
}