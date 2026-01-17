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

import java.util.concurrent.ConcurrentHashMap

/**
 * Central handler for font loading and caching.
 * 
 * Manages SDF font atlases with automatic caching by path and size.
 * Use this instead of creating SDFFontAtlas instances directly.
 *
 * Usage:
 * ```kotlin
 * val font = FontHandler.loadFont("fonts/MyFont.ttf", 128f)
 * val defaultFont = FontHandler.getDefaultFont()
 * ```
 */
object FontHandler {
	private val fonts = ConcurrentHashMap<String, SDFFontAtlas>()
	private var defaultFont: SDFFontAtlas? = null

	/**
	 * Load an SDF font from resources.
	 *
	 * @param path Resource path to TTF/OTF file (e.g., "fonts/FiraSans-Regular.ttf")
	 * @param size Base font size for SDF generation (larger = higher quality, default 128)
	 * @return The loaded SDFFontAtlas, or null if loading failed
	 */
	fun loadFont(path: String, size: Float = 128f): SDFFontAtlas? {
		val key = "$path@$size"
		return fonts.getOrPut(key) {
			try {
				SDFFontAtlas(path, size)
			} catch (e: Exception) {
				println("[FontHandler] Failed to load font: $path - ${e.message}")
				return null
			}
		}
	}

	/**
	 * Get or create the default font.
	 * Uses MinecraftDefault-Regular.ttf at 128px base size.
	 */
	fun getDefaultFont(size: Float = 128f): SDFFontAtlas {
		defaultFont?.let { return it }

		val key = "fonts/FiraSans-Regular.ttf@$size"
		val font = fonts[key] ?: run {
			val newFont = SDFFontAtlas("fonts/FiraSans-Regular.ttf", size)
			fonts[key] = newFont
			newFont
		}
		defaultFont = font
		return font
	}

	fun isFontLoaded(path: String, size: Float = 128f) = fonts.containsKey("path@$size")

	fun getLoadedFonts(): Set<String> = fonts.keys.toSet()

	/**
	 * Clean up all loaded fonts and release GPU resources.
	 * Call this when shutting down or when fonts are no longer needed.
	 */
	fun cleanup() {
		fonts.values.forEach { it.close() }
		fonts.clear()
		defaultFont = null
	}
}
