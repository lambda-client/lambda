package com.lambda.graphics.texture

import com.lambda.graphics.texture.TextureUtils.rescale
import com.lambda.graphics.texture.TextureUtils.setupLOD
import com.lambda.graphics.texture.TextureUtils.upload
import com.lambda.util.LambdaResource
import org.lwjgl.opengl.GL14.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class MipmapTexture(image: BufferedImage, levels: Int = 4) : Texture() {
    private var lastLod: Float? = null

    init {
        bind()
        setupLOD(levels)

        // Upload base image
        upload(image, 0)

        // Upload downscaled ones
        for (level in 1..levels) {
            val newWidth = image.width shr level
            val newHeight = image.height shr level
            val scaled = image.rescale(newWidth, newHeight)

            upload(scaled, level)
        }
    }

    fun setLOD(targetLod: Float) {
        if (lastLod == targetLod) return
        lastLod = targetLod

        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, targetLod)
    }

    companion object {
        /**
         * Retrieves an image from the resources folder and generates a mipmap texture.
         *
         * @param path The path to the image.
         * @param levels The number of mipmap levels.
         */
        fun fromResource(path: String, levels: Int = 4): MipmapTexture =
            MipmapTexture(ImageIO.read(LambdaResource(path).stream), levels)
    }
}
