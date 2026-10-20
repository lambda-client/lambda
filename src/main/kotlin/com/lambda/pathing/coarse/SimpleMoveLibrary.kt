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
		return Footing.standsAt(view, stance) || catalog.movements.any { it.occupies(view, stance) }
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

	private val fullReach = ReadReach.of(templates)!!

	private val groundReach = ReadReach.of(templates.filter { it.movement != MovementId.BOUNCE && it.movement != MovementId.HONEY_SLIDE })

	private val honeyReach = ReadReach.of(templates.filter { it.movement == MovementId.HONEY_SLIDE })

	private val padReach = ReadReach.of(templates.filter { it.movement == MovementId.BOUNCE })

	internal fun originsReading(changed: CellBox, view: CoarseVoxelView): CellBox = reach(changed, view) { it.origins(changed) }

	internal fun targetsReading(changed: CellBox, view: CoarseVoxelView): CellBox = reach(changed, view) { it.targets(changed) }

	private inline fun reach(changed: CellBox, view: CoarseVoxelView, box: (ReadReach) -> CellBox): CellBox {
		var reached = box(groundReach ?: return box(fullReach))
		if (honeyReach != null && honeyReach.zone(changed).let { view.mayContainHoney(it.xs.first, it.ys.first, it.zs.first, it.xs.last, it.ys.last, it.zs.last) }) {
			reached = CellBox.enclosing(listOf(reached, box(honeyReach)))
		}
		if (padReach != null && padReach.zone(changed).grown(-bounceReach, bounceReach, -bounceDepth, 0, -bounceReach, bounceReach)
				.let { view.mayBounceWithin(it.xs.first, it.ys.first, it.zs.first, it.xs.last, it.ys.last, it.zs.last) }) {
			reached = CellBox.enclosing(listOf(reached, box(padReach)))
		}
		return reached
	}

	private class ReadReach(
		val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val minZ: Int, val maxZ: Int,
		val minTargetX: Int, val maxTargetX: Int, val minTargetY: Int, val maxTargetY: Int, val minTargetZ: Int, val maxTargetZ: Int,
	) {
		fun origins(changed: CellBox): CellBox = changed.grown(-maxX, -minX, -maxY, -minY, -maxZ, -minZ)

		fun targets(changed: CellBox): CellBox = changed.grown(minTargetX, maxTargetX, minTargetY, maxTargetY, minTargetZ, maxTargetZ)

		fun zone(changed: CellBox): CellBox = origins(changed).grown(minX, maxX, minY, maxY, minZ, maxZ)

		companion object {
			fun of(templates: List<MotionTemplate>): ReadReach? = if (templates.isEmpty()) null else ReadReach(
				templates.minOf { it.readBounds[0] }, templates.maxOf { it.readBounds[3] },
				minOf(templates.minOf { it.readBounds[1] }, -1), maxOf(templates.maxOf { it.readBounds[4] }, 1),
				templates.minOf { it.readBounds[2] }, templates.maxOf { it.readBounds[5] },
				minOf(templates.minOf { it.dx - it.readBounds[3] }, 0), maxOf(templates.maxOf { it.dx - it.readBounds[0] }, 0),
				minOf(templates.minOf { it.dy - it.readBounds[4] }, -1), maxOf(templates.maxOf { it.dy - it.readBounds[1] }, 1),
				minOf(templates.minOf { it.dz - it.readBounds[5] }, 0), maxOf(templates.maxOf { it.dz - it.readBounds[2] }, 0),
			)
		}
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
