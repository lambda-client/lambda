package com.lambda

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lambda.config.serializer.BlockPosSerializer
import com.lambda.config.serializer.BlockSerializer
import com.lambda.util.Eager
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.reflections.Reflections

object Lambda {
    const val MOD_NAME = "Lambda"
    const val MOD_ID = "lambda"
    private const val SYMBOL = "λ"
    val VERSION: String = LoaderInfo.getVersion()

    val LOG: Logger = LogManager.getLogger(SYMBOL)
    val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(BlockPos::class.java, BlockPosSerializer)
        .registerTypeAdapter(Block::class.java, BlockSerializer)
        .create()

    fun initialize() {
        LOG.info("Initializing $MOD_NAME $VERSION")

        initializeEagerObjects()
    }

    /**
     * Initializes all objects annotated with @[Eager].
     */
    private fun initializeEagerObjects() {
        val reflections = Reflections("com.lambda")

        val eagerTypes = reflections.getTypesAnnotatedWith(Eager::class.java)
        val eagerSubTypes = eagerTypes.flatMap { reflections.getSubTypesOf(it) }
        (eagerTypes + eagerSubTypes).forEach { eagerType ->
            try {
                val instanceField = eagerType.getDeclaredField("INSTANCE")
                instanceField.isAccessible = true
                val instance = instanceField.get(null)
                println("Initialized ${instance::class.simpleName}")
            } catch (e: Exception) {
                println("Failed to initialize ${eagerType.simpleName}")
            }
        }
    }
}
