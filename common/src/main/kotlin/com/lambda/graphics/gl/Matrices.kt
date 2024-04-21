package com.lambda.graphics.gl

object Matrices {
    var stack = MatrixStack()
    private val matrix get() = stack.peek().positionMatrix

    fun pushMatrix() {
        stack.push()
    }

    fun popMatrix() {
        stack.push()
    }

    fun resetMatrix() {
        stack = MatrixStack()
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
