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
import com.lambda.config.serializer.BindCodec
import com.lambda.config.serializer.BlockCodec
import com.lambda.config.serializer.BlockPosCodec
import com.lambda.config.serializer.ColorSerializer
import com.lambda.config.serializer.GameProfileCodec
import com.lambda.config.serializer.ItemCodec
import com.lambda.config.serializer.ItemStackCodec
import com.lambda.config.serializer.KeyCodeCodec
import com.lambda.config.serializer.OptionalCodec
import com.lambda.config.serializer.TextCodec
import com.lambda.config.serializer.UUIDCodec
import com.lambda.config.settings.complex.Bind
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
    const val ModName = "Lambda"
    const val ModId = "lambda"
    const val Symbol = "λ"
    const val AppId = "1221289599427416127"
    const val RepoUrl = "https://github.com/lambda-client/lambda"
    val Version: String = FabricLoader.getInstance()
        .getModContainer("lambda").orElseThrow()
        .metadata.version.friendlyString

    val Log: Logger = LogManager.getLogger(Symbol)

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
            Log.info("$ModName $Version initialized in ${Loader.initialize()} ms\n")
            if (ClickGuiLayout.setLambdaWindowIcon) setLambdaWindowIcon()
            true
        }
    }
}
