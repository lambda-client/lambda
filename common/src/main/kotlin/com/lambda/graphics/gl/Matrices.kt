package com.lambda.graphics.gl

import org.joml.Matrix4f
import org.joml.Quaternionf
import kotlin.collections.ArrayDeque

object Matrices {
    private val stack = ArrayDeque(listOf(Matrix4f()))

    fun translate(x: Double, y: Double, z: Double) {
        translate(x.toFloat(), y.toFloat(), z.toFloat())
    }

    fun translate(x: Float, y: Float, z: Float) {
        stack.last().translate(x, y, z)
    }

    fun scale(x: Float, y: Float, z: Float) {
        stack.last().scale(x, y, z)
    }

    fun multiply(quaternion: Quaternionf) {
        stack.last().rotate(quaternion)
    }

    fun multiply(quaternion: Quaternionf, originX: Float, originY: Float, originZ: Float) {
        stack.last().rotateAround(quaternion, originX, originY, originZ)
    }

    fun push() {
        val entry = stack.last()
        stack.addLast(Matrix4f(entry))
    }

    fun pop() {
        stack.removeLast()
    }

    fun peek() = stack.last()

    fun resetMatrix(entry: Matrix4f = Matrix4f()) {
        stack.clear()
        stack.add(entry)
    }
}