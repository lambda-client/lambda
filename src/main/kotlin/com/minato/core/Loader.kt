
package com.minato.core

import com.minato.Minato
import com.minato.Minato.LOG
import com.minato.util.CommunicationUtils.ascii
import com.minato.util.ReflectionUtils.getInstances
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

object Loader {
    private val started = System.currentTimeMillis()

    val runtime: String
        get() = "${(System.currentTimeMillis() - started).milliseconds}"

    private val loadables = getInstances<Loadable>()

    fun initialize(): Long {
        ascii.split("\n").forEach { LOG.info(it) }
        LOG.info("Initializing ${Minato.MOD_NAME} ${Minato.VERSION} (${loadables.size} loaders)...")

        val initTime = measureTimeMillis {
            loadables.sortedByDescending { it.priority }.forEach {
                var response: String
                val time = measureTimeMillis { response = it.load() }
                if (response.isNotBlank()) LOG.info("$response ($time ms)")
            }
        }

        return initTime
    }
}
