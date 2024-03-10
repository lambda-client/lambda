package com.lambda

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lambda.config.serializer.BlockPosSerializer
import com.lambda.config.serializer.BlockSerializer
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.BoringModule
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Lambda {
    private const val MOD_NAME = "Lambda"
    const val MOD_ID = "lambda"
    private const val SYMBOL = "λ"
    private val VERSION: String = LoaderInfo.getVersion()

    val LOG: Logger = LogManager.getLogger(SYMBOL)
    val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(BlockPos::class.java, BlockPosSerializer)
        .registerTypeAdapter(Block::class.java, BlockSerializer)
        .create()

    init {
        // ToDo: Why do i have to call these?
        BoringModule
        ModuleRegistry

//        listener<KeyPressEvent> {
//            if (it.key != GLFW.GLFW_KEY_Z) {
//                return@listener
//            }
//
//            taskContext {
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
//            }
//        }
    }

    fun initialize() {
        LOG.info("Initializing $MOD_NAME $VERSION")
    }
}
