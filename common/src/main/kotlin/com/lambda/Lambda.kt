/*
 * Copyright 2024 Lambda
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
import com.lambda.config.serializer.*
import com.lambda.config.serializer.gui.CustomModuleWindowSerializer
import com.lambda.config.serializer.gui.ModuleTagSerializer
import com.lambda.config.serializer.gui.TagWindowSerializer
import com.lambda.core.Loader
import com.lambda.gui.impl.clickgui.windows.tag.CustomModuleWindow
import com.lambda.gui.impl.clickgui.windows.tag.TagWindow
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.util.KeyCode
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.awt.Color
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*
import java.util.concurrent.CountDownLatch


object Lambda {
    const val MOD_NAME = "Lambda"
    const val MOD_ID = "lambda"
    const val SYMBOL = "λ"
    const val APP_ID = "1221289599427416127"
    val VERSION: String = LoaderInfo.getVersion()
    val LOG: Logger = LogManager.getLogger(SYMBOL)

    @JvmStatic
    val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

    val isDebug = System.getProperty("lambda.dev") != null

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ModuleTag::class.java, ModuleTagSerializer)
        .registerTypeAdapter(CustomModuleWindow::class.java, CustomModuleWindowSerializer)
        .registerTypeAdapter(TagWindow::class.java, TagWindowSerializer)
        .registerTypeAdapter(KeyCode::class.java, KeyCodeSerializer)
        .registerTypeAdapter(Color::class.java, ColorSerializer)
        .registerTypeAdapter(BlockPos::class.java, BlockPosSerializer)
        .registerTypeAdapter(Block::class.java, BlockSerializer)
        .registerTypeAdapter(GameProfile::class.java, GameProfileSerializer)
        .registerTypeAdapter(Optional::class.java, OptionalSerializer)
        .registerTypeAdapter(ItemStack::class.java, ItemStackSerializer)
        .registerTypeAdapter(Text::class.java, Text.Serializer())
        .create()

    fun initialize(block: (Long) -> Unit) {
        runConcurrent {
            // Create an HttpClient
            val client = HttpClient.newHttpClient()

            // Define the URI for the SSE stream
            val uri = URI.create("http://localhost:8080/api/v1/party/listen")

            // Create an HttpRequest for the SSE stream
            val request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Accept", "text/event-stream")
                .build()

            // Create a CountDownLatch to wait for the events
            val latch = CountDownLatch(1)

            // Send the request and handle the response asynchronously
            client.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept { response ->
                    println("Connected to SSE stream")
                    response.body().forEach { line ->
                        if (line.startsWith("data:")) {
                            val data = line.substring(5).trim()
                            println("Received event data: $data")
                        }
                    }
                    latch.countDown()
                }

            // Wait until the response is received and handled
            latch.await()
        }

        recordRenderCall {
            block(Loader.initialize())
        }
    }
}
