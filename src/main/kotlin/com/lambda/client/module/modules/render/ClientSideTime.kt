package com.lambda.client.module.modules.render

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import java.text.SimpleDateFormat
import java.util.*

object ClientSideTime : Module(
    name = "ClientSideTime",
    description = "Change the client-side world time",
    category = Category.RENDER
) {
    private val mode by setting("Mode", ClientSideTimeMode.TICKS)
    private val time by setting("Time", 0, 0..24000, 600, { mode == ClientSideTimeMode.TICKS })

    enum class ClientSideTimeMode {
        REAL_WORLD_TIME, TICKS
    }

    @JvmStatic
    fun getUpdatedTime(): Long {
        if (mode == ClientSideTimeMode.REAL_WORLD_TIME)
            return dateToMinecraftTime(Calendar.getInstance())
        return time.toLong()
    }

    private fun dateToMinecraftTime(calendar: Calendar): Long {
        // We subtract 6 (add 18) to convert the real time to minecraft time :)
        calendar.add(Calendar.HOUR, 18)
        val time = calendar.time
        val minecraftHours = SimpleDateFormat("HH").format(time)
        val minecraftMinutes = (SimpleDateFormat("mm").format(time).toLong() * 100) / 60
        return "${minecraftHours}${minecraftMinutes}0".toLong()
    }
}