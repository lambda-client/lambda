package com.lambda

import net.minecraft.client.MinecraftClient
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Lambda {
    private const val MOD_NAME = "Lambda"
    const val MOD_ID = "lambda"
    const val SYMBOL = "λ"
    private val VERSION: String = LoaderInfo.getVersion()

    val LOG: Logger = LogManager.getLogger()

    val mc: MinecraftClient = MinecraftClient.getInstance()

    fun initialize() {
        LOG.info("Initializing $MOD_NAME $VERSION")
    }
}
