package com.lambda.graphics.texture

import com.lambda.graphics.texture.TextureUtils.bindTexture
import org.lwjgl.opengl.GL13.glGenTextures

open class Texture {
    val id = glGenTextures()

    fun bind(slot: Int = 0) = bindTexture(id, slot)
}
