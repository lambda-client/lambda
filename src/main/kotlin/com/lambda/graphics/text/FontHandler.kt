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

package com.lambda.graphics.text

import com.lambda.Lambda.LOG
import com.lambda.config.Config
import com.lambda.config.categories.FontCategory
import com.lambda.core.Loadable
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.util.FolderRegistry
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.notExists

/**
 * Central handler for font loading and caching.
 *
 * Manages SDF font atlases with automatic caching by path and size.
 * Fonts are discovered at startup but only loaded when actually used.
 */
@Suppress("unused")
object FontHandler : Loadable, Config(
	"Font",
	FontCategory
) {
	override val priority = -1

	private val loadedAtlases = ConcurrentHashMap<String, SDFFontAtlas>()

	private val baseFonts = listOf(
		FontInfo("Minecraft Default", "fonts/MinecraftDefault-Regular.ttf"),
		FontInfo("FiraSans Regular", "fonts/FiraSans-Regular.ttf"),
		FontInfo("FiraSans Bold", "fonts/FiraSans-Bold.ttf")
	)

	private val discoveredFonts = baseFonts.toMutableList()

	val defaultFontInfo = baseFonts[0]
	val defaultFont by lazy {
		getOrLoadFont(defaultFontInfo) ?: throw IllegalStateException("Failed to load default font")
	}

	private val selectedFont by setting("Selected Font", defaultFontInfo.path, description = "The file name of the font you want to use. (The font must be placed in the fonts folder in the lambda directory)")
		.onValueChangeUnsafe { _, to ->
			activeFont = getOrLoadFont(to) ?: defaultFont
		}

	var activeFont = defaultFont
		private set

	override fun load(): String {
		discoverFonts()
		activeFont = getOrLoadFont(selectedFont) ?: defaultFont

		listen<ClientEvent.Shutdown> {
			cleanup()
		}

		return "Loaded ${discoveredFonts.size} font definitions"
	}

	fun discoverFonts() {
		discoveredFonts.clear()

		val fontsFolder = FolderRegistry.fonts

		if (fontsFolder.notExists()) {
			fontsFolder.toFile().mkdirs()
		}

		val fontFiles = fontsFolder.toFile().listFiles()
			?.filter { it.isFile && it.extension.lowercase() in setOf("ttf") }
			?: emptyList()

		fontFiles.forEach { fontFile ->
			val displayName = fontFile.nameWithoutExtension
				.replace("_", " ")
				.replace("-", " ")
				.split(" ")
				.joinToString(" ") { word ->
					word.lowercase().replaceFirstChar { it.uppercase() }
				}

			if (discoveredFonts.none { it.displayName == displayName }) {
				discoveredFonts.add(
					FontInfo(
						displayName = displayName,
						path = fontFile.path,
						userFont = true,
					)
				)
			}
		}

		discoveredFonts.sortBy { it.displayName }

		LOG.info("[FontHandler] Discovered ${discoveredFonts.size} fonts")
	}

	/**
	 * Get a specific font by its path (loads if not already loaded)
	 */
	fun getOrLoadFont(path: String, size: Float = 128f): SDFFontAtlas? {
		val fontInfo = discoveredFonts.find { it.path.endsWith(path) && it.size == size }
			?: FontInfo(path.substringAfterLast("/"), path = path, size = size)
		return getOrLoadFont(fontInfo)
	}

	/**
	 * Internal method to load a font if not already cached
	 */
	private fun getOrLoadFont(fontInfo: FontInfo): SDFFontAtlas? {
		val key = fontInfo.key

		return loadedAtlases.getOrPut(key) {
			try {
				LOG.info("[FontHandler] Loading SDF atlas for: ${fontInfo.displayName}")
				SDFFontAtlas(fontInfo.path, fontInfo.userFont, fontInfo.size).apply { upload() }
			} catch (e: Exception) {
				LOG.error("[FontHandler] Failed to load font: ${fontInfo.path} - ${e.message}")
				return null
			}
		}
	}

	fun getStringWidthNormalized(text: String, normalizedSize: Float) =
		activeFont.getStringWidthNormalized(text, normalizedSize)

	fun getDescentNormalized(normalizedSize: Float) =
		activeFont.getDescentNormalized(normalizedSize)

	fun getStringDimensionsNormalized(text: String, normalizedSize: Float) =
		activeFont.getStringDimensionsNormalized(text, normalizedSize)

	fun getSizeForWidthNormalized(text: String, targetWidthNormalized: Float) =
		activeFont.getSizeForWidthNormalized(text, targetWidthNormalized)

	/**
	 * Clean up all loaded fonts and release GPU resources.
	 * Call this when shutting down or when fonts are no longer needed.
	 */
	fun cleanup() {
		loadedAtlases.values.forEach { it.close() }
		loadedAtlases.clear()
	}

	data class FontInfo(
		val displayName: String,
		val path: String = "",
		val userFont: Boolean = false,
		val size: Float = 128f
	) {
		val key = "$path@$size"
	}
}
