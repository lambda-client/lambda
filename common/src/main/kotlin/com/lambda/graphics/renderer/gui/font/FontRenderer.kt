package com.lambda.graphics.renderer.gui.font

import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.renderer.gui.AbstractGuiRenderer
import com.lambda.graphics.shader.Shader

class FontRenderer(private val font: LambdaFont = LambdaFont.FiraSansRegular) : AbstractGuiRenderer<IFontEntry>(
    VertexAttrib.Group.FONT, shader
) {
    override fun preRender() {
        font.glyphs.bind()
    }

    override fun newEntry(block: IFontEntry.() -> Unit) =
        FontEntry(this, block, font)

    companion object {
        private val shader = Shader("renderer/font")
    }
}
