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

package com.lambda.graphics

import com.lambda.event.EventFlow.post
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.gl.Matrices.resetMatrices
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.graphics.mc.TransientRegionESP
import org.joml.Matrix4f

object RenderMain {
    val StaticESP = TransientRegionESP("Static")
    val DynamicESP = TransientRegionESP("Dynamic")

    val projectionMatrix = Matrix4f()
    val modelViewMatrix
        get() = Matrices.peek()
    val projModel: Matrix4f
        get() = Matrix4f(projectionMatrix).mul(modelViewMatrix)

    @JvmStatic
    fun render3D(positionMatrix: Matrix4f, projMatrix: Matrix4f) {
        resetMatrices(positionMatrix)
        projectionMatrix.set(projMatrix)

        // Render transient ESPs using the new pipeline
        StaticESP.render(false) // Depth tested
        DynamicESP.render(true) // Through walls

        RenderEvent.Render.post()
    }

    init {
        listen<TickEvent.Post> {
            StaticESP.clear()
            DynamicESP.clear()

            RenderEvent.Upload.post()

            StaticESP.upload()
            DynamicESP.upload()
        }
    }
}
