package com.lambda.client.gui.hudgui.elements.misc

internal object Stocks : LabelHud(
    name = "Stocks",
    category = Category.MISC,
    description = "Info About a stock"
) {

    private val symbol = setting("Symbol", "TSLA")
    private val delay by setting("Delay", 100, 5..60, 1)
    override fun SafeClientEvent.updateText() {
        if (symbol = symbol) {
            displayText.add("Current Price of " + symbol + " is [price] "+ delay)
        }
        }
    }
}