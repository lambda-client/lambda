package com.lambda.client.manager.managers

import com.lambda.client.commons.extension.synchronized
import com.lambda.client.event.events.RunGameLoopEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.Manager
import com.lambda.client.mixin.extension.tickLength
import com.lambda.client.mixin.extension.timer
import com.lambda.client.module.AbstractModule
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import java.util.*

object TimerManager : Manager {
    private val timer = TickTimer(TimeUnit.TICKS)
    private val modifications = TreeMap<AbstractModule, Pair<Float, Long>>(compareByDescending { it.modulePriority }).synchronized() // <Module, <Tick length, Added Time>>

    private var modified = false

    var tickLength = 50.0f; private set

    init {
        listener<RunGameLoopEvent.Start> {
            if (timer.tick(6L)) {
                val removeTime = System.currentTimeMillis() - 600L
                modifications.values.removeIf { it.second < removeTime }
            }

            if (mc.player != null && modifications.isNotEmpty()) {
                modifications.firstEntry()?.let {
                    mc.timer.tickLength = it.value.first
                }
                modified = true
            } else if (modified) {
                reset()
            }

            tickLength = mc.timer.tickLength
        }
    }

    fun AbstractModule.resetTimer() {
        modifications.remove(this)
    }

    fun AbstractModule.modifyTimer(tickLength: Float) {
        if (mc.player != null) {
            modifications[this] = tickLength to System.currentTimeMillis()
        }
    }

    private fun reset() {
        mc.timer.tickLength = 50.0f
        modified = false
    }
}