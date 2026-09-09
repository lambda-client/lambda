package com.lambda.pathing.world

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

enum class CollisionClass {

	EMPTY,

	FULL,

	PARTIAL,

	UNKNOWN,
	;

	companion object {

		fun of(shape: VoxelShape): CollisionClass = when {
			shape.isEmpty -> EMPTY
			shape === VoxelShapes.fullCube() -> FULL
			isFullCube(shape) -> FULL
			else -> PARTIAL
		}

		private fun isFullCube(shape: VoxelShape): Boolean {
			val boxes = shape.boundingBoxes
			if (boxes.size != 1) return false
			val box = boxes[0]
			return box.minX <= EPSILON && box.minY <= EPSILON && box.minZ <= EPSILON &&
					box.maxX >= 1.0 - EPSILON && box.maxY >= 1.0 - EPSILON && box.maxZ >= 1.0 - EPSILON
		}

		private const val EPSILON = 1.0E-7
	}
}

enum class Medium {
	AIR,
	WATER,
	LAVA,
	POWDER_SNOW,
	COBWEB,
	CLIMBABLE,
	SOLID,
	UNKNOWN,
}

data class CoarseVoxel(
	val fullyPassable: Boolean,
	val centerPassable: Boolean,

	val standingSurface: Double?,

	val intrusionHeight: Double = 0.0,

	val bouncy: Boolean = false,
	val medium: Medium = if (fullyPassable) Medium.AIR else Medium.SOLID,
) {

	val surfaceOffset: Double get() = (standingSurface ?: 1.0) - 1.0

	val standable: Boolean get() = standingSurface != null

	val intrudesAbove: Boolean get() = intrusionHeight > 0.0

	companion object {

		const val UNKNOWN_INTRUSION = 1.0

		val AIR = CoarseVoxel(fullyPassable = true, centerPassable = true, standingSurface = null)
		val FULL_BLOCK = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = 1.0)
		val UNKNOWN = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = null, intrusionHeight = UNKNOWN_INTRUSION, medium = Medium.UNKNOWN)
		val HAZARD = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = null, medium = Medium.UNKNOWN)

		fun of(medium: Medium, passable: Boolean = true) = CoarseVoxel(
			fullyPassable = passable,
			centerPassable = passable,
			standingSurface = null,
			medium = medium,
		)
	}
}

interface CoarseVoxelView {
	fun voxel(x: Int, y: Int, z: Int): CoarseVoxel

	fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? = null

	fun collisionClass(x: Int, y: Int, z: Int): CollisionClass {
		val shape = collisionShape(x, y, z) ?: return CollisionClass.UNKNOWN
		return CollisionClass.of(shape)
	}

	fun medium(x: Int, y: Int, z: Int): Medium = voxel(x, y, z).medium

	fun isKnown(x: Int, y: Int, z: Int): Boolean = true

	val simulableStanceY: IntRange get() = Int.MIN_VALUE..Int.MAX_VALUE

	fun standingSurface(x: Int, y: Int, z: Int): Double? {
		voxel(x, y, z).standingSurface?.let { return it }
		return voxel(x, y - 1, z).intrusionHeight.takeIf { it > 0.0 && it < 1.0 }
	}

	fun surfaceOffset(x: Int, y: Int, z: Int): Double = (standingSurface(x, y, z) ?: 1.0) - 1.0
}

fun Stance.center(view: CoarseVoxelView) = HorizontalPoint(
	x + 0.5,
	y + view.surfaceOffset(x, y - 1, z),
	z + 0.5,
)
