package com.lambda.client.module.modules.render

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.mixin.world.MixinWorld
import java.text.SimpleDateFormat
import java.util.*

/**
 * @see MixinWorld.onGetWorldTime
 */
object TimeWarp : Module(
    name = "TimeWarp",
    description = "Change the client-side world time",
    category = Category.RENDER
) {
    private val mode by setting("Mode", TimeWarpMode.TICKS)
    private val time by setting("Time", 0, 0..24000, 600, { mode == TimeWarpMode.TICKS })

    enum class TimeWarpMode {
        REAL_WORLD_TIME, TICKS
    }

    @JvmStatic
    fun getUpdatedTime(): Long {
        if (mode == TimeWarpMode.REAL_WORLD_TIME)
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