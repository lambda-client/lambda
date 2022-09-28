package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud

internal object ServerInfo : LabelHud(
    name = "ServerInfo",
    category = Category.MISC,
    description = "Brand / type and ip of the server"
) {
    private val showBrand by setting("Show Brand", true)
    private val showIP by setting("Show full server ip", false)

    override fun SafeClientEvent.updateText() {
        if (mc.isIntegratedServerRunning) {
            displayText.add("Singleplayer: " + mc.player?.serverBrand)
        } else {
            if (showBrand) {
                val serverBrand = player.serverBrand ?: "Unknown Server Type"
                displayText.add("Brand", secondaryColor)
                displayText.addLine(serverBrand, primaryColor)
            }

            if (showIP) {
                val serverIp = player.connection.networkManager.remoteAddress.toString()
                displayText.add("IP", secondaryColor)
                displayText.addLine(serverIp, primaryColor)
            }
        }
    }

}