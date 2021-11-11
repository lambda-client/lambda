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

    private val symbol = setting("Symbol", "TSLA")
    private val delay by setting("Delay", 100, 5..60, 1)
    val timer = TickTimer(TimeUnit.SECONDS)
    var url = "https://finnhub.io/api/v1/quote?symbol="+symbol+"&token=c5resoqad3ifnpn51ou0"
    override fun SafeClientEvent.updateText() {
        if (timer.tick(delay.toLong())){
                //get a new request from the API
                //parse the response
                //display the update
        displayText.add("Current Price of $symbol is [price] $delay")
        }
    }
}