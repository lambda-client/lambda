package com.lambda.graphics.renderer.gui.font.glyph

import com.lambda.util.math.Vec2d

data class CharInfo(
    val size: Vec2d,
    val uv1: Vec2d,
    val uv2: Vec2d
) {
    val width get() = size.x
    val height get() = size.y

    val u1 get() = uv1.x
    val v1 get() = uv1.y
    val u2 get() = uv2.x
    val v2 get() = uv2.y
}
