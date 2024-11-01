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
    fun clear()

    fun grow(amount: Int)

    fun use(block: IRenderContext.() -> Unit) {
        block()
    }
}
