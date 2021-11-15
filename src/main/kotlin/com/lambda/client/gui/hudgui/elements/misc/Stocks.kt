package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
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
    private val tickdelay by setting("Delay", 10, 5..60, 1)
    private val ticktimer = TickTimer(TimeUnit.SECONDS)
    private val apiClient = DefaultApi()
    private var stockdata = "0"
    override fun SafeClientEvent.updateText() {
            if (ticktimer.tick(tickdelay)) {
                updateStockData()
                displayText.add("Price of $symbol is $stockdata")
            }
        }
    private fun updateStockData() {
        stockdata = apiClient.quote(symbol).toString()
        MessageSendHelper.sendChatMessage("Stonk")
    }
    }
