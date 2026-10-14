package com.lambda.pathing.world

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.FallRule
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

fun VoxelShape.flatBoxes(): DoubleArray {
	val boxes = boundingBoxes
	val flat = DoubleArray(boxes.size * 6)
	for ((index, box) in boxes.withIndex()) {
		flat[6 * index] = box.minX
		flat[6 * index + 1] = box.minY
		flat[6 * index + 2] = box.minZ
		flat[6 * index + 3] = box.maxX
		flat[6 * index + 4] = box.maxY
		flat[6 * index + 5] = box.maxZ
	}
	return flat
}

enum class Medium {
	AIR,
	CLIMBABLE,
	WATER,
	SOLID,
	UNKNOWN,
}

enum class CellClass(val plane: Int) {
	EMPTY(-1),
	SOLID(0),
	HAZARD(1),
	CLIMB(2),
	SPECIAL(3),
	WATER(4),
	;

	fun mark(planes: Array<LongArray>, index: Int) {
		if (plane >= 0) planes[plane][index ushr 6] = planes[plane][index ushr 6] or (1L shl index)
	}

	fun fill(planes: Array<LongArray>) {
		if (plane >= 0) planes[plane].fill(-1L)
	}

	companion object {
		const val PLANES = 5

		fun of(voxel: CoarseVoxel, collision: CollisionClass): CellClass = when {
			(voxel == CoarseVoxel.FULL_BLOCK || voxel == CoarseVoxel.BOUNCY_BLOCK) && collision == CollisionClass.FULL -> SOLID
			voxel == CoarseVoxel.AIR && collision == CollisionClass.EMPTY -> EMPTY
			voxel == CoarseVoxel.HAZARD && collision == CollisionClass.FULL -> HAZARD
			voxel == CoarseVoxel.CLIMBABLE && collision == CollisionClass.EMPTY -> CLIMB
			voxel == CoarseVoxel.WATER && collision == CollisionClass.FULL -> WATER
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
	val openSides: Int = if (fullyPassable) ALL_SIDES else 0,
	val partial: Boolean = false,
	val velocityMultiplier: Double = 1.0,
) {
	val surfaceOffset: Double get() = (standingSurface ?: 1.0) - 1.0

	val intrudesAbove: Boolean get() = intrusionHeight > 0.0

	fun opensToward(sides: Int): Boolean = openSides and sides == sides

	companion object {

		const val UNKNOWN_INTRUSION = 1.0

		const val EAST = 0b0001

		const val WEST = 0b0010

		const val SOUTH = 0b0100

		const val NORTH = 0b1000

		const val ALL_SIDES = EAST or WEST or SOUTH or NORTH

		fun side(dx: Int, dz: Int): Int = when {
			dx > 0 -> EAST
			dx < 0 -> WEST
			dz > 0 -> SOUTH
			dz < 0 -> NORTH
			else -> 0
		}

		fun sides(dx: Int, dz: Int): Int = side(dx, 0) or side(0, dz)

		fun dx(side: Int): Int = (side and EAST) - ((side and WEST) ushr 1)

		fun dz(side: Int): Int = ((side and SOUTH) ushr 2) - ((side and NORTH) ushr 3)

		val AIR = CoarseVoxel(fullyPassable = true, centerPassable = true, standingSurface = null)
		val FULL_BLOCK = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = 1.0)
		val BOUNCY_BLOCK = FULL_BLOCK.copy(bouncy = true)
		val UNKNOWN = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = null, intrusionHeight = UNKNOWN_INTRUSION, medium = Medium.UNKNOWN)
		val HAZARD = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = null, medium = Medium.UNKNOWN)
		val CLIMBABLE = CoarseVoxel(fullyPassable = true, centerPassable = true, standingSurface = null, medium = Medium.CLIMBABLE)
		val WATER = CoarseVoxel(fullyPassable = false, centerPassable = false, standingSurface = null, medium = Medium.WATER, openSides = ALL_SIDES)
	}
}

interface CoarseVoxelView {
	fun voxel(x: Int, y: Int, z: Int): CoarseVoxel

	fun entityAt(x: Int, y: Int, z: Int): Boolean = false

	fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? = null

	fun collisionBoxes(x: Int, y: Int, z: Int): DoubleArray? = collisionShape(x, y, z)?.flatBoxes()

	fun collisionClass(x: Int, y: Int, z: Int): CollisionClass {
		val shape = collisionShape(x, y, z) ?: return CollisionClass.UNKNOWN
		return CollisionClass.of(shape)
	}

	fun medium(x: Int, y: Int, z: Int): Medium = voxel(x, y, z).medium

	fun bubbles(x: Int, y: Int, z: Int): Boolean = false

	fun honey(x: Int, y: Int, z: Int): Boolean = false

	fun mayContainHoney(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean = true

	fun isKnown(x: Int, y: Int, z: Int): Boolean = true

	fun isUnknown(x: Int, y: Int, z: Int): Boolean = !isKnown(x, y, z)

	fun forEachKnownSection(action: (Long) -> Unit) {}

	val simulableStanceY: IntRange get() = Int.MIN_VALUE..Int.MAX_VALUE

	fun standingSurface(x: Int, y: Int, z: Int): Double? {
		voxel(x, y, z).standingSurface?.let { return it }
		return voxel(x, y - 1, z).intrusionHeight.takeIf { it > 0.0 && it < 1.0 }
	}

	fun surfaceOffset(x: Int, y: Int, z: Int): Double = (standingSurface(x, y, z) ?: 1.0) - 1.0

	fun fallRule(x: Int, y: Int, z: Int): FallRule = FallRule.NORMAL

	fun cursor(): CoarseVoxelView = this

	val terrain: Any get() = this

	val revision: Any? get() = null

	fun mayBounceWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean = true

	val mayBounce: Boolean get() = true

	fun maySoftLandWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean = true

	val maySoftLand: Boolean get() = true

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

fun Stance.center(view: CoarseVoxelView): HorizontalPoint {
	if (Footing.involvesPartial(view, x, y, z)) Footing.spot(view, this)?.let { return it }
	return HorizontalPoint(x + 0.5, y + view.surfaceOffset(x, y - 1, z), z + 0.5)
}
