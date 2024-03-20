package com.lambda.graphics.renderer

import com.lambda.graphics.buffer.vao.VAO
import kotlinx.coroutines.*
import kotlin.properties.Delegates

abstract class Renderer <T: IRenderEntry<T>> : IRenderer<T> {
    private val entrySet = mutableSetOf<T>()
    private var rebuild = true

    abstract val vao: VAO
    protected abstract fun newEntry(block: T.() -> Unit): T

    override fun build(block: T.() -> Unit) =
        newEntry(block).process(entrySet::add)

    override fun remove(entry: T) =
        entry.process(entrySet::remove)

    override fun render() {
        if (rebuild) {
            rebuild = false

            vao.clear()
            entrySet.forEach { it.build(vao) }
            vao.upload()
        }

        vao.render()
    }

    override fun update() {
        entrySet.forEach(IRenderEntry<T>::update)
    }

    override fun clear() {
        entrySet.clear()
        vao.clear()
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
}