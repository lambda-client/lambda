package com.lambda.graphics.renderer

interface IRenderer <T: IRenderEntry<T>> {
    val asRenderer get() = this // Downcast

    /**
     * Registers new render entry
     *
     * Forces the renderer to rebuild itself on the next update tick
     */
    fun build(block: T.() -> Unit): T

    /**
     * Removes given entry from the render set
     *
     * Forces the renderer to rebuild itself on the next update tick
     */
    fun remove(entry: T): T

    /**
     * Performs a draw call
     */
    fun render()

    /**
     * Ticks all render entries and rebuilds VAO if needed
     *
     * For DynamicESP renderers should be called from tick event
     */
    fun update()

    /**
     * Clears the render set
     */
    fun clear()
}