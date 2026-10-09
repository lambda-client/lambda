package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseMoveCosts
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.families.MovementCatalog
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.abs

class SimpleMoveLibrary private constructor(val catalog: MovementCatalog) {
	val templates: List<MotionTemplate> = catalog.templates
	internal val stampPrograms by lazy(LazyThreadSafetyMode.PUBLICATION) { templates.map { it.stampProgram() } }
	private val templatesWithoutBounces = templates.filter { it.movement != MovementId.BOUNCE }
	private val bounceReach = templates.maxOfOrNull { if (it.bounceDrop == null) 0 else 2 * maxOf(abs(it.dx), abs(it.dz)) + 1 } ?: 0
	private val bounceDepth = templates.maxOfOrNull { it.bounceDrop?.plus(2) ?: 0 } ?: 0
	internal val groups = TemplateGroups(templates)
	private val readOffsets: Set<VoxelPos> = templates.flatMapTo(HashSet()) { it.readOffsets }
	private val heuristicCaps = HeuristicCaps.of(templates)
	val sustainedTicksPerBlock: Double = sustainedRate(templates)

	private val minReadX = readOffsets.minOf(VoxelPos::x)
	private val maxReadX = readOffsets.maxOf(VoxelPos::x)
	private val minReadZ = readOffsets.minOf(VoxelPos::z)
	private val maxReadZ = readOffsets.maxOf(VoxelPos::z)
	private val minReadY = minOf(readOffsets.minOf(VoxelPos::y), -1)
	private val maxReadY = maxOf(readOffsets.maxOf(VoxelPos::y), 1)

	private val targetReadOffsets: Set<VoxelPos> = buildSet {
		for (template in templates) {
			for ((x, y, z) in template.readOffsets) {
				add(VoxelPos(template.dx - x, template.dy - y, template.dz - z))
			}
		}
		add(VoxelPos(0, -1, 0))
		add(VoxelPos(0, 0, 0))
		add(VoxelPos(0, 1, 0))
	}

	private val minTargetX = targetReadOffsets.minOf(VoxelPos::x)
	private val maxTargetX = targetReadOffsets.maxOf(VoxelPos::x)
	private val minTargetZ = targetReadOffsets.minOf(VoxelPos::z)
	private val maxTargetZ = targetReadOffsets.maxOf(VoxelPos::z)

	fun readsUnknown(view: CoarseVoxelView, origin: Stance): Boolean {
		val x0 = origin.x + minReadX
		val x1 = origin.x + maxReadX
		val y0 = origin.y + minReadY
		val y1 = origin.y + maxReadY
		val z0 = origin.z + minReadZ
		val z1 = origin.z + maxReadZ
		var x = x0
		while (x <= x1) {
			var z = z0
			while (z <= z1) {
				var y = y0
				while (y <= y1) {
					if (!view.isKnown(x, y, z)) return true
					y = (y or 15) + 1
				}
				z = (z or 15) + 1
			}
			x = (x or 15) + 1
		}
		return false
	}

	fun isStance(view: CoarseVoxelView, stance: Stance): Boolean {
		if (stance.y !in view.simulableStanceY) return false
		val support = view.voxel(stance.x, stance.y - 1, stance.z)
		val stands = view.standingSurface(stance.x, stance.y - 1, stance.z) != null &&
				!support.intrudesAbove &&
				view.voxel(stance.x, stance.y, stance.z).centerPassable &&
				view.voxel(stance.x, stance.y + 1, stance.z).centerPassable
		return stands || catalog.movements.any { it.occupies(view, stance) }
	}

	fun templates(view: CoarseVoxelView, at: Stance): List<MotionTemplate> =
		if (bouncesAt(view, at.x, at.y, at.z)) templates else templatesWithoutBounces

	internal val hasBounces: Boolean get() = bounceDepth > 0

	internal fun bouncesAt(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean =
		bounceDepth > 0 && view.mayBounceWithin(x - bounceReach, y - bounceDepth, z - bounceReach, x + bounceReach, y, z + bounceReach)


	fun heuristic(from: Stance, to: Stance): Double = heuristic(from.x, from.y, from.z, to.x, to.y, to.z)

	fun heuristic(fromX: Int, fromY: Int, fromZ: Int, toX: Int, toY: Int, toZ: Int): Double =
		heuristicCaps.estimate(toX - fromX, toY - fromY, toZ - fromZ)

	internal fun originColumnRanges(chunk: PathingChunk) = columnRanges(chunk, -maxReadX, -minReadX, -maxReadZ, -minReadZ)

	internal fun targetColumnRanges(chunk: PathingChunk) = columnRanges(chunk, minTargetX, maxTargetX, minTargetZ, maxTargetZ)

	private fun columnRanges(chunk: PathingChunk, lowX: Int, highX: Int, lowZ: Int, highZ: Int): Pair<IntRange, IntRange> {
		val chunkMinX = chunk.x shl 4
		val chunkMinZ = chunk.z shl 4
		return ((chunkMinX + lowX)..(chunkMinX + 15 + highX)) to ((chunkMinZ + lowZ)..(chunkMinZ + 15 + highZ))
	}

	companion object {

		fun build(costs: CoarseMoveCosts, options: SimpleMoveOptions = SimpleMoveOptions()): SimpleMoveLibrary =
			SimpleMoveLibrary(MovementCatalog.build(costs, options))

		private fun sustainedRate(templates: List<MotionTemplate>): Double = templates
			.filter { it.dy == 0 && abs(it.dx) + abs(it.dz) == 1 }
			.minOfOrNull { it.lowerBoundTicks }
			?: 1.0
	}
}
