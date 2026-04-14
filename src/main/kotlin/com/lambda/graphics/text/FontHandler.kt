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
import com.lambda.config.Configurable
import com.lambda.config.configurations.FontConfig
import com.lambda.core.Loadable
import com.lambda.util.FolderRegister
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.notExists

/**
 * Central handler for font loading and caching.
 *
 * Manages SDF font atlases with automatic caching by path and size.
 * Fonts are discovered at startup but only loaded when actually used.
 */
object FontHandler : Loadable, Configurable(FontConfig) {
	override val name = "Font"

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

	private val selectedFont by setting("Selected Font", defaultFontInfo.path, description = "The local path (from the lambda folder) to the font file, including the .ttf file extension")
		.onValueChangeUnsafe { _, to ->
			activeFont = getFont(to) ?: defaultFont
		}

	var activeFont = defaultFont
		private set

	override fun load(): String {
		discoverFonts()
		activeFont = getFont(selectedFont) ?: defaultFont
		return "Loaded ${discoveredFonts.size} font definitions"
	}

	fun discoverFonts() {
		discoveredFonts.clear()

		val fontsFolder = FolderRegister.fonts

		if (fontsFolder.notExists()) {
			fontsFolder.toFile().mkdirs()
		}

		val fontFiles = fontsFolder.toFile().listFiles()
			?.filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
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
	 * Get all discoverable fonts (for settings UI)
	 */
	fun getAvailableFonts(): List<FontInfo> = discoveredFonts.toList()

	/**
	 * Get a specific font by its FontInfo (loads if not already loaded)
	 */
	fun getFont(fontInfo: FontInfo): SDFFontAtlas? = getOrLoadFont(fontInfo)

	/**
	 * Get a specific font by its path (loads if not already loaded)
	 */
	fun getFont(path: String, size: Float = 128f): SDFFontAtlas? {
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

	/**
	 * Set the active font to be used for rendering
	 */
	fun setActiveFont(fontInfo: FontInfo): Boolean {
		val font = getFont(fontInfo)
		if (font != null) {
			activeFont = font
			return true
		}
		return false
	}

	fun isFontLoaded(fontInfo: FontInfo) = loadedAtlases.containsKey(fontInfo.key)

	fun isFontLoaded(path: String, size: Float = 128f) = loadedAtlases.containsKey("$path@$size")

	fun getLoadedFontKeys(): Set<String> = loadedAtlases.keys.toSet()

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
