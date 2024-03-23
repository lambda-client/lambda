package com.lambda

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lambda.config.serializer.BlockPosSerializer
import com.lambda.config.serializer.BlockSerializer
import com.lambda.config.serializer.ColorSerializer
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.awt.Color


object Lambda {
    const val MOD_NAME = "Lambda"
    const val MOD_ID = "lambda"
    const val SYMBOL = "λ"
    val VERSION: String = LoaderInfo.getVersion()
    val LOG: Logger = LogManager.getLogger(SYMBOL)
    val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Color::class.java, ColorSerializer)
        .registerTypeAdapter(BlockPos::class.java, BlockPosSerializer)
        .registerTypeAdapter(Block::class.java, BlockSerializer)
        .create()

    fun initialize() = Loader.initialize()
}
