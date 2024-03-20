package com.lambda.graphics.renderer.gui.rect

import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.renderer.gui.AbstractGuiRenderer
import com.lambda.graphics.shader.Shader

class RectRenderer : AbstractGuiRenderer<IRectEntry>(
    VertexAttrib.Group.RECT
) {
    override fun render() {
        shader.use()
        super.render()
    }

    override fun newEntry(block: IRectEntry.() -> Unit) =
        RectEntry(this, block)

    companion object {
        private val shader = Shader("renderer/rect")
    }
}