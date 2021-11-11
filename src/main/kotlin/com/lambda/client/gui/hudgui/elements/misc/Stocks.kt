package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

internal object Stocks : LabelHud(
    name = "Stocks",
    category = Category.MISC,
    description = "Info About a stock"
) {

    private val symbol = setting("Symbol", "TSLA")
    private val delay by setting("Delay", 100, 5..60, 1)

    override fun SafeClientEvent.updateText() {
        val client = HttpClient(CIO)
        if (timer.tick(delay.toLong()) {
                //get a new request from the API
                //parse the response
                //display the update
        displayText.add("Current Price of $symbol is [price] $delay")
        }
    }
}