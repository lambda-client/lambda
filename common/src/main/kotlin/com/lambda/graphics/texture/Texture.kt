package com.lambda.graphics.texture

import com.lambda.graphics.texture.TextureUtils.bindTexture
import com.lambda.threading.mainThread
import org.lwjgl.opengl.GL13.glGenTextures

abstract class Texture {
    private val id by mainThread {
        glGenTextures().apply {
            bindTexture(this)
            init()
        }
    }

    protected abstract fun init()

    open fun bind(slot: Int = 0) = bindTexture(id, slot)
}
