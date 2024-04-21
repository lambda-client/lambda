package com.lambda.graphics.gl

import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import java.util.*
import kotlin.math.cbrt

class MatrixStack {
    private val stack: Deque<Entry> = ArrayDeque()

    init {
        val matrix4f = Matrix4f()
        val matrix3f = Matrix3f()
        stack.add(
            Entry(Matrix4f(), Matrix3f()
        ))
    }

    fun translate(x: Double, y: Double, z: Double): Matrix4f =
        translate(x.toFloat(), y.toFloat(), z.toFloat())

    fun translate(x: Float, y: Float, z: Float): Matrix4f =
        stack.last.positionMatrix.translate(x, y, z)

    fun scale(x: Float, y: Float, z: Float) {
        val entry = stack.last
        entry.positionMatrix.scale(x, y, z)
        if (x == y && y == z) {
            if (x > 0.0f) {
                return
            }
            entry.normalMatrix.scale(-1.0f)
        }
        val f = 1.0f / x
        val g = 1.0f / y
        val h = 1.0f / z
        val i = cbrt(f * g * h)
        entry.normalMatrix.scale(i * f, i * g, i * h)
    }

    fun multiply(quaternion: Quaternionf) {
        val entry = stack.last
        entry.positionMatrix.rotate(quaternion)
        entry.normalMatrix.rotate(quaternion)
    }

    fun multiply(quaternion: Quaternionf, originX: Float, originY: Float, originZ: Float) {
        val entry = stack.last
        entry.positionMatrix.rotateAround(quaternion, originX, originY, originZ)
        entry.normalMatrix.rotate(quaternion)
    }

    fun push() {
        val entry = stack.last
        stack.addLast(Entry(Matrix4f(entry.positionMatrix), Matrix3f(entry.normalMatrix)))
    }

    fun pop() {
        stack.removeLast()
    }

    fun peek(): Entry {
        return stack.last
    }

    fun isEmpty() = stack.size <= 1

    fun loadIdentity() {
        val entry = stack.last
        entry.positionMatrix.identity()
        entry.normalMatrix.identity()
    }

    fun multiplyPositionMatrix(matrix: Matrix4f) {
        stack.last.positionMatrix.mul(matrix)
    }

    data class Entry(val positionMatrix: Matrix4f, val normalMatrix: Matrix3f)
}
