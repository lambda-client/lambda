package com.lambda.graphics.texture

import com.lambda.graphics.texture.TextureUtils.bindTexture
import com.lambda.threading.mainThread
import com.lambda.threading.runGameScheduled
import org.lwjgl.opengl.GL13.glGenTextures

abstract class Texture {
    private val id = glGenTextures()

    fun bind(slot: Int = 0) = bindTexture(id, slot)
}
