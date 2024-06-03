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
import com.lambda.util.KeyCode
import com.mojang.authlib.GameProfile
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
    @JvmStatic
    val mc: MinecraftClient by lazy { MinecraftClient.getInstance() }

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
        .create()

    fun initialize() = Loader.initialize()
}
