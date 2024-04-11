package com.lambda.graphics.renderer

import com.lambda.graphics.buffer.vao.IRenderContext

interface IRenderEntry <T : IRenderEntry<T>> {
    val owner: IRenderer<T>
    val updateBlock: T.() -> Unit

    /**
     * Builds data for rendering
     */
    fun build(ctx: IRenderContext)

    /**
     * Updates this render entry
     */
    fun update() {
        @Suppress("UNCHECKED_CAST")
        updateBlock(this as T)
    }

    /**
     * Destroys this render entry
     *
     * Calling this function has the same result as calling renderer.remove(myEntry)
     */
    fun destroy() {
        @Suppress("UNCHECKED_CAST")
        owner.remove(this as T)
    }
}