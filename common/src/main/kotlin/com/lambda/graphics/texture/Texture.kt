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
import net.minecraft.client.texture.NativeImage
import org.lwjgl.opengl.GL45C.*
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.*
import java.nio.ByteBuffer
import kotlin.IllegalStateException

/**
 * Represents a texture that can be uploaded and bound to the graphics pipeline
 * Supports mipmap generation and LOD (Level of Detail) configuration
 */
open class Texture {
    val format: Int
    private val levels: Int
    private val nativeFormat: NativeImage.Format // For mojang native images

    /**
     * @param image             Optional initial image to upload to the texture
     * @param format            The format of the image passed in, if the [image] is null, then you must pass the appropriate format
     * @param levels            Number of mipmap levels to generate for the texture
     */
    constructor(image: BufferedImage?, format: Int = GL_RGBA, levels: Int = 4) {
        this.format = image?.type?.let { bufferedMapping[it] } ?: format
        this.levels = levels
        this.nativeFormat = nativeMapping.getOrDefault(format, NativeImage.Format.RGBA)

        image?.let { bindTexture(id); upload(it) }
    }

    /**
     * @param buffer            The image buffer
     * @param width             The width of the image
     * @param height            The height of the image
     * @param format            The format of the image passed in, must be specified
     * @param levels            Number of mipmap levels to generate for the texture
     */
    constructor(buffer: ByteBuffer, width: Int, height: Int, format: Int, levels: Int = 4) {
        this.format = format
        this.levels = levels
        this.nativeFormat = nativeMapping.getOrDefault(format, NativeImage.Format.RGBA)

        bindTexture(id)
        upload(buffer, width, height)
    }

    /**
     * Indicates whether there is an initial texture or not
     */
    var initialized: Boolean = false; protected set
    val id = glGenTextures()

    var width = -1; protected set
    var height = -1; protected set

    /**
     * Binds the texture to a specific slot in the graphics pipeline.
     */
    open fun bind(slot: Int = 0) {
        bindTexture(id, slot)
    }

    /**
     * Unbinds the currently bound texture
     */
    open fun unbind(slot: Int = 0) {
        bindTexture(0, slot)
    }

    /**
     * Uploads an image to the texture and generates mipmaps for the texture if applicable
     * This function does not bind the texture
     *
     * @param image     The image to upload to the texture
     * @param offset    The mipmap level to upload the image to
     */
    fun upload(image: BufferedImage, offset: Int = 0) {
        // Store level_base +1 through `level` images and generate
        // mipmaps from them
        setupLOD(levels)

        width = image.width
        height = image.height
        initialized = true

        // Set this mipmap to `offset` to define the original texture
        setupTexture(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR)
        glTexImage2D(GL_TEXTURE_2D, offset, GL_RGBA, width, height, 0, format, GL_UNSIGNED_BYTE, readImage(image, nativeFormat))
        if (levels > 0) glGenerateMipmap(GL_TEXTURE_2D) // This take the derived values GL_TEXTURE_BASE_LEVEL and GL_TEXTURE_MAX_LEVEL to generate the stack
    }

    /**
     * Uploads an image to the texture and generates mipmaps for the texture if applicable
     * This function does not bind the texture
     *
     * @param buffer    The image buffer to upload to the texture
     * @param width     The width of the texture
     * @param height    The height of the texture
     * @param offset    The mipmap level to upload the image to
     */
    fun upload(buffer: ByteBuffer, width: Int, height: Int, offset: Int = 0) {
        // Store level_base +1 through `level` images and generate
        // mipmaps from them
        setupLOD(levels)

        this.width = width
        this.height = height
        initialized = true

        // Set this mipmap to `offset` to define the original texture
        setupTexture(GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR)
        glTexImage2D(GL_TEXTURE_2D, offset, GL_RGBA, width, height, 0, format, GL_UNSIGNED_BYTE, buffer)
        if (levels > 0) glGenerateMipmap(GL_TEXTURE_2D) // This take the derived values GL_TEXTURE_BASE_LEVEL and GL_TEXTURE_MAX_LEVEL to generate the stack
    }

    /**
     * Updates the data of a texture
     * This function does not bind the texture
     *
     * @param image     The image to upload to the texture
     * @param offset    The mipmap level to upload the image to
     *
     * @throws IllegalStateException If the texture has the consistency flag and is already initialized
     */
    fun update(image: BufferedImage, offset: Int = 0) {
        if (!initialized) return upload(image, offset)

        checkDimensions(width, height)
        glTexSubImage2D(GL_TEXTURE_2D, offset, 0, 0, width, height, format, GL_UNSIGNED_BYTE, readImage(image, nativeFormat))
    }

    /**
     * Updates the data of a texture
     * This function does not bind the texture
     *
     * @param buffer    The image buffer to upload to the texture
     * @param width     The width of the texture
     * @param height    The height of the texture
     * @param offset    The mipmap level to upload the image to
     *
     * @throws IllegalStateException If the texture has the consistency flag and is already initialized
     */
    fun update(buffer: ByteBuffer, width: Int, height: Int, offset: Int = 0) {
        if (!initialized) return upload(buffer, width, height, offset)

        checkDimensions(width, height)
        glTexSubImage2D(GL_TEXTURE_2D, offset, 0, 0, width, height, format, GL_UNSIGNED_BYTE, buffer)
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

    private fun checkDimensions(width: Int, height: Int) =
        check(width + height <= this.width + this.height && initialized) {
            "Client tried to update a texture with more data than allowed\n" +
                    "Expected ${this.width + this.height} bytes but got ${width + height}"
        }

    companion object {
        private val nativeMapping = mapOf(
            GL_RED      to NativeImage.Format.LUMINANCE,
            GL_GREEN    to NativeImage.Format.LUMINANCE,
            GL_BLUE     to NativeImage.Format.LUMINANCE,
            GL_RG       to NativeImage.Format.LUMINANCE_ALPHA,
            GL_RGB      to NativeImage.Format.RGB,
            GL_RGBA     to NativeImage.Format.RGBA,
        )

        private val bufferedMapping = mapOf(
            TYPE_BYTE_BINARY    to GL_RED,
            TYPE_BYTE_GRAY      to GL_RG,
            TYPE_INT_RGB        to GL_RGB,
            TYPE_INT_ARGB       to GL_RGBA,
            TYPE_4BYTE_ABGR     to GL_BGRA,
        )
    }
}
