package com.lambda.graphics.renderer

import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode

abstract class Renderer(drawMode: VertexMode, attribGroup: VertexAttrib.Group) {
    protected val vao = VAO(drawMode, attribGroup)

    open fun render() {
        vao.upload()
        vao.render()
        vao.clear()
    }
}