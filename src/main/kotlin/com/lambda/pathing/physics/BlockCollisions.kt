package com.lambda.pathing.physics

import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class BlockCollisions {
	private var size = 0
	private var positions = IntArray(INITIAL_CAPACITY * 3)
	private var shapes = arrayOfNulls<VoxelShape>(INITIAL_CAPACITY)

	val isEmpty: Boolean get() = size == 0

	fun clear() {
		for (index in 0 until size) shapes[index] = null
		size = 0
	}

	fun add(x: Int, y: Int, z: Int, shape: VoxelShape) {
		if (size == shapes.size) {
			shapes = shapes.copyOf(size * 2)
			positions = positions.copyOf(size * 6)
		}
		positions[size * 3] = x
		positions[size * 3 + 1] = y
		positions[size * 3 + 2] = z
		shapes[size] = if (shape === VoxelShapes.fullCube()) null else shape.offset(x.toDouble(), y.toDouble(), z.toDouble())
		size++
	}

	fun maxOffset(axis: Direction.Axis, box: Box, maxDist: Double): Double {
		var distance = maxDist
		for (index in 0 until size) {
			if (abs(distance) < Vanilla.COLLISION_EPSILON) return 0.0
			val shape = shapes[index]
			distance = if (shape != null) {
				shape.calculateMaxDistance(axis, box, distance)
			} else {
				fullCubeMaxDistance(axis, box, distance, positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2])
			}
		}
		return distance
	}

	fun stepHeights(minY: Double, maximum: Float, base: Float): FloatArray {
		val heights = HashSet<Float>(4)
		fun offer(point: Double): Boolean {
			val height = (point - minY).toFloat()
			if (height < 0.0F || height == base) return true
			if (height > maximum) return false
			heights += height
			return true
		}
		for (index in 0 until size) {
			val shape = shapes[index]
			if (shape == null) {
				val y = positions[index * 3 + 1].toDouble()
				if (offer(y)) offer(y + 1.0)
			} else {
				val points = shape.getPointPositions(Direction.Axis.Y)
				for (point in 0 until points.size) if (!offer(points.getDouble(point))) break
			}
		}
		return heights.sorted().toFloatArray()
	}

	private fun fullCubeMaxDistance(axis: Direction.Axis, box: Box, maxDist: Double, x: Int, y: Int, z: Int): Double {
		val overlaps = when (axis) {
			Direction.Axis.X -> spans(box.minY, box.maxY, y) && spans(box.minZ, box.maxZ, z)
			Direction.Axis.Y -> spans(box.minX, box.maxX, x) && spans(box.minZ, box.maxZ, z)
			Direction.Axis.Z -> spans(box.minX, box.maxX, x) && spans(box.minY, box.maxY, y)
		}
		if (!overlaps) return maxDist
		val low = axis.choose(x, y, z).toDouble()
		if (maxDist > 0.0) {
			val front = box.getMax(axis)
			if (!(front - Vanilla.COLLISION_EPSILON < low)) return maxDist
			val gap = low - front
			return if (gap >= -Vanilla.COLLISION_EPSILON) min(maxDist, gap) else maxDist
		}
		if (maxDist < 0.0) {
			val back = box.getMin(axis)
			if (back + Vanilla.COLLISION_EPSILON < low + 1.0) return maxDist
			val gap = low + 1.0 - back
			return if (gap <= Vanilla.COLLISION_EPSILON) max(maxDist, gap) else maxDist
		}
		return maxDist
	}

	private fun spans(min: Double, max: Double, block: Int): Boolean =
		min + Vanilla.COLLISION_EPSILON < block + 1.0 && !(max - Vanilla.COLLISION_EPSILON < block)

	private companion object {
		const val INITIAL_CAPACITY = 16
	}
}
