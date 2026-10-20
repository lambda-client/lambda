package com.lambda.pathing.world.edit

import com.lambda.pathing.world.snapshot.EditableEnvironment
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

object SnapshotRaycast {
	fun cast(environment: EditableEnvironment, start: Vec3d, end: Vec3d): BlockHitResult? {
		if (start == end) return null
		val toX = MathHelper.lerp(-1.0E-7, end.x, start.x)
		val toY = MathHelper.lerp(-1.0E-7, end.y, start.y)
		val toZ = MathHelper.lerp(-1.0E-7, end.z, start.z)
		val fromX = MathHelper.lerp(-1.0E-7, start.x, end.x)
		val fromY = MathHelper.lerp(-1.0E-7, start.y, end.y)
		val fromZ = MathHelper.lerp(-1.0E-7, start.z, end.z)
		var x = MathHelper.floor(fromX)
		var y = MathHelper.floor(fromY)
		var z = MathHelper.floor(fromZ)
		hitIn(environment, x, y, z, start, end)?.let { return it }
		val dx = toX - fromX
		val dy = toY - fromY
		val dz = toZ - fromZ
		val stepX = MathHelper.sign(dx)
		val stepY = MathHelper.sign(dy)
		val stepZ = MathHelper.sign(dz)
		val deltaX = if (stepX == 0) Double.MAX_VALUE else stepX / dx
		val deltaY = if (stepY == 0) Double.MAX_VALUE else stepY / dy
		val deltaZ = if (stepZ == 0) Double.MAX_VALUE else stepZ / dz
		var nextX = deltaX * if (stepX > 0) 1.0 - MathHelper.fractionalPart(fromX) else MathHelper.fractionalPart(fromX)
		var nextY = deltaY * if (stepY > 0) 1.0 - MathHelper.fractionalPart(fromY) else MathHelper.fractionalPart(fromY)
		var nextZ = deltaZ * if (stepZ > 0) 1.0 - MathHelper.fractionalPart(fromZ) else MathHelper.fractionalPart(fromZ)
		while (nextX <= 1.0 || nextY <= 1.0 || nextZ <= 1.0) {
			if (nextX < nextY) {
				if (nextX < nextZ) {
					x += stepX
					nextX += deltaX
				} else {
					z += stepZ
					nextZ += deltaZ
				}
			} else if (nextY < nextZ) {
				y += stepY
				nextY += deltaY
			} else {
				z += stepZ
				nextZ += deltaZ
			}
			hitIn(environment, x, y, z, start, end)?.let { return it }
		}
		return null
	}

	private fun hitIn(environment: EditableEnvironment, x: Int, y: Int, z: Int, start: Vec3d, end: Vec3d): BlockHitResult? {
		val shape = environment.readBlock(x, y, z)?.rayShape ?: VoxelShapes.fullCube()
		if (shape.isEmpty) return null
		return shape.raycast(start, end, BlockPos(x, y, z))
	}
}
