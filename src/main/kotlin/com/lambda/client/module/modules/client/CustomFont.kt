package com.lambda.client.module.modules.client

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.AsyncCachedValue
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.graphics.font.LambdaFontRenderer
import com.lambda.client.util.threads.onMainThread
import kotlinx.coroutines.runBlocking
import java.awt.GraphicsEnvironment

object CustomFont : Module(
    name = "CustomFont",
    description = "Use different GUI fonts",
    category = Category.CLIENT,
    showOnArray = false,
    enabledByDefault = true
) {
    private const val DEFAULT_FONT_NAME = "Fira Sans"

    val fontName = setting("Font Name", DEFAULT_FONT_NAME, consumer = { prev, value ->
        getMatchingFontName(value) ?: getMatchingFontName(prev) ?: DEFAULT_FONT_NAME
    })
    private val sizeSetting by setting("Size", 1.0f, 0.5f..2.0f, 0.05f)
    private val shadowSetting by setting("Shadow", false)
    private val gapSetting by setting("Gap", 0.0f, -10f..10f, 0.5f)
    private val lineSpaceSetting by setting("Line Space", 0.8f, -10f..10f, 0.05f)
    private val baselineOffsetSetting by setting("Baseline Offset", 2.4f, -10.0f..10.0f, 0.05f)
    private val lodBiasSetting by setting("Lod Bias", 0.0f, -10.0f..10.0f, 0.05f)

    val isDefaultFont get() = fontName.value.equals(DEFAULT_FONT_NAME, true)
    val size get() = sizeSetting * 0.15f
    val gap get() = gapSetting * 0.5f - 0.8f
    val lineSpace get() = size * (lineSpaceSetting * 0.05f + 0.77f)
    val lodBias get() = lodBiasSetting * 0.25f - 0.5f
    val baselineOffset get() = baselineOffsetSetting * 2.0f - 4.5f
    val shadow get() = shadowSetting

    /** Available fonts on the system */
    val availableFonts: Map<String, String> by AsyncCachedValue(5L, TimeUnit.SECONDS) {
        HashMap<String, String>().apply {
            val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()

            environment.availableFontFamilyNames.forEach {
                this[it.lowercase()] = it
            }

            environment.allFonts.forEach {
                this[it.name.lowercase()] = it.family
            }
        }
    }

    private fun getMatchingFontName(name: String): String? {
        return if (name.equals(DEFAULT_FONT_NAME, true)) DEFAULT_FONT_NAME
        else availableFonts[name.lowercase()]
    }

    init {
        fontName.listeners.add {
            runBlocking {
                onMainThread { LambdaFontRenderer.reloadFonts() }
            }
        }
    }
}