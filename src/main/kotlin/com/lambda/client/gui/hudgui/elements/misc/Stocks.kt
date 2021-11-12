package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import io.finnhub.api.apis.DefaultApi
import io.finnhub.api.infrastructure.ApiClient

internal object Stocks : LabelHud(
    name = "Stocks",
    category = Category.MISC,
    description = "Info About a stock"
) {
    init {
        ApiClient.apiKey["token"] = "c5resoqad3ifnpn51ou0"
    }
    private val symbol by setting("Symbol", "TSLA")
    private val delay by setting("Delay", 100, 1..60, 1)
    private val timer = TickTimer(TimeUnit.SECONDS)
    //private val apiClient = DefaultApi()
    override fun SafeClientEvent.updateText() {
        if (timer.tick(delay.toLong())){
            //displayText.add("Current Price of $symbol is ${apiClient.quote(symbol)} $delay")
            displayText.add("THIS IS A LOT OF TEXT MARS ON TOP EZZZ")
        }
    }
}