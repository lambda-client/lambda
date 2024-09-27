package com.lambda.graphics.texture

import com.lambda.graphics.texture.TextureUtils.bindTexture
import org.lwjgl.opengl.GL45C.*

open class Texture {
    val id = glGenTextures()

    open fun bind(slot: Int = 0) = bindTexture(id, slot)
}
