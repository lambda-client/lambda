package com.lambda.graphics.buffer.vao

import java.awt.Color

interface IRenderContext {
    fun vec3(x: Double, y: Double, z: Double): IRenderContext
    fun vec2(x: Double, y: Double): IRenderContext

    fun vec3m(x: Double, y: Double, z: Double): IRenderContext
    fun vec2m(x: Double, y: Double): IRenderContext

    fun float(v: Double): IRenderContext
    fun color(color: Color): IRenderContext
    fun end(): Int

    fun putLine(vertex1: Int, vertex2: Int)
    fun putTriangle(vertex1: Int, vertex2: Int, vertex3: Int)
    fun putQuad(vertex1: Int, vertex2: Int, vertex3: Int, vertex4: Int)

    fun render()
    fun upload()
    fun clear()

    fun grow(amount: Int)

    fun use(block: IRenderContext.() -> Unit) {
        block()
    }
}