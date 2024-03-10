package com.lambda.common

import com.lambda.common.event.events.KeyPressEvent
import com.lambda.common.event.listener.SafeListener.Companion.listener
import com.lambda.common.task.tasks.HelloWorldTask
import com.lambda.common.threading.taskContext
import net.minecraft.client.MinecraftClient
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.lwjgl.glfw.GLFW

object Lambda {
    private const val MOD_NAME = "Lambda"
    const val MOD_ID = "lambda"
    private const val SYMBOL = "λ"
    private val VERSION: String = LoaderInfo.getVersion()

    val LOG: Logger = LogManager.getLogger(SYMBOL)
    val mc: MinecraftClient = MinecraftClient.getInstance()

    init {
        listener<KeyPressEvent> {
            if (it.key != GLFW.GLFW_KEY_Z) {
                return@listener
            }

            taskContext {
                HelloWorldTask()
                    .withDelay(500L)
                    .withTimeout(200L)
                    .withRepeats(10)
                    .withMaxAttempts(5)
                    .onSuccess {
                        LOG.info("Hello, World! Task completed")
                    }.onRepeat { repeats ->
                        LOG.info("Hello, World! Task $name repeated $repeats times")
                    }.onTimeout {
                        LOG.warn("Hello, World! Task $name timed out")
                        HelloWorldTask().execute()
                    }.onRetry {
                        LOG.warn("Hello, World! Task $name retrying")
                    }.onFailure { error ->
                        LOG.error("Hello, World! Task failed", error)
                    }.execute()
            }
        }
    }

    fun initialize() {
        LOG.info("Initializing $MOD_NAME $VERSION")
    }
}
