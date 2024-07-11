package com.lambda.graphics.gl

import com.lambda.Lambda.mc
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Quaternionf

object Matrices {
    private val stack = ArrayDeque<Matrix4f>(1)

    var vertexTransformer: Matrix4d? = null

    fun translate(x: Double, y: Double, z: Double) {
        translate(x.toFloat(), y.toFloat(), z.toFloat())
    }

    fun translate(x: Float, y: Float, z: Float) {
        stack.last().translate(x, y, z)
    }

    fun scale(x: Double, y: Double, z: Double) {
        stack.last().scale(x.toFloat(), y.toFloat(), z.toFloat())
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

    fun push(block: Matrices.() -> Unit) {
        push()
        this.block()
        pop()
    }

    fun pop() {
        stack.removeLast()
    }

    fun peek() = stack.last()

    fun resetMatrix(entry: Matrix4f = Matrix4f()) {
        stack.clear()
        stack.add(entry)
    }

    fun withVertexTransform(matrix: Matrix4f, block: () -> Unit) {
        vertexTransformer = Matrix4d(matrix)
        block()
        vertexTransformer = null
    }

    fun buildWorldProjection(pos: Vec3d, scale: Double = 1.0) = Matrix4f().apply {
        val s = 0.025f * scale.toFloat()

        translate(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
        rotate(mc.gameRenderer.camera.rotation)
        scale(-s, -s, s)
    }
}
