package com.lambda.pathing.world

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import kotlin.math.floor
import net.minecraft.util.math.Vec3d
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
	CLIMBABLE,
	SOLID,
	UNKNOWN,
}

enum class CellClass(val plane: Int) {
	EMPTY(-1),
	SOLID(0),
	HAZARD(1),
	CLIMB(2),
	SPECIAL(3),
	;

	fun mark(planes: Array<LongArray>, index: Int) {
		if (plane >= 0) planes[plane][index ushr 6] = planes[plane][index ushr 6] or (1L shl index)
	}

	fun fill(planes: Array<LongArray>) {
		if (plane >= 0) planes[plane].fill(-1L)
	}

	companion object {
		const val PLANES = 4

		fun of(voxel: CoarseVoxel, collision: CollisionClass): CellClass = when {
			(voxel == CoarseVoxel.FULL_BLOCK || voxel == CoarseVoxel.BOUNCY_BLOCK) && collision == CollisionClass.FULL -> SOLID
			voxel == CoarseVoxel.AIR && collision == CollisionClass.EMPTY -> EMPTY
			voxel == CoarseVoxel.HAZARD && collision == CollisionClass.FULL -> HAZARD
			voxel == CoarseVoxel.CLIMBABLE && collision == CollisionClass.EMPTY -> CLIMB
			else -> SPECIAL
		}
	}
}

const val SECTION_CELL_COUNT = 16 * 16 * 16

data class CoarseVoxel(
	val fullyPassable: Boolean,
	val centerPassable: Boolean,
	val standingSurface: Double?,
	val intrusionHeight: Double = 0.0,
	val bouncy: Boolean = false,
	val medium: Medium = if (fullyPassable) Medium.AIR else Medium.SOLID,
) {
	val surfaceOffset: Double get() = (standingSurface ?: 1.0) - 1.0

	val intrudesAbove: Boolean get() = intrusionHeight > 0.0

	companion object {

		const val UNKNOWN_INTRUSION = 1.0

		val AIR = CoarseVoxel(fullyPassable = true, centerPassable = true, standingSurface = null)
		val FULL_BLOCK = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = 1.0)
		val BOUNCY_BLOCK = FULL_BLOCK.copy(bouncy = true)
		val UNKNOWN = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = null, intrusionHeight = UNKNOWN_INTRUSION, medium = Medium.UNKNOWN)
		val HAZARD = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = null, medium = Medium.UNKNOWN)
		val CLIMBABLE = CoarseVoxel(fullyPassable = true, centerPassable = true, standingSurface = null, medium = Medium.CLIMBABLE)
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

	fun cursor(): CoarseVoxelView = this

	val revision: Any? get() = null

	fun mayBounceWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean = true

	val mayBounce: Boolean get() = true

	fun cellClass(x: Int, y: Int, z: Int): CellClass =
		if (!isKnown(x, y, z)) CellClass.SPECIAL else CellClass.of(voxel(x, y, z), collisionClass(x, y, z))

	fun classifySection(sectionX: Int, sectionY: Int, sectionZ: Int, planes: Array<LongArray>) {
		val baseX = sectionX shl 4
		val baseY = sectionY shl 4
		val baseZ = sectionZ shl 4
		for (index in 0 until SECTION_CELL_COUNT) {
			cellClass(baseX + (index and 15), baseY + (index ushr 8), baseZ + ((index ushr 4) and 15)).mark(planes, index)
		}
	}
}

fun CoarseVoxelView.climbableAt(position: Vec3d): Boolean =
	medium(floor(position.x).toInt(), floor(position.y).toInt(), floor(position.z).toInt()) == Medium.CLIMBABLE

fun CoarseVoxelView.canStandAt(x: Int, y: Int, z: Int): Boolean =
	standingSurface(x, y - 1, z) != null && voxel(x, y, z).centerPassable && voxel(x, y + 1, z).centerPassable

fun Stance.center(view: CoarseVoxelView) = HorizontalPoint(
	x + 0.5,
	y + view.surfaceOffset(x, y - 1, z),
	z + 0.5,
)
