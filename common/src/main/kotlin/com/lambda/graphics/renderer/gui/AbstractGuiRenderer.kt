package com.lambda.graphics.renderer.gui

import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.buffer.vao.vertex.VertexAttrib
import com.lambda.graphics.buffer.vao.vertex.VertexMode
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.renderer.Renderer
import com.lambda.graphics.renderer.IRenderEntry
import com.lambda.graphics.shader.Shader
import com.lambda.util.TransformedObservable
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

abstract class AbstractGuiRenderer <T: IRenderEntry<T>> (
    vertexType: VertexAttrib.Group,
    shader: Shader
) : Renderer<T>(shader) {
    override val vao = VAO(VertexMode.TRIANGLES, vertexType)

    fun positionVec2() =
        object : TransformedObservable<Vec2d>(Vec2d.ZERO) {
            override fun transform(value: Vec2d) =
                value - matrixOffset

            override fun onChange(oldValue: Vec2d, newValue: Vec2d) {
                rebuild = true
            }
        }

    fun positionRect() =
        object : TransformedObservable<Rect>(Rect.ZERO) {
            override fun transform(value: Rect) =
                value - matrixOffset

            override fun onChange(oldValue: Rect, newValue: Rect) {
                rebuild = true
            }
        }
}