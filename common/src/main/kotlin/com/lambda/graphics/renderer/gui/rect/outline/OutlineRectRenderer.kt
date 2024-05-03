package com.lambda.graphics.renderer.gui.rect.outline

import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.renderer.gui.rect.AbstractRectRenderer
import com.lambda.graphics.renderer.gui.rect.IRectEntry
import com.lambda.graphics.shader.Shader

class OutlineRectRenderer : AbstractRectRenderer<IRectEntry.Outline> (
    VertexAttrib.Group.RECT_OUTLINE, shader
) {
    override fun newEntry(block: IRectEntry.Outline.() -> Unit) =
        OutlineRectEntry(this, block)

    companion object {
        private val shader = Shader("renderer/rect_outline")
    }
}