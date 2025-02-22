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
     * Binds this texture to the specified slot in the graphics pipeline.
     *
     * @param slot The slot to bind the texture to. Defaults to 0.
     */
    open fun bind(slot: Int = 0) {
        bindTexture(id, slot)
    }

    /**
     * Unbinds any texture from the specified slot.
     *
     * This method removes the current texture binding on the given slot by binding a texture ID of 0.
     * If no slot is explicitly provided, the operation defaults to slot 0.
     *
     * @param slot The slot index from which to unbind the texture.
     */
    open fun unbind(slot: Int = 0) {
        bindTexture(0, slot)
    }

    /**
     * Uploads the given image data to the texture at the specified mipmap level.
     *
     * This function updates the texture's dimensions to match the image, marks it as initialized, and
     * configures level-of-detail parameters before setting texture filtering options. If mipmapping is enabled,
     * mipmaps are automatically generated. Note that the texture must be bound before calling this function.
     *
     * @param image  the BufferedImage containing the texture data.
     * @param offset the mipmap level where the image data is applied (typically 0 for the base level).
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
     * Uploads image data from a ByteBuffer to the texture.
     *
     * This method updates the texture's dimensions, configures Level of Detail (LOD) parameters,
     * and marks the texture as initialized. The image data is uploaded at the specified mipmap level,
     * and if mipmap generation is enabled (i.e. when more than zero levels are configured), it triggers
     * the creation of the full mipmap chain. Note that the texture is assumed to be bound before calling this method.
     *
     * @param buffer The image data to be uploaded.
     * @param width The width of the texture image.
     * @param height The height of the texture image.
     * @param offset The mipmap level where the image data will be applied, typically 0 for the base level.
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
     * Updates the content of an existing texture with new image data.
     *
     * If the texture is not yet initialized, this method delegates to [upload] to set up
     * the texture data. When the texture is already initialized, it updates the texture
     * at the specified mipmap level. Note that this method does not bind the texture;
     * it assumes the texture is already bound.
     *
     * @param image The BufferedImage containing the new texture data.
     * @param offset The mipmap level to update (default is 0).
     *
     * @throws IllegalStateException if the provided image data's dimensions are incompatible
     *                               with the texture's existing dimensions.
     */
    fun update(image: BufferedImage, offset: Int = 0) {
        if (!initialized) return upload(image, offset)

        checkDimensions(width, height)
        glTexSubImage2D(GL_TEXTURE_2D, offset, 0, 0, width, height, format, GL_UNSIGNED_BYTE, readImage(image, nativeFormat))
    }

    /**
     * Updates the texture content with new image data from the provided ByteBuffer.
     *
     * If the texture is not yet initialized, this function delegates to the texture upload routine.
     * For an already initialized texture, it checks that the new dimensions do not exceed the original ones
     * and updates the specified mipmap level without binding the texture.
     *
     * @param buffer The ByteBuffer containing the new texture data.
     * @param width The width of the new image data.
     * @param height The height of the new image data.
     * @param offset The mipmap level at which to update the texture.
     *
     * @throws IllegalStateException if the provided dimensions exceed those of the initialized texture.
     */
    fun update(buffer: ByteBuffer, width: Int, height: Int, offset: Int = 0) {
        if (!initialized) return upload(buffer, width, height, offset)

        checkDimensions(width, height)
        glTexSubImage2D(GL_TEXTURE_2D, offset, 0, 0, width, height, format, GL_UNSIGNED_BYTE, buffer)
    }

    /**
     * Configures the level-of-detail (LOD) parameters for the texture.
     *
     * This method sets the minimum and maximum LOD values, as well as the base and maximum mipmap levels,
     * using the specified total number of mipmap levels. The [levels] parameter includes the base level (level 0),
     * so the highest valid mipmap level is [levels] - 1.
     *
     * Note: This configuration may not work correctly with textures using immutable storage.
     *
     * @param levels The total number of mipmap levels, including the base level.
     */
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

    /**
         * Validates that the dimensions of the new texture data do not exceed the allowed total dimensions.
         *
         * This method checks that the sum of the provided width and height does not exceed the sum of the texture's
         * current width and height, and ensures that the texture has been initialized. If the condition fails, it
         * throws an IllegalStateException with details about the expected and received total dimensions.
         *
         * @param width The width of the new texture data.
         * @param height The height of the new texture data.
         * @throws IllegalStateException if the texture is uninitialized or if the new data's dimensions exceed the allowed limits.
         */
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
