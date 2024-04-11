package com.lambda.graphics.renderer.gui

import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.renderer.Renderer
import com.lambda.graphics.renderer.IRenderEntry

abstract class AbstractGuiRenderer <T: IRenderEntry<T>> (
    vertexType: VertexAttrib.Group
) : Renderer<T>() {
    override val vao = VAO(VertexMode.TRIANGLES, vertexType)
}