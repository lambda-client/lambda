package com.lambda.graphics.renderer

import com.lambda.graphics.buffer.vao.VAO
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.shader.Shader
import com.lambda.util.math.Vec2d
import kotlinx.coroutines.*
import kotlin.properties.Delegates

abstract class Renderer <T: IRenderEntry<T>> (private val shader: Shader) : IRenderer<T> {
    private val entrySet = mutableSetOf<T>()
    protected var rebuild = false
    private var destroyed = false

    val asRenderer get() = this as IRenderer<T>

    // Optimization tweak to reduce build calls when whole renderer moves
    // Instead of rebuilding all entries you translate the matrix
    var matrixOffset = Vec2d.ZERO

    abstract val vao: VAO
    protected abstract fun newEntry(block: T.() -> Unit): T

    override fun build(block: T.() -> Unit): T {
        checkDestroyed()
        return newEntry(block).process(entrySet::add)
    }

    override fun remove(entry: T): T {
        checkDestroyed()
        return entry.process(entrySet::remove)
    }

    override fun render() {
        checkDestroyed()

        if (rebuild) {
            rebuild = false

            vao.clear()
            entrySet.forEach { it.build(vao) }
            vao.upload()
        }

        Matrices.push()
        Matrices.translate(matrixOffset.x, matrixOffset.y, 0.0)

        shader.use()
        preRender()
        vao.render()

        Matrices.pop()
    }

    protected open fun preRender() {}

    override fun update() {
        checkDestroyed()
        entrySet.forEach(IRenderEntry<T>::update)
    }

    override fun clear() {
        checkDestroyed()

        entrySet.clear()
        vao.clear()
    }

    override fun destroy() {
        checkDestroyed()

        entrySet.clear()
        vao.destroy()
        destroyed = true
    }

    private fun T.process(action: T.() -> Unit): T {
        this.apply(action)
        rebuild = true

        return this
    }

    fun <T> field(initValue: T) =
        Delegates.observable(initValue) { _, prev: T, curr: T ->
            if (prev == curr) return@observable
            rebuild = true
        }

    private fun checkDestroyed() {
        check(!destroyed) { "Using the renderer after it is destroyed" }
    }
}