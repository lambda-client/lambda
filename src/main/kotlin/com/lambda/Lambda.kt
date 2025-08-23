/*
 * Copyright 2025 Lambda
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
import com.lambda.config.serializer.BlockPosSerializer
import com.lambda.config.serializer.BlockSerializer
import com.lambda.config.serializer.ColorSerializer
import com.lambda.config.serializer.GameProfileSerializer
import com.lambda.config.serializer.ItemStackSerializer
import com.lambda.config.serializer.KeyCodeSerializer
import com.lambda.config.serializer.OptionalSerializer
import com.lambda.core.Loader
import com.lambda.threading.recordRenderCall
import com.lambda.util.KeyCode
import com.mojang.authlib.GameProfile
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.awt.Color
import java.util.*


object Lambda : ClientModInitializer {
    const val MOD_NAME = "Lambda"
    const val MOD_ID = "lambda"
    const val SYMBOL = "λ"
    const val APP_ID = "1221289599427416127"
    val VERSION: String = FabricLoader.getInstance()
        .getModContainer("lambda").orElseThrow()
        .metadata.version.friendlyString

    val LOG: Logger = LogManager.getLogger(SYMBOL)

    @JvmStatic
    val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

    val isDebug = System.getProperty("lambda.dev") != null

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(KeyCode::class.java, KeyCodeSerializer)
        .registerTypeAdapter(Color::class.java, ColorSerializer)
        .registerTypeAdapter(BlockPos::class.java, BlockPosSerializer)
        .registerTypeAdapter(Block::class.java, BlockSerializer)
        .registerTypeAdapter(GameProfile::class.java, GameProfileSerializer)
        .registerTypeAdapter(Optional::class.java, OptionalSerializer)
        .registerTypeAdapter(ItemStack::class.java, ItemStackSerializer)
        .registerTypeAdapter(Text::class.java, Text.Serializer(DynamicRegistryManager.EMPTY))
        .create()

    override fun onInitializeClient() =
        recordRenderCall { LOG.info("$MOD_NAME $VERSION initialized in ${Loader.initialize()} ms\n") }
}
