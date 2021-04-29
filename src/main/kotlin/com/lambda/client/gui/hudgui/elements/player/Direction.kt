package com.lambda.client.gui.hudgui.elements.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.math.Direction

internal object Direction : LabelHud(
    name = "Direction",
    category = Category.PLAYER,
    description = "Direction of player facing to"
) {

    override fun SafeClientEvent.updateText() {
        val entity = mc.renderViewEntity ?: player
        val direction = Direction.fromEntity(entity)
        displayText.add(direction.displayName, secondaryColor)
        displayText.add("(${direction.displayNameXY})", primaryColor)
    }

}