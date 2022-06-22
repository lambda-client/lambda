package com.lambda.client.gui.hudgui.elements.misc

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lambda.client.commons.utils.grammar
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.NetworkManager
import com.lambda.client.util.CachedValue
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.WebUtils
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object Queue2B2T : LabelHud(
    name = "2B2T Queue",
    category = Category.MISC,
    description = "Length of 2B2T Queue"
) {
    private val hasShownWarning = setting("Has Shown Warning", false, { false })
    private val show by setting("Show", Show.BOTH)

    private enum class Show {
        BOTH, PRIORITY, REGULAR
    }

    private const val apiUrl = "https://2bqueue.info/queue"

    private val gson = Gson()
    private val dataUpdateTimer = TickTimer(TimeUnit.SECONDS)

    private var queueData = QueueData(0, 0, 0, 0)
    private val lastUpdate by CachedValue(1L, TimeUnit.SECONDS) {
        val difference = System.currentTimeMillis() - queueData.lastUpdated

        val minuteAmt = (difference / 60000L % 60L).toInt()
        val secondAmt = (difference / 1000L % 60L).toInt()
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

        if (dataUpdateTimer.tick(15L)) {
            updateQueueData()
        }

        if (NetworkManager.isOffline) {
            displayText.addLine("Cannot connect to 2bqueue.info", primaryColor)
            displayText.add("Make sure your internet is working!", primaryColor)
        } else {
            if (showPriority) {
                displayText.add("Priority: ", primaryColor)
                displayText.add("${queueData.priority}", secondaryColor)
            }

            if (showRegular) {
                displayText.add("Regular: ", primaryColor)
                displayText.add("${queueData.regular}", secondaryColor)
            }

            displayText.addLine("", primaryColor)
            displayText.add("Last updated $lastUpdate ago", primaryColor)
        }
    }

    private fun sendWarning() {
        MessageSendHelper.sendWarningMessage(
            "This module uses an external API, 2bqueue.info, which is operated by tycrek#0001." +
                "If you do not trust this external API / have not verified the safety yourself, disable this HUD component."
        )
        hasShownWarning.value = true
    }

    private fun updateQueueData() {
        defaultScope.launch(Dispatchers.IO) {
            runCatching {
                val json = WebUtils.getUrlContents(apiUrl)
                gson.fromJson(json, QueueData::class.java)
            }.getOrNull()?.let {
                queueData = it
            }
        }
    }

    private val showPriority get() = show == Show.BOTH || show == Show.PRIORITY
    private val showRegular get() = show == Show.BOTH || show == Show.REGULAR

    private class QueueData(
        @SerializedName("prio")
        val priority: Int,
        val regular: Int,
        val total: Int,
        @SerializedName("timems")
        val lastUpdated: Long
    )
}
