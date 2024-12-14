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

import com.lambda.util.LambdaResource
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import java.awt.image.BufferedImage

object TextureHandler {
    private val textureMap = Object2ObjectOpenHashMap<Any, Texture>()

    /**
     * Returns the texture owned by a specific object
     */
    val Any.texture: Texture
        get() = textureMap.getValue(this@texture)

    /**
     * Generate mipmap texture from data and associate it with its owner
     */
    fun Any.upload(data: BufferedImage, mipmaps: Int = 1) =
        Texture(data, levels = mipmaps).also { textureMap[this@upload] = it }

    /**
     * Generate mipmap texture from data and associate it with its owner
     *
     * @param path  Lambda resource path containing the image data
     */
    fun Any.upload(path: String, mipmaps: Int = 1) =
        Texture(LambdaResource.readImage(path), levels = mipmaps).also { textureMap[this@upload] = it }
}
