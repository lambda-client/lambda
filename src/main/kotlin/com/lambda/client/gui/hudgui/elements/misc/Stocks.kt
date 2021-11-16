package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.lambda.client.util.WebUtils
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import com.lambda.client.util.text.MessageSendHelper.sendServerMessage
import com.lambda.client.util.threads.defaultScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object Stocks : LabelHud(
    name = "Stocks",
    category = Category.MISC,
    description = "Info About a stock"
) {

    private val symbol by setting("Symbol", "TSLA")
    private val tickdelay by setting("Delay", 30, 20..120, 1)
    private val ticktimer = TickTimer(TimeUnit.SECONDS)
    private val url = "https://finnhub.io/api/v1/quote?symbol=$symbol&token=c5resoqad3ifnpn51ou0"
    private var stockData = StockData(0)
    override fun SafeClientEvent.updateText() {
        if (ticktimer.tick(tickdelay)) {
            updateStockData()
        }
        displayText.add("Price of $symbol is ${stockData.c}")
    }

    private fun updateStockData() {
        defaultScope.launch(Dispatchers.Default) {
            runCatching {
                val gson = GsonBuilder().setPrettyPrinting().create()
                val json = WebUtils.getUrlContents(url)
                sendChatMessage(json)
                val stockData = gson.fromJson(json, StockData::class.java)
                sendChatMessage("made it into json")
                val outputJson: String = gson.toJson(stockData)
                sendChatMessage("outputjson $outputJson, stockdata: $stockData")
            }
        }
    }
    private class StockData (
        val c: Int
        )
    }
