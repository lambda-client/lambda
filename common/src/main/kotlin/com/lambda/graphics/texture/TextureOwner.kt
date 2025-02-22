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

import com.lambda.graphics.renderer.gui.font.sdf.DistanceFieldTexture
import com.lambda.util.readImage
import java.awt.image.BufferedImage

/**
 * The [TextureOwner] object is responsible for managing textures owned by various objects in the render pipeline
 */
object TextureOwner {
    private val textureMap = HashMap<Any, MutableList<Texture>>()

    /**
     * Retrieves the first texture owned by the object
     */
    val Any.texture: Texture
        get() = textureMap.getValue(this@texture)[0]

    /**
         * Retrieves the texture associated with the receiver object at the specified index.
         *
         * The returned texture is cast to the provided generic type [T], allowing for type-safe access.
         *
         * @param index the position of the texture in the object's associated texture list.
         * @return the texture at the specified index, cast as type [T].
         */
    @Suppress("unchecked_cast")
    fun <T : Texture> Any.texture(index: Int) =
        textureMap.getValue(this@texture)[index] as T

    /**
     * Binds a list of textures to texture slots, ensuring no more than 32 textures
     * are bound at once (to fit within the typical GPU limitations)
     *
     * @param textures The list of objects that own textures to be bound.
     * @throws IllegalArgumentException If more than 32 textures are provided.
     */
    fun bind(vararg textures: Any) {
        check(textures.size < 33) { "Texture slot overflow, expected to use less than 33 slots, got ${textures.size} slots" }

        textures.forEachIndexed { index, texture -> texture.texture.bind(index) }
    }

    /**
     * Binds the provided textures to consecutive GPU slots starting at slot 0.
     *
     * Ensures that no more than 32 textures are bound at once to comply with GPU limitations.
     *
     * @param textures the textures to be bound
     * @throws IllegalArgumentException if more than 32 textures are provided
     */
    fun bind(vararg textures: Texture) {
        check(textures.size < 33) { "Texture slot overflow, expected to use less than 33 slots, got ${textures.size} slots" }

        textures.forEachIndexed { index, texture -> texture.bind(index) }
    }

    /**
     * Uploads a texture from image data and associates it with the object,
     * optionally generating mipmaps for the texture
     *
     * @param data The image data as a [BufferedImage] to create the texture
     * @param mipmaps The number of mipmaps to generate for the texture (default is 1)
     * @return The created texture object
     */
    fun Any.upload(data: BufferedImage, mipmaps: Int = 1) =
        Texture(data, levels = mipmaps).also { textureMap.computeIfAbsent(this@upload) { mutableListOf() }.add(it) }

    /**
     * Uploads a texture from an image file path and associates it with the object,
     * optionally generating mipmaps for the texture
     *
     * @param path The resource path to the image file
     * @param mipmaps The number of mipmaps to generate for the texture (default is 1)
     * @return The created texture object
     */
    fun Any.upload(path: String, mipmaps: Int = 1) =
        Texture(path.readImage(), levels = mipmaps).also { textureMap.computeIfAbsent(this@upload) { mutableListOf() }.add(it) }

    /**
         * Uploads a distance field texture from the provided image data and associates it with the calling object.
         *
         * Distance field textures are typically used for rendering high-quality fonts. The created texture is
         * added to the object's texture list maintained in the texture map.
         *
         * @param data The image data used to create the distance field texture.
         * @return The newly created distance field texture.
         */
    fun Any.uploadField(data: BufferedImage) =
        DistanceFieldTexture(data).also { textureMap.computeIfAbsent(this@uploadField) { mutableListOf() }.add(it) }

    /**
         * Uploads an animated GIF as a texture and associates it with the calling object's texture list.
         *
         * @param path The resource path of the GIF file.
         * @return The animated texture instance that was created.
         */
    fun Any.uploadGif(path: String) =
        AnimatedTexture(path).also { textureMap.computeIfAbsent(this@uploadGif) { mutableListOf() }.add(it) }
}
