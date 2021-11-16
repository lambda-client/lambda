package com.lambda.client.gui.hudgui.elements.misc

import com.google.gson.Gson
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.WebUtils

internal object Stocks : LabelHud(
    name = "Stocks",
    category = Category.MISC,
    description = "Info about a stock"
) {
    private val symbol by setting("Symbol", "TSLA")
    private val tickdelay by setting("Delay", 30, 20..120, 1)
    private val ticktimer = TickTimer(TimeUnit.SECONDS)
    val url = "https://finnhub.io/api/v1/quote?symbol=$symbol&token=c5resoqad3ifnpn51ou0"

    override fun SafeClientEvent.updateText() {
        if (ticktimer.tick(tickdelay)) {
            updateStockData()
        }
    }

    private fun updateStockData() {
        displayText.add("Price of $symbol is ${Gson().fromJson(WebUtils.getUrlContents(url), StockData::class.java).c}")
    }
    private class StockData(val c: Double)
}
