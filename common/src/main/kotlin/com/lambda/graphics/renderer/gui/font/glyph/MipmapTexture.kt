package com.lambda.graphics.renderer.gui.font.glyph

import com.lambda.graphics.texture.Texture
import com.lambda.graphics.texture.TextureUtils.rescale
import com.lambda.graphics.texture.TextureUtils.setupLOD
import com.lambda.graphics.texture.TextureUtils.upload
import org.lwjgl.opengl.GL14.*
import java.awt.image.BufferedImage

class MipmapTexture(private val image: BufferedImage, private val levels: Int = 4) : Texture() {
    private var lastLod: Float? = null

    override fun init() {
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
}