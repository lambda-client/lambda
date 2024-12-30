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

package com.lambda.graphics.renderer.gui.font.core

import com.lambda.util.math.Vec2d

/**
 * Represents information about a character (glyph) in a font.
 *
 * @property size The size of the character in a 2D vector.
 * @property uv1 The top-left UV coordinates of the character texture.
 * @property uv2 The bottom-right UV coordinates of the character texture.
 */
data class GlyphInfo(
    val size: Vec2d,
    val uv1: Vec2d,
    val uv2: Vec2d
) {
    /**
     * The width of the character.
     */
    val width get() = size.x

    /**
     * The height of the character.
     */
    val height get() = size.y

    /**
     * The U coordinate of the top-left corner of the character texture.
     */
    val u1 get() = uv1.x

    /**
     * The V coordinate of the top-left corner of the character texture.
     */
    val v1 get() = uv1.y

    /**
     * The U coordinate of the bottom-right corner of the character texture.
     */
    val u2 get() = uv2.x

    /**
     * The V coordinate of the bottom-right corner of the character texture.
     */
    val v2 get() = uv2.y
}