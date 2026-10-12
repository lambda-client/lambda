package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseMoveCosts
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.families.MovementCatalog
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Footing
import kotlin.math.abs

class SimpleMoveLibrary private constructor(val catalog: MovementCatalog) {
	val templates: List<MotionTemplate> = catalog.templates
	internal val stampPrograms by lazy(LazyThreadSafetyMode.PUBLICATION) { templates.map { it.stampProgram() } }
	internal val stampLayout by lazy(LazyThreadSafetyMode.PUBLICATION) { StampLayout(this) }
	internal val templatesWithoutBounces = templates.filter { it.movement != MovementId.BOUNCE }
	private val bounceReach = templates.maxOfOrNull { if (it.bounceDrop == null) 0 else 2 * maxOf(abs(it.dx), abs(it.dz)) + 1 } ?: 0
	private val bounceDepth = templates.maxOfOrNull { it.bounceDrop?.plus(2) ?: 0 } ?: 0
	internal val groups = TemplateGroups(templates)
	internal val bounceIndex by lazy(LazyThreadSafetyMode.PUBLICATION) { BounceIndex(templates.filter { it.movement == MovementId.BOUNCE }) }
	private val heuristicCaps = HeuristicCaps.of(templates)
	val sustainedTicksPerBlock: Double = sustainedRate(templates)

	private val minReadX = templates.minOf { it.readBounds[0] }
	private val maxReadX = templates.maxOf { it.readBounds[3] }
	private val minReadZ = templates.minOf { it.readBounds[2] }
	private val maxReadZ = templates.maxOf { it.readBounds[5] }
	private val minReadY = minOf(templates.minOf { it.readBounds[1] }, -1)
	private val maxReadY = maxOf(templates.maxOf { it.readBounds[4] }, 1)

	private val minTargetX = minOf(templates.minOf { it.dx - it.readBounds[3] }, 0)
	private val maxTargetX = maxOf(templates.maxOf { it.dx - it.readBounds[0] }, 0)
	private val minTargetY = minOf(templates.minOf { it.dy - it.readBounds[4] }, -1)
	private val maxTargetY = maxOf(templates.maxOf { it.dy - it.readBounds[1] }, 1)
	private val minTargetZ = minOf(templates.minOf { it.dz - it.readBounds[5] }, 0)
	private val maxTargetZ = maxOf(templates.maxOf { it.dz - it.readBounds[2] }, 0)

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
		val stands = if (Footing.involvesPartial(view, stance.x, stance.y, stance.z)) Footing.stands(view, stance)
		else view.standingSurface(stance.x, stance.y - 1, stance.z) != null && !view.voxel(stance.x, stance.y - 1, stance.z).intrudesAbove &&
				view.voxel(stance.x, stance.y, stance.z).centerPassable && view.voxel(stance.x, stance.y + 1, stance.z).centerPassable
		return stands || catalog.movements.any { it.occupies(view, stance) }
	}

	fun templates(view: CoarseVoxelView, at: Stance): List<MotionTemplate> =
		if (bouncesAt(view, at.x, at.y, at.z)) templates else templatesWithoutBounces

	internal val falls: Boolean = catalog.supports(MovementId.FALL)

	internal fun bouncesAt(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean =
		bounceDepth > 0 && view.mayBounceWithin(x - bounceReach, y - bounceDepth, z - bounceReach, x + bounceReach, y, z + bounceReach)


	fun heuristic(from: Stance, to: Stance): Double = heuristic(from.x, from.y, from.z, to.x, to.y, to.z)

	fun heuristic(fromX: Int, fromY: Int, fromZ: Int, toX: Int, toY: Int, toZ: Int): Double =
		heuristicCaps.estimate(toX - fromX, toY - fromY, toZ - fromZ)

	internal fun originsReading(changed: CellBox): CellBox = changed.grown(-maxReadX, -minReadX, -maxReadY, -minReadY, -maxReadZ, -minReadZ)

	internal fun targetsReading(changed: CellBox): CellBox =
		changed.grown(minTargetX, maxTargetX, minTargetY, maxTargetY, minTargetZ, maxTargetZ)

	companion object {

		fun build(costs: CoarseMoveCosts, options: SimpleMoveOptions = SimpleMoveOptions()): SimpleMoveLibrary =
			SimpleMoveLibrary(MovementCatalog.build(costs, options))

		private fun sustainedRate(templates: List<MotionTemplate>): Double = templates
			.filter { it.dy == 0 && abs(it.dx) + abs(it.dz) == 1 }
			.minOfOrNull { it.lowerBoundTicks }
			?: 1.0
	}
}
