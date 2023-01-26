package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.TimeUtils

internal object Time : LabelHud(
    name = "Time",
    category = Category.MISC,
    description = "System date and time",
    separator = "",
) {

    private val showDate = setting("Show Date", true)
    private val showTime = setting("Show Time", true)
    private val dateFormat = setting("Date Format", TimeUtils.DateFormat.DDMMYY, { showDate.value })
    private val timeFormat = setting("Time Format", TimeUtils.TimeFormat.HHMM, { showTime.value })
    private val timeUnit = setting("Time Unit", TimeUtils.TimeUnit.H12, { showTime.value })

    override fun SafeClientEvent.updateText() {
        if (showDate.value) {
            val date = TimeUtils.getDate(dateFormat.value)
            date.forEach { displayText.add(it.toString(), if (it.isDigit()) primaryColor else secondaryColor) }
            displayText.addLine("")
        }
        if (showTime.value) {
            val time = TimeUtils.getTime(timeFormat.value, timeUnit.value)
            time.forEach { displayText.add(it.toString(), if (it.isDigit()) primaryColor else secondaryColor) }
        }
    }

}