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
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL45C.*
import java.awt.image.BufferedImage

/**
 * Represents a texture that can be uploaded and bound to the graphics pipeline.
 * Supports mipmap generation and LOD (Level of Detail) configuration
 *
 * @param image             Optional initial image to upload to the texture
 * @param levels            Number of mipmap levels to generate for the texture
 * @param forceConsistency  Flag to enforce consistency when updating the texture. If true, attempts to update
 *                          the texture after initialization will throw an exception
 */
open class Texture(
    image: BufferedImage?,
    private val levels: Int = 4,
    private val forceConsistency: Boolean = false,
) {
    /**
     * Indicates whether there is an initial texture or not
     */
    var initialized: Boolean = false; private set
    val id = glGenTextures()

    /**
     * Binds the texture to a specific slot in the graphics pipeline.
     */
    open fun bind(slot: Int = 0) {
        bindTexture(id, slot)
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, RenderSettings.lodBias)
    }

    /**
     * Uploads an image to the texture and generates mipmaps for the texture if applicable.
     *
     * @param image The image to upload to the texture
     * @param offset The mipmap level to upload the image to
     */
    open fun upload(image: BufferedImage, offset: Int = 0) {
        // Store level_base +1 through `level` images and generate
        // mipmaps from them
        setupLOD(levels = levels)

        val width = image.width
        val height = image.height

        // Set this mipmap to `offset` to define the original texture
        glTexImage2D(GL_TEXTURE_2D, offset, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, readImage(image))
        glGenerateMipmap(GL_TEXTURE_2D) // This take the derived values GL_TEXTURE_BASE_LEVEL and GL_TEXTURE_MAX_LEVEL to generate the stack

        setupTexture(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR)
    }

    open fun update(image: BufferedImage, offset: Int = 0) {
        if (forceConsistency && initialized)
            throw IllegalStateException("Client tried to update a texture, but the enforce consistency flag was present")

        val width = image.width
        val height = image.height

        // Can we rebuild LOD ?
        glTexSubImage2D(GL_TEXTURE_2D, offset, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, readImage(image))
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

            initialized = true
        }
    }
}
