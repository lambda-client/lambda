package com.lambda.pathing.world.snapshot

import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CollisionClass
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

data class SnapshotBlockPhysics(
	val collisionShape: VoxelShape,
	val slipperiness: Double = DEFAULT_SLIPPERINESS,
	val velocityMultiplier: Double = 1.0,
	val jumpVelocityMultiplier: Double = 1.0,
	val unsupportedPhysics: UnsupportedPhysics? = null,
	val coarseVoxel: CoarseVoxel = CoarseVoxel.UNKNOWN,
	val fenceLike: Boolean = false,
	val bounceFactor: Double = 0.0,
	val dampensSteppingSpeed: Boolean = false,
) {
	val collisionClass: CollisionClass =
		if (unsupportedPhysics != null) CollisionClass.FULL else CollisionClass.of(collisionShape)

	private val collisionBoxes = collisionShape.boundingBoxes

	internal fun intersectsCollisionBox(query: Box, x: Int, y: Int, z: Int): Boolean =
		collisionBoxes.any { local ->

			query.minX < local.maxX + x && query.maxX > local.minX + x &&
					query.minY < local.maxY + y && query.maxY > local.minY + y &&
					query.minZ < local.maxZ + z && query.maxZ > local.minZ + z
		}

	companion object {
		const val DEFAULT_SLIPPERINESS = Vanilla.DEFAULT_SLIPPERINESS.toDouble()

		val AIR = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.AIR)
		val FULL_CUBE = SnapshotBlockPhysics(VoxelShapes.fullCube(), coarseVoxel = CoarseVoxel.FULL_BLOCK)
	}
}
