package com.lambda.client.manager

import com.lambda.client.event.KamiEventBus
import com.lambda.client.util.StopTimer
import com.lambda.commons.utils.ClassUtils
import com.lambda.commons.utils.ClassUtils.instance
import kotlinx.coroutines.Deferred

internal object ManagerLoader : com.lambda.client.AsyncLoader<List<Class<out Manager>>> {
    override var deferred: Deferred<List<Class<out Manager>>>? = null

    override fun preLoad0(): List<Class<out Manager>> {
        val stopTimer = StopTimer()

        val list = ClassUtils.findClasses<Manager>("com.lambda.client.manager.managers")

        val time = stopTimer.stop()

        com.lambda.client.LambdaMod.LOG.info("${list.size} managers found, took ${time}ms")
        return list
    }

    override fun load0(input: List<Class<out Manager>>) {
        val stopTimer = StopTimer()

        for (clazz in input) {
            KamiEventBus.subscribe(clazz.instance)
        }

        val time = stopTimer.stop()
        com.lambda.client.LambdaMod.LOG.info("${input.size} managers loaded, took ${time}ms")
    }
}