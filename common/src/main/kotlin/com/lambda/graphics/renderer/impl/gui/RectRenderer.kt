package com.lambda.graphics.renderer.impl.gui

import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.renderer.entry.gui.IRectEntry
import com.lambda.graphics.renderer.entry.gui.RectEntry
import com.lambda.graphics.renderer.impl.AbstractGuiRenderer
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