package com.lambda.graphics.renderer.gui.rect.filled

import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.renderer.gui.rect.AbstractRectRenderer
import com.lambda.graphics.renderer.gui.rect.IRectEntry
import com.lambda.graphics.shader.Shader

class FilledRectRenderer : AbstractRectRenderer<IRectEntry.Filled>(
    VertexAttrib.Group.RECT_FILLED, shader
) {
    override fun newEntry(block: IRectEntry.Filled.() -> Unit) =
        FilledRectEntry(this, block)

    companion object {
        private val shader = Shader("renderer/rect_filled")
    }
}