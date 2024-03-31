package com.lambda.gui.layer

import com.lambda.graphics.renderer.IRenderer
import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.rect.RectRenderer

class RenderLayer {
    private val renderers = mutableListOf<IRenderer<*>>()

    val rect = RectRenderer().apply(::register)
    val font = FontRenderer().apply(::register)

    fun register(renderer: IRenderer<*>) = renderers.add(renderer)

    fun render() {
        renderers.forEach(IRenderer<*>::update)
        renderers.forEach(IRenderer<*>::render)
    }
}
