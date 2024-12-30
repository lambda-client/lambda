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

package com.lambda.graphics.renderer.gui.font.sdf

import com.lambda.graphics.buffer.frame.CachedFrame
import com.lambda.graphics.buffer.frame.FrameBuffer
import com.lambda.graphics.shader.Shader
import com.lambda.graphics.texture.Texture
import com.lambda.util.math.Vec2d

/**
 * A class that represents a distance field texture, which is created by rendering a given texture
 * onto a framebuffer with specific shader operations.
 *
 * The texture is used to create a signed distance field (SDF) for rendering operations.
 *
 * @param texture The texture to be used for creating the distance field.
 */
class DistanceFieldTexture(texture: Texture) {
    val frame = CachedFrame(texture.width, texture.height).write {
        FrameBuffer.pipeline.use {
            val (pos1, pos2) = Vec2d.ZERO to Vec2d(texture.width, texture.height)

            grow(4)
            putQuad(
                vec2(pos1.x, pos1.y).vec2(0.0, 1.0).end(),
                vec2(pos1.x, pos2.y).vec2(0.0, 0.0).end(),
                vec2(pos2.x, pos2.y).vec2(1.0, 0.0).end(),
                vec2(pos2.x, pos1.y).vec2(1.0, 1.0).end()
            )

            shader.use()
            shader["u_TexelSize"] = Vec2d.ONE / pos2
            texture.bind()

            immediateDraw()
        }
    }

    companion object {
        private val shader = Shader("signed_distance_field", "renderer/pos_tex")
    }
}