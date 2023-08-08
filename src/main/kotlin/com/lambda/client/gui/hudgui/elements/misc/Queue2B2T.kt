package com.lambda.client.gui.hudgui.elements.misc

import com.google.gson.Gson
import com.lambda.client.LambdaMod
import com.lambda.client.commons.utils.ConnectionUtils
import com.lambda.client.commons.utils.grammar
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.NetworkManager
import com.lambda.client.util.CachedValue
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime

internal object Queue2B2T : LabelHud(
    name = "2B2T Queue",
    category = Category.MISC,
    description = "Length of 2B2T Queue"
) {
    private val hasShownWarning = setting("Has Shown Warning", false, { false })
    private val show by setting("Show", Show.BOTH)
    private val showUpdatedTime by setting("Show Updated Time", true)

    private enum class Show {
        BOTH, PRIORITY, REGULAR
    }

    private const val apiUrl = "https://api.2b2t.vc/queue"

    private val gson = Gson()
    private val dataUpdateTimer = TickTimer(TimeUnit.SECONDS)
    private var hasInitialized = false

    private var queueData = QueueData(0, 0, ZonedDateTime.now().toString())
    private val lastUpdate by CachedValue(1L, TimeUnit.SECONDS) {
        val dateRaw = queueData.time
        val parsedDate = ZonedDateTime.parse(dateRaw)
        val difference = Instant.now().epochSecond - parsedDate.toEpochSecond()

        val minuteAmt = (difference / 60L % 60L).toInt()
        val secondAmt = (difference % 60L).toInt()
        val minutes = grammar(minuteAmt, "minute", "minutes")
        val seconds = grammar(secondAmt, "second", "seconds")

        if (minuteAmt == 0) {
            seconds
        } else {
            "$minutes, $seconds"
        }
    }

    override fun SafeClientEvent.updateText() {
        if (!hasShownWarning.value) {
            sendWarning()
        }

        if (dataUpdateTimer.tick(300L) // API caches queue data for 5 minutes
            || !hasInitialized) {
            hasInitialized = true
            updateQueueData()
        }

        if (NetworkManager.isOffline) {
            displayText.addLine("Cannot connect to api.2b2t.vc", primaryColor)
            displayText.add("Make sure your internet is working!", primaryColor)
            return
        }

        if (showPriority) {
            displayText.add("Priority: ", primaryColor)
            displayText.add("${queueData.prio}", secondaryColor)
        }

        if (showRegular) {
            displayText.add("Regular: ", primaryColor)
            displayText.add("${queueData.regular}", secondaryColor)
        }
        if (showUpdatedTime) {
            displayText.addLine("", primaryColor)
            displayText.add("Last updated $lastUpdate ago", primaryColor)
        }
    }

    private fun sendWarning() {
        MessageSendHelper.sendWarningMessage(
            "This module uses an external API, api.2b2t.vc, which is operated by rfresh#2222." +
                " If you do not trust this external API / have not verified the safety yourself, disable this HUD component."
        )
        hasShownWarning.value = true
    }

    private fun updateQueueData() {
        defaultScope.launch(Dispatchers.IO) {
            runCatching {
                ConnectionUtils.requestRawJsonFrom(apiUrl) {
                    LambdaMod.LOG.error("Failed querying queue data", it)
                }?.let {
                    gson.fromJson(it, QueueData::class.java)?.let { data ->
                        queueData = data
                        return@runCatching
                    }

                    LambdaMod.LOG.error("No queue data received. Is 2b2t down?")
                }
            }
        }
    }

    private val showPriority get() = show == Show.BOTH || show == Show.PRIORITY
    private val showRegular get() = show == Show.BOTH || show == Show.REGULAR

    private class QueueData(
        val prio: Int,
        val regular: Int,
        val time: String
    )
}
