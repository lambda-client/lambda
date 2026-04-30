/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lambda.config.Codec
import com.lambda.core.Loader
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenOnceUnsafe
import com.lambda.gui.components.ClickGuiLayout
import com.lambda.util.ReflectionUtils.getInstances
import com.lambda.util.WindowUtils.setLambdaWindowIcon
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Lambda : ClientModInitializer {
    const val MOD_NAME = "Lambda"
    const val MOD_ID = "lambda"
    const val SYMBOL = "λ"
    const val APP_ID = "1221289599427416127"
    const val REPO_URL = "https://github.com/lambda-client/lambda"
    val VERSION: String = FabricLoader.getInstance()
        .getModContainer("lambda").orElseThrow()
        .metadata.version.friendlyString

    val LOG: Logger = LogManager.getLogger(SYMBOL)

    @JvmStatic
    val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

    val isDebug = System.getProperty("lambda.dev") != null

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .apply {
            getInstances<Codec<*>>()
                .forEach { codec ->
                    registerTypeAdapter(codec.type, codec)
                }
        }
        .create()

    override fun onInitializeClient() {} // nop

    init {
        // We want the opengl context to be created
        listenOnceUnsafe<ClientEvent.Startup>({ Int.MAX_VALUE }) {
            LOG.info("$MOD_NAME $VERSION initialized in ${Loader.initialize()} ms\n")
            if (ClickGuiLayout.setLambdaWindowIcon) setLambdaWindowIcon()
            true
        }
    }
}
