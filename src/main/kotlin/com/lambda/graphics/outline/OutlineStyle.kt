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

package com.lambda.graphics.outline

import com.lambda.graphics.shader.CustomShaderSet
import java.awt.Color

enum class OutlineMode {
    Line,
    Glow,
    Both;

    fun usesLine() = this == Line || this == Both

    fun usesGlow() = this == Glow || this == Both
}

enum class GlowPosition {
    Inset,
    Outset,
    InsetOutset;

    fun usesInset() = this == Inset || this == InsetOutset

    fun usesOutset() = this == Outset || this == InsetOutset
}

data class OutlineStyle(
    val color: Color,
    val lineWidth: Float = 1.0f,
    val glowMultiplier: Float = 3.5f,
    val glowPasses: Int = 2,
    val glowOffset: Float = 1.25f,
    val lineIntensity: Float = 1.0f,
    val outlineMode: OutlineMode = OutlineMode.Both,
    val glowPosition: GlowPosition = GlowPosition.Outset,
    val fillOpacity: Float = 0.3f,
    val glowResolution: Float = 0.5f,
    val glowDownsample: Float = 0.5f,
    val customShader: String = CustomShaderSet.NONE,
) {
    fun usesLine() = outlineMode.usesLine()

    fun usesGlow() = outlineMode.usesGlow()

    fun usesInset() = glowPosition.usesInset()

    fun usesOutset() = glowPosition.usesOutset()

    companion object {
        val DEFAULT = OutlineStyle(Color.WHITE)
    }
}
