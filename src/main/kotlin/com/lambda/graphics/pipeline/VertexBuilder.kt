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

package com.lambda.graphics.pipeline

import com.lambda.graphics.buffer.DynamicByteBuffer
import com.lambda.graphics.gl.Matrices
import org.joml.Vector4d

/**
 * A builder class for constructing vertex buffer objects (VBOs) with associated vertex attributes and indices.
 * Provides a DSL-like syntax for defining vertices and their attributes in a type-safe manner.
 */
@Suppress("DuplicatedCode")
class VertexBuilder(
    private val direct: VertexPipeline? = null
) {
    val vertices by lazy { mutableListOf<Attribute>() }
    val indices by lazy { mutableListOf<Int>() }

    private var verticesCounter = 0

    /**
     * Adds multiple indices to the index buffer
     * @param indices The indices to add to the element array buffer
     */
    fun build(vararg indices: Int) {
        direct?.let {
            indices.forEach { i ->
                it.indices.putInt(i)
            }

            return
        }

        indices.forEach { this.indices += it }
    }

    /**
     * Adds rectangle indices (2 triangles) using 4 vertices
     */
    fun buildQuad(
        index1: Int, index2: Int, index3: Int, index4: Int
    ) {
        direct?.let {
            it.indices.putInt(index1)
            it.indices.putInt(index2)
            it.indices.putInt(index3)
            it.indices.putInt(index3)
            it.indices.putInt(index4)
            it.indices.putInt(index1)
            return
        }

        this.indices += index1
        this.indices += index2
        this.indices += index3
        this.indices += index3
        this.indices += index4
        this.indices += index1
    }

    /**
     * Adds line indices between 2 vertices
     */
    fun buildLine(
        index1: Int, index2: Int,
    ) {
        direct?.let {
            it.indices.putInt(index1)
            it.indices.putInt(index2)
            return
        }

        this.indices += index1
        this.indices += index2
    }

    /**
     * Adds triangle indices using 3 vertices
     */
    fun buildTriangle(
        index1: Int, index2: Int, index3: Int
    ) {
        direct?.let {
            it.indices.putInt(index1)
            it.indices.putInt(index2)
            it.indices.putInt(index3)
            return
        }

        this.indices += index1
        this.indices += index2
        this.indices += index3
    }

    /**
     * Creates a collection of indices from varargs
     * @return List of provided indices for element array buffer
     */
    fun collect(vararg indices: Int) =
        indices

    /**
     * Creates a new vertex with specified attributes
     * @param block Configuration lambda for defining vertex attributes
     * @return Index of the created vertex in the vertex array
     */
    fun vertex(block: Vertex.() -> Unit): Int {
        Vertex(this).apply(block)
        return verticesCounter++
    }

    /**
     * Uploads constructed vertex data to a rendering pipeline
     * @param pipeline The target pipeline for vertex and index data
     */
    fun uploadTo(pipeline: VertexPipeline) {
        check(direct == null) {
            "Builder is already associated with a rendering pipeline. Cannot upload data again."
        }

        uploadVertices(pipeline.vertices)
        uploadIndices(pipeline.indices)
    }

    fun uploadVertices(buffer: DynamicByteBuffer) {
        vertices.forEach { attribute ->
            attribute.upload(buffer)
        }
    }

    fun uploadIndices(buffer: DynamicByteBuffer) {
        indices.forEach {
            buffer.putInt(it)
        }
    }

    /**
     * Represents a single vertex with multiple attributes.
     * Attributes are stored in the order they're declared, which must match shader layout.
     */
    class Vertex(private val builder: VertexBuilder) {

        /**
         * Adds a single-precision floating point attribute
         * @param value The scalar value to add
         */
        fun float(value: Double): Vertex {
            builder.direct?.let {
                it.vertices.putFloat(value)
                return this
            }

            builder.vertices.add(Attribute.Float(value))
            return this
        }

        /**
         * Adds a 2-component vector attribute
         * @param x X-axis component
         * @param y Y-axis component
         */
        fun vec2(x: Double, y: Double): Vertex {
            builder.direct?.let {
                it.vertices.putVec2(x, y)
                return this
            }

            builder.vertices.add(Attribute.Vec2(x, y))
            return this
        }

        /**
         * Adds a matrix-transformed 2-component vector
         * @param x X-axis component
         * @param y Y-axis component
         */
        fun vec2m(x: Double, y: Double) =
            Matrices.vertexTransformer?.let { mat ->
                val vec = Vector4d(x, y, 0.0, 1.0).apply(mat::transform)
                vec2(vec.x, vec.y)
            } ?: vec2(x, y)

        /**
         * Adds a 3-component vector attribute
         * @param x X-axis component
         * @param y Y-axis component
         * @param z Z-axis component
         */
        fun vec3(x: Double, y: Double, z: Double): Vertex {
            builder.direct?.let {
                it.vertices.putVec3(x, y, z)
                return this
            }

            builder.vertices.add(Attribute.Vec3(x, y, z))
            return this
        }

        /**
         * Adds a matrix-transformed 3-component vector attribute
         * @param x X-axis component
         * @param y Y-axis component
         * @param z Z-axis component (defaults to 0.0)
         */
        fun vec3m(x: Double, y: Double, z: Double = 0.0) =
            Matrices.vertexTransformer?.let { mat ->
                val vec = Vector4d(x, y, z, 1.0).apply(mat::transform)
                vec3(vec.x, vec.y, vec.z)
            } ?: vec3(x, y, z)

        /**
         * Adds a color attribute in RGBA format
         * @param color Color value using AWT Color class
         */
        fun color(color: java.awt.Color): Vertex {
            builder.direct?.let {
                it.vertices.putColor(color)
                return this
            }

            builder.vertices.add(Attribute.Color(color))
            return this
        }
    }

    /**
     * Sealed hierarchy representing different vertex attribute types.
     * Each attribute knows how to upload itself to a [DynamicByteBuffer].
     *
     * @property upload Lambda that handles writing the attribute data to a buffer
     */
    sealed class Attribute(
        val upload: DynamicByteBuffer.() -> Unit
    ) {
        /**
         * Single-precision floating point attribute
         * @property value Scalar floating point value
         */
        data class Float(
            var value: Double
        ) : Attribute({ putFloat(value) })

        /**
         * 2-component vector attribute
         * @property x X-axis component
         * @property y Y-axis component
         */
        data class Vec2(
            var x: Double, var y: Double
        ) : Attribute({ putVec2(x, y) })

        /**
         * 3-component vector attribute
         * @property x X-axis component
         * @property y Y-axis component
         * @property z Z-axis component
         */
        data class Vec3(
            var x: Double, var y: Double, var z: Double
        ) : Attribute({ putVec3(x, y, z) })

        /**
         * Color attribute in RGBA format
         * @property color Color value using AWT Color class
         */
        data class Color(
            var color: java.awt.Color
        ) : Attribute({ putColor(color) })
    }
}
