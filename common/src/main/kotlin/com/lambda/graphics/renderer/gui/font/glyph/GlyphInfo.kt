package com.lambda.graphics.renderer.gui.font.glyph

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
