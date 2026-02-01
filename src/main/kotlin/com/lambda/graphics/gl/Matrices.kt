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

package com.lambda.graphics.gl

import com.lambda.Lambda.mc
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4d
import org.joml.Matrix4f

/**
 * A utility object for managing OpenGL transformation matrices.
 * Provides a stack-based approach to matrix operations such as translation, scaling,
 * and world projection building, with optional support for vertex transformations.
 */
object Matrices {
	/**
	 * A stack of 4x4 transformation matrices.
	 */
	private val stack = ArrayDeque(listOf(Matrix4f()))

	/**
	 * An optional matrix for applying vertex transformations.
	 */
	var vertexTransformer: Matrix4d? = null

	/**
	 * An optional vec3 offset for applying vertex transformations.
	 */
	var vertexOffset: Vec3d? = null

	/**
	 * Executes a block of code within the context of a new matrix.
	 * The current matrix is pushed onto the stack before the block executes and popped after the block completes.
	 *
	 * Push and pop operations are essential for managing hierarchical transformations in OpenGL.
	 * - `push`: Saves the current matrix state to allow local transformations.
	 * - `block`: Code that uses the modified matrix (ex: rendering)
	 * - `pop`: Restores the previous state and effectively reverts any changes.
	 *
	 * @param block The block of code to execute within the context of the new matrix.
	 */
	fun push(block: Matrices.() -> Unit) {
		push()
		block.invoke(this)
		pop()
	}

	/**
	 * Pushes a copy of the current matrix onto the stack.
	 */
	fun push() {
		val entry = stack.last()
		stack.addLast(Matrix4f(entry))
	}

	/**
	 * Removes the top matrix from the stack.
	 *
	 * @throws NoSuchElementException If the stack is empty.
	 */
	fun pop() {
		stack.removeLast()
	}

	/**
	 * Applies a translation to the top matrix on the transformation stack
	 *
	 * @param x The translation amount along the X axis.
	 * @param y The translation amount along the Y axis.
	 * @param z The translation amount along the Z axis. Defaults to `0.0`.
	 */
	fun translate(x: Double, y: Double, z: Double = 0.0) {
		translate(x.toFloat(), y.toFloat(), z.toFloat())
	}

	/**
	 * Applies a translation to the top matrix on the transformation stack
	 *
	 * @param x The translation amount along the X axis.
	 * @param y The translation amount along the Y axis.
	 * @param z The translation amount along the Z axis. Defaults to `0f`.
	 */
	fun translate(x: Float, y: Float, z: Float = 0f) {
		stack.last().translate(x, y, z)
	}

	/**
	 * Scales the current matrix by the given x, y, and z factors.
	 *
	 * @param x The scaling factor along the X axis.
	 * @param y The scaling factor along the Y axis.
	 * @param z The scaling factor along the Z axis. Defaults to `1.0`.
	 */
	fun scale(x: Double, y: Double, z: Double = 1.0) {
		stack.last().scale(x.toFloat(), y.toFloat(), z.toFloat())
	}

	/**
	 * Scales the current matrix by the given x, y, and z factors.
	 *
	 * @param x The scaling factor along the X axis.
	 * @param y The scaling factor along the Y axis.
	 * @param z The scaling factor along the Z axis. Defaults to `1f`.
	 */
	fun scale(x: Float, y: Float, z: Float = 1f) {
		stack.last().scale(x, y, z)
	}

	/**
	 * Retrieves the current matrix from the stack without removing it.
	 *
	 * @throws NoSuchElementException if the stack is empty
	 * @return The top matrix on the stack
	 */
	fun peek(): Matrix4f = stack.last()

	/**
	 * Resets the matrix stack with a single initial matrix.
	 *
	 * @param entry The matrix to initialize the stack with.
	 */
	fun resetMatrices(entry: Matrix4f) {
		stack.clear()
		stack.add(entry)
	}

	/**
	 * Temporarily sets a vertex transformation matrix for the duration of a block.
	 *
	 * @param matrix The transformation matrix to apply to vertices.
	 * @param block The block of code to execute with the transformation applied.
	 */
	fun withVertexTransform(matrix: Matrix4f, block: () -> Unit) {
		vertexTransformer = Matrix4d(matrix)
		block()
		vertexTransformer = null
	}

	/**
	 * Applies a temporary vertex offset to mitigate precision issues in matrix operations on large coordinates
	 *
	 * @param offset the offset to apply to vertices for reducing precision loss
	 * @param block the block of code within which the vertex offset is active
	 */
	fun withVertexOffset(offset: Vec3d, block: () -> Unit) {
		vertexOffset = offset
		block()
		vertexOffset = null
	}

	/**
	 * Builds a world projection matrix for a given position, scale, and rotation mode.
	 *
	 * @param pos The position in world coordinates.
	 * @param scale The scaling factor. Defaults to `1.0`.
	 * @param mode The rotation mode to apply. Defaults to [ProjRotationMode.ToCamera].
	 * @return A [Matrix4f] representing the world projection.
	 */
	fun buildWorldProjection(
		pos: Vec3d,
		scale: Double = 1.0,
		mode: ProjRotationMode = ProjRotationMode.ToCamera
	): Matrix4f =
		Matrix4f().apply {
			val s = 0.025f * scale.toFloat()

			val rotation = when (mode) {
				ProjRotationMode.ToCamera -> mc.gameRenderer.camera.rotation
				ProjRotationMode.Up -> RotationAxis.POSITIVE_X.rotationDegrees(90f)
			}

			translate(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
			rotate(rotation)
			scale(-s, -s, s)
		}

	/**
	 * Modes for determining the rotation of the world projection.
	 */
	enum class ProjRotationMode {
		ToCamera,
		Up
	}
}
