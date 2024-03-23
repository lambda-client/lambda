package com.lambda.graphics.renderer.gui.font.glyph

import com.lambda.graphics.texture.Texture
import com.lambda.graphics.texture.TextureUtils.setupLOD
import com.lambda.graphics.texture.TextureUtils.upload
import com.lambda.module.modules.client.FontSettings
import org.lwjgl.opengl.GL14.*
import java.awt.image.BufferedImage

class FontTexture(private val textures: List<BufferedImage>) : Texture() {
    private var lastLod: Float? = null

    override fun init() {
        setupLOD(textures.size)

        textures.forEachIndexed { level: Int, image: BufferedImage ->
            upload(image, level)
        }
    }

    override fun bind() {
        super.bind()

        val targetLod = FontSettings.lodBias.toFloat()

        if (lastLod == targetLod) return
        lastLod = targetLod

        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_LOD_BIAS, targetLod)
    }
}