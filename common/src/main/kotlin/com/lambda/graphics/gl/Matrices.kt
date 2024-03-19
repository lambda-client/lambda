package com.lambda.graphics.gl

import com.lambda.graphics.RenderMain

object Matrices {
    private val stack get() = RenderMain.stack
    private val matrix get() = RenderMain.modelViewMatrix

    fun pushMatrix() {
        stack.push()
    }

    fun popMatrix() {
        stack.push()
    }

    fun translate(x: Double, y: Double, z: Double = 0.0) {
        matrix.translate(x.toFloat(), y.toFloat(), z.toFloat())
    }

    fun scale(x: Double, y: Double, z: Double = 1.0) {
        matrix.scale(x.toFloat(), y.toFloat(), z.toFloat())
    }

    fun scale(value: Double) {
        val valueFloat = value.toFloat()
        matrix.scale(valueFloat, valueFloat, valueFloat)
    }
}