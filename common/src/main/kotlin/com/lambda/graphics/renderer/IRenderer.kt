package com.lambda.graphics.renderer

interface IRenderer <T: IRenderEntry<T>> {
    /**
     * Registers new render entry
     */
    fun build(block: T.() -> Unit): T

    /**
     * Removes given entry from the render set
     */
    fun remove(entry: T): T

    /**
     * Performs a draw call and rebuilds VAO before (if needed)
     */
    fun render()

    /**
     * Updates all render entries
     */
    fun update()

    /**
     * Clears the render set
     */
    fun clear()

    /**
     * Destroys this renderer and frees v-ram used by VAO
     */
    fun destroy()
}