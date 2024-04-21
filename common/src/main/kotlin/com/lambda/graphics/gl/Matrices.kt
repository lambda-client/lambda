package com.lambda.graphics.gl

import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import java.util.*
import kotlin.math.cbrt

object Matrices {
    private val stack: Deque<Entry> = ArrayDeque(
        listOf(Entry(Matrix4f(), Matrix3f()))
    )

    fun translate(x: Double, y: Double, z: Double): Matrix4f =
        translate(x.toFloat(), y.toFloat(), z.toFloat())

    fun translate(x: Float, y: Float, z: Float): Matrix4f =
        stack.last.position.translate(x, y, z)

    fun scale(x: Float, y: Float, z: Float) {
        val entry = stack.last
        entry.position.scale(x, y, z)
        if (x == y && y == z) {
            if (x > 0.0f) {
                return
            }
            entry.normal.scale(-1.0f)
        }
        val f = 1.0f / x
        val g = 1.0f / y
        val h = 1.0f / z
        val i = cbrt(f * g * h)
        entry.normal.scale(i * f, i * g, i * h)
    }

    fun multiply(quaternion: Quaternionf) {
        val entry = stack.last
        entry.position.rotate(quaternion)
        entry.normal.rotate(quaternion)
    }

    fun multiply(quaternion: Quaternionf, originX: Float, originY: Float, originZ: Float) {
        val entry = stack.last
        entry.position.rotateAround(quaternion, originX, originY, originZ)
        entry.normal.rotate(quaternion)
    }

    fun push() {
        val entry = stack.last
        stack.addLast(Entry(Matrix4f(entry.position), Matrix3f(entry.normal)))
    }

    fun pop() {
        stack.removeLast()
    }

    fun peek(): Entry {
        return stack.last
    }

    fun isEmpty() = stack.size <= 1

    fun resetMatrix() {
        val entry = stack.last
        entry.position.identity()
        entry.normal.identity()
    }

    fun loadIdentity() {
        val entry = stack.last
        entry.position.identity()
        entry.normal.identity()
    }

    fun multiplyPositionMatrix(matrix: Matrix4f) {
        stack.last.position.mul(matrix)
    }

    data class Entry(val position: Matrix4f, val normal: Matrix3f)
}
