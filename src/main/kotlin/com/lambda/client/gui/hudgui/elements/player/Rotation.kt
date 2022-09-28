package com.lambda.client.gui.hudgui.elements.player

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.math.RotationUtils

internal object Rotation : LabelHud(
    name = "Rotation",
    category = Category.PLAYER,
    description = "Player rotation"
) {

    override fun SafeClientEvent.updateText() {
        val yaw = MathUtils.round(RotationUtils.normalizeAngle(mc.player?.rotationYaw ?: 0.0f), 1)
        val pitch = MathUtils.round(mc.player?.rotationPitch ?: 0.0f, 1)

        displayText.add("Yaw", secondaryColor)
        displayText.add(yaw.toString(), primaryColor)
        displayText.add("Pitch", secondaryColor)
        displayText.add(pitch.toString(), primaryColor)
    }

}