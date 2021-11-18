package com.lambda.client.gui.hudgui.elements.misc

import com.google.gson.Gson
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.WebUtils
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import kotlin.math.round

internal object Stocks : LabelHud(
    name = "Stocks",
    category = Category.MISC,
    description = "Info about a stock"
) {
    private var symbol by setting("Symbol", "TSLA")
    private val tickdelay by setting("Delay", 30, 20..120, 1)
    private val ticktimer = TickTimer(TimeUnit.SECONDS)
    private var url = "https://finnhub.io/api/v1/quote?symbol=$symbol&token=c5resoqad3ifnpn51ou0"
    private var stockData = StockData(0.0)
    private var price = 0.0
    private var newrl = url

    override fun SafeClientEvent.updateText() {
        if (ticktimer.tick(tickdelay)) {
            updateStockData()
        }
        displayText.add("Current Price of $symbol is ", primaryColor)
        displayText.add("$price", secondaryColor)
    }

    private fun updateStockData() {
        sendChatMessage("url: $url, newrl: $newrl ")
        newrl = url
            price = Gson().fromJson(WebUtils.getUrlContents(url), StockData::class.java).c
    }

    private class StockData(
        val c: Double
    )
}
