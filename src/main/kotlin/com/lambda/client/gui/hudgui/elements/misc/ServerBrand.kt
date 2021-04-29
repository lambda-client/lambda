package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud

internal object ServerBrand : LabelHud(
    name = "ServerBrand",
    category = Category.MISC,
    description = "Brand / type of the server"
) {

    override fun SafeClientEvent.updateText() {
        if (mc.isIntegratedServerRunning) {
            displayText.add("Singleplayer: " + mc.player?.serverBrand)
        } else {
            val serverBrand = mc.player?.serverBrand ?: "Unknown Server Type"
            displayText.add(serverBrand, primaryColor)
        }
    }

}