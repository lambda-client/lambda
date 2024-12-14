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

package com.lambda.graphics.texture

import com.lambda.graphics.texture.TextureUtils.bindTexture
import com.lambda.graphics.texture.TextureUtils.readImage
import com.lambda.graphics.texture.TextureUtils.setupTexture
import com.lambda.module.modules.client.RenderSettings
import org.lwjgl.opengl.GL45C.*
import java.awt.image.BufferedImage

open class Texture(
    image: BufferedImage?,
    private val levels: Int = 4,
) {
    val id = glGenTextures()

    open fun bind(slot: Int = 0) {
        bindTexture(id, slot)
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, RenderSettings.lodBias)
    }

    open fun upload(image: BufferedImage, offset: Int = 0) {
        // Store level_base +1 through `level` images and generate
        // mipmaps from them
        setupLOD(levels = levels)

        val width = image.width
        val height = image.height

        // Set this mipmap to 0 to define the original texture
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, readImage(image))
        glGenerateMipmap(GL_TEXTURE_2D) // This take the derived values GL_TEXTURE_BASE_LEVEL and GL_TEXTURE_MAX_LEVEL to generate the stack

        setupTexture(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR)
    }

    private fun setupLOD(levels: Int) {
        // When you call glTextureStorage, you're specifying the total number of levels, including level 0
        // This is a 0-based index system, which means that the maximum mipmap level is n-1
        //
        // TLDR: This will not work correctly with immutable texture storage

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_LOD, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LOD, levels)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_BASE_LEVEL, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, levels)
    }

    init {
        image?.let {
            bind()
            upload(it)
        }
    }
}
