package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import io.finnhub.api.apis.DefaultApi
import io.finnhub.api.infrastructure.ApiClient
import com.google.gson.Gson
import com.lambda.client.util.threads.defaultScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object Stocks : LabelHud(
    name = "Stocks",
    category = Category.MISC,
    description = "Info About a stock"
) {
    init {
        ApiClient.apiKey["token"] = "c5resoqad3ifnpn51ou0"
    }

    private val symbol by setting("Symbol", "TSLA")
    private val tickdelay by setting("Delay", 30, 20..120, 1)
    private val ticktimer = TickTimer(TimeUnit.SECONDS)
    private val apiClient = DefaultApi()
    private val gson = Gson()
    private var jsondata = "0"
    private var stockData = StockData(0)
    override fun SafeClientEvent.updateText() {
        if (ticktimer.tick(tickdelay)) {
            updateStockData()

        }
        displayText.add("Price of $symbol is ${stockData.c}")
    }

    private fun updateStockData() {
        defaultScope.launch(Dispatchers.IO) {
            runCatching {
                jsondata = apiClient.quote(symbol).toString()
                gson.fromJson(jsondata, StockData::class.java)
            }.getOrNull()?.let {
                stockData = it
            }
        }
    }

    private class StockData(
        val c: Int,
    )
}
