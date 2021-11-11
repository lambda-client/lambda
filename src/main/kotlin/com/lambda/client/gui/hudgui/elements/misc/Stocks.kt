package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud

internal object Stocks : LabelHud(
    name = "Stocks",
    category = Category.MISC,
    description = "Info About a stock"
) {

    private val symbol = setting("Symbol", "TSLA")
    private val delay by setting("Delay", 100, 5..60, 1)
    override fun SafeClientEvent.updateText() {
        displayText.add("Current Price of $symbol is [price] $delay")
        }
    }