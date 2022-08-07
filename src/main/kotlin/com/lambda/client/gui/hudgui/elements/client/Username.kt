package com.lambda.client.gui.hudgui.elements.client

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud

internal object Username : LabelHud(
    name = "Username",
    category = Category.CLIENT,
    description = "Player username"
) {

    private val prefix = setting("Prefix", "Welcome")
    private val suffix = setting("Suffix", "")

    override fun SafeClientEvent.updateText() {
        if (prefix.value != "") displayText.add(prefix.value, primaryColor)
        displayText.add(mc.session.username, secondaryColor)
        if (suffix.value != "") displayText.add(suffix.value, primaryColor)
    }

}
