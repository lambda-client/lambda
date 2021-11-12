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
    private val delay by setting("Delay", 100, 5..60, 1)
    private val timer = TickTimer(TimeUnit.SECONDS)
    private lateinit var price: String
    private lateinit var liveprice: String
    private val apiClient = DefaultApi()
    override fun SafeClientEvent.updateText() {
        if (timer.tick(delay.toLong())){
            price = apiClient.quote(symbol).toString()
            liveprice = price
            displayText.add("Current Price of $symbol is $liveprice $delay")
        }
        liveprice = price
    }

}