package com.lambda.task.tasks

import com.lambda.Lambda.LOG
import com.lambda.event.events.KeyPressEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.task.buildChain
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
import com.lambda.threading.taskContext
import com.lambda.util.Communication.info
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.screen.GenericContainerScreenHandler
import org.lwjgl.glfw.GLFW

object TaskTester {
    init {
        listener<KeyPressEvent> {
            if (it.key != GLFW.GLFW_KEY_Z) {
                return@listener
            }

            taskContext {
                val blockPos = mc.crosshairTarget?.blockResult?.blockPos ?: return@taskContext

                this@TaskTester.info("Testing task")
                buildChain {
                    openContainer<GenericContainerScreenHandler>(blockPos)
                        .onSuccess {
                            this@TaskTester.info("Opened container")
                        }
                }.execute()
//                HelloWorldTask()
//                    .withDelay(500L)
//                    .withTimeout(200L)
//                    .withRepeats(10)
//                    .withMaxAttempts(5)
//                    .onSuccess {
//                        LOG.info("Hello, World! Task completed")
//                    }.onRepeat { repeats ->
//                        LOG.info("Hello, World! Task $name repeated $repeats times")
//                    }.onTimeout {
//                        LOG.warn("Hello, World! Task $name timed out")
//                        HelloWorldTask().execute()
//                    }.onRetry {
//                        LOG.warn("Hello, World! Task $name retrying")
//                    }.onFailure { error ->
//                        LOG.error("Hello, World! Task failed", error)
//                    }.execute()
            }
        }
    }
}