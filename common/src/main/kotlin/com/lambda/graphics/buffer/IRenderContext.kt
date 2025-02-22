/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.graphics.buffer

import java.awt.Color

interface IRenderContext {
    fun vec3(x: Double, y: Double, z: Double): IRenderContext
    fun vec2(x: Double, y: Double): IRenderContext

    fun vec3m(x: Double, y: Double, z: Double): IRenderContext
    fun vec2m(x: Double, y: Double): IRenderContext

    fun float(v: Double): IRenderContext
    fun color(color: Color): IRenderContext
    fun end(): Int

    fun putLine(vertex1: Int, vertex2: Int)
    fun putTriangle(vertex1: Int, vertex2: Int, vertex3: Int)
    fun putQuad(vertex1: Int, vertex2: Int, vertex3: Int, vertex4: Int)

    fun render()
    fun upload()
    /**
 * Clears the current rendering context.
 *
 * This resets the context by removing any queued or rendered data,
 * preparing it for new drawing commands.
 */
fun clear()

    /**
     * Executes an immediate draw by sequentially calling upload, render, and clear.
     *
     * This method provides a single entry point for performing the complete draw cycle without requiring separate calls for
     * uploading data, rendering the content, and clearing the context.
     */
    fun immediateDraw() {
        upload()
        render()
        clear()
    }

    /**
 * Increases the capacity of the rendering context by the specified amount.
 *
 * @param amount The additional capacity units to add.
 */
fun grow(amount: Int)

    /**
     * Executes the given block within the current rendering context.
     *
     * This method allows for scoping multiple rendering operations or configurations within
     * a single lambda, using the current context as the receiver.
     *
     * @param block a lambda with receiver that encapsulates rendering commands.
     */
    fun use(block: IRenderContext.() -> Unit) {
        block()
    }
}
