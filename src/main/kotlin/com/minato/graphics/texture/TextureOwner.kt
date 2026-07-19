
package com.minato.graphics.texture

import com.minato.util.readImage
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
     * Retrieves the texture associated with the receiver object at the specified index
     *
     * @param index The index of the texture to retrieve
     * @return The texture [T] at the given index
     */
    @Suppress("unchecked_cast")
    fun <T : Texture> Any.texture(index: Int) =
        textureMap.getValue(this@texture)[index] as T

    /**
     * Binds a list of textures to texture slots, ensuring no more than 96 textures
     * are bound at once (to fit within the typical GPU limitations)
     *
     * @param textures The list of objects that own textures to be bound.
     * @throws IllegalArgumentException If more than 96 textures are provided.
     */
    fun bind(vararg textures: Any) {
        check(textures.size <= 96) { "Expected equal or less than 96 simultaneous textures, got ${textures.size} textures" }

        textures.forEachIndexed { index, texture -> texture.texture.bind(index) }
    }

    /**
     * Binds a list of textures to texture slots, ensuring no more than 96 textures
     * are bound at once (to fit within the typical GPU limitations)
     *
     * @param textures The list of textures to be bound
     * @throws IllegalArgumentException If more than 96 textures are provided
     */
    fun bind(vararg textures: Texture) {
        check(textures.size <= 96) { "Expected equal or less than 96 simultaneous textures, got ${textures.size} textures" }

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
        Texture(path.readImage(), levels = mipmaps).also {
            textureMap.computeIfAbsent(this@upload) { mutableListOf() }.add(it)
        }

    /**
     * Uploads a GIF and associates it with the object as an animated texture
     *
     * @param path The resource path to the GIF file
     * @return The created animated texture object
     */
    fun Any.uploadGif(path: String) =
        AnimatedTexture(path).also { textureMap.computeIfAbsent(this@uploadGif) { mutableListOf() }.add(it) }
}
