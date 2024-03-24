package com.lambda

import com.lambda.Lambda.LOG
import com.lambda.command.CommandManager
import com.lambda.interaction.RotationManager
import com.lambda.module.ModuleRegistry
import kotlin.system.measureTimeMillis

object Loader {
    private val loadables = listOf(
        ModuleRegistry,
        CommandManager,
        RotationManager
    )

    fun initialize() {
        LOG.info("Initializing ${Lambda.MOD_NAME} ${Lambda.VERSION}")

        val initTime = measureTimeMillis {
            loadables.forEach { loadable ->
                var info: String
                val phaseTime = measureTimeMillis {
                    info = loadable.load()
                }

                LOG.info("$info in ${phaseTime}ms")
            }
        }

        LOG.info("${Lambda.MOD_NAME} ${Lambda.VERSION} was successfully initialized (${initTime}ms)")
    }
}
