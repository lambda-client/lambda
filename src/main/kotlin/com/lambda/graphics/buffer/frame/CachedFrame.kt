/*
 * Copyright 2025 Lambda
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

package com.lambda.graphics.buffer.frame

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.graphics.gl.Matrices
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11C.glViewport

/**
 * A class that handles a cached frame, encapsulating a framebuffer with a specified width and height.
 * It provides methods for binding the framebuffer texture and writing to the framebuffer with custom rendering operations.
 *
 * @param width The width of the framebuffer.
 * @param height The height of the framebuffer.
 */
class CachedFrame(val width: Int, val height: Int) {

    // The framebuffer associated with this cached frame
    private val frameBuffer = FrameBuffer(width, height)

    /**
     * Binds the color texture of the framebuffer to a specified texture slot.
     *
     * @param slot The texture slot to bind the color texture to. Defaults to slot 0.
     */
    fun bind(slot: Int = 0) = frameBuffer.bindColorTexture(slot)

    /**
     * Executes custom drawing operations on the framebuffer.
     *
     * The method temporarily modifies the view and projection matrices, the viewport,
     * and then restores them after the block is executed.
     *
     * @param block A block of code that performs custom drawing operations on the framebuffer.
     */
    fun write(block: () -> Unit): CachedFrame {
        frameBuffer.write {
            // Save the current viewmodel matrix
            Matrices.push()
            // Set the viewmodel matrix to translate the scene away
            Matrices.peek().set(Matrix4f().translate(0f, 0f, -3000f))

            // Save the previous projection matrix and set a custom orthographic projection
            val prevProj = Matrix4f(RenderMain.projectionMatrix)
            RenderMain.projectionMatrix.setOrtho(0f, width.toFloat(), height.toFloat(), 0f, 1000f, 21000f)

            // Resize the viewport to match the framebuffer's dimensions
            glViewport(0, 0, width, height)

            // Execute the drawing operations defined in the block
            block()

            // Restore the previous viewport dimensions
            glViewport(0, 0, mc.framebuffer.viewportWidth, mc.framebuffer.viewportHeight)

            // Restore the previous projection matrix
            RenderMain.projectionMatrix.set(prevProj)

            // Restore the previous viewmodel matrix
            Matrices.pop()
        }

        return this
    }
}
