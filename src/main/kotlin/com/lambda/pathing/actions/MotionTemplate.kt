package com.lambda.pathing.actions

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.BounceArcProbe
import com.lambda.pathing.launch.JumpArcProbe
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.canStandAt
import kotlin.math.abs
import kotlin.math.roundToInt

class MotionTemplate internal constructor(
	val id: MotionTemplateId,
	val dx: Int,
	val dy: Int,
	val dz: Int,
	val movement: MovementId,
	val lowerBoundTicks: Double,
	private val conditions: List<CellCondition>,
	private val arc: ArcSpec? = null,
	private val strideCost: Double? = null,
) {

	data class ArcSpec(
		val modes: List<LaunchMode> = LaunchMode.entries,
		val bounceDrop: Int? = null,

		val riseAdmission: ((Double) -> Boolean)? = null,
	)

	init {
		require(dx != 0 || dy != 0 || dz != 0) { "Motion template cannot be stationary" }
		require(lowerBoundTicks.isFinite() && lowerBoundTicks > 0.0) {
			"Template lower bound must be finite and positive: $lowerBoundTicks"
		}
	}

	fun target(origin: Stance): Stance = origin.offset(dx, dy, dz)

	internal val minimumTicks: Double = minOf(lowerBoundTicks, strideCost ?: lowerBoundTicks)

	private fun bounceEdge(
		view: CoarseVoxelView,
		origin: Stance,
		drop: Int,
		launchOffset: Double,
	): CoarseEdge? {
		val probed = BounceArcProbe.probe(
			view, origin, dx, dz, drop, dy,
			launchHeight = origin.y + launchOffset,
		) ?: return null

		return CoarseEdge(
			id = CoarseEdgeId(id, origin),
			from = origin,
			to = target(origin),
			movement = movement,
			lowerBoundTicks = lowerBoundTicks,
			readSet = LazyReadSet {
				buildSet {
					readOffsets.forEach { add(VoxelPos(origin.x + it.x, origin.y + it.y, origin.z + it.z)) }
					addAll(probed.reads)
				}
			},
			bounce = probed.solution,
		)
	}

	private fun surfaceOffset(view: CoarseVoxelView, stance: Stance): Double =
		view.surfaceOffset(stance.x, stance.y - 1, stance.z)

	private fun realRise(view: CoarseVoxelView, origin: Stance): Double =
		dy + surfaceOffset(view, target(origin)) - surfaceOffset(view, origin)

	internal fun matches(view: CoarseVoxelView, origin: Stance): Boolean =
		conditions.all { it.matches(view, origin.x, origin.y, origin.z) }

	internal fun edge(view: CoarseVoxelView, origin: Stance): CoarseEdge? {
		if (!matches(view, origin)) return null
		val probed = arc?.let { spec ->

			val launchOffset = surfaceOffset(view, origin)
			val landingOffset = surfaceOffset(view, target(origin))
			spec.bounceDrop?.let { drop ->
				return bounceEdge(view, origin, drop, launchOffset)
			}
			val riseHeight = dy + landingOffset - launchOffset
			spec.riseAdmission?.let { admits -> if (!admits(riseHeight)) return null }
			JumpArcProbe.probe(
				view, origin, dx, dz, dy, modes = spec.modes,
				riseHeight = riseHeight,
				launchHeight = origin.y + launchOffset,
			) ?: return null
		}

		val ticks = if (strideCost != null && realRise(view, origin) <= CoarseMoveRates.FREE_STEP_RISE) {
			strideCost
		} else {
			lowerBoundTicks
		}

		val spec = arc
		val standingStart = spec == null || probed == null ||
				standingStartViable(view, origin, spec, probed.solution)

		return CoarseEdge(
			id = CoarseEdgeId(id, origin),
			from = origin,
			to = target(origin),
			movement = movement,
			lowerBoundTicks = ticks,
			readSet = LazyReadSet {
				buildSet {
					readOffsets.forEach { add(VoxelPos(origin.x + it.x, origin.y + it.y, origin.z + it.z)) }
					probed?.let { addAll(it.reads) }
				}
			},
			launch = probed?.solution,
			standingStart = standingStart,
		)
	}

	private fun standingStartViable(
		view: CoarseVoxelView,
		origin: Stance,
		spec: ArcSpec,
		solution: LaunchSolution,
	): Boolean {
		val profile = BallisticProfile.VANILLA
		val behindX = origin.x - Integer.signum(dx)
		val behindZ = origin.z - Integer.signum(dz)
		val behind = view.canStandAt(behindX, origin.y, behindZ)
		val runUp = IN_CELL_RUN_UP + if (behind) 1.0 else 0.0
		fun reach(sprint: Boolean): Double =
			profile.runUpSpeed(0.0, profile.groundRunUpTicks(0.0, runUp, sprint, STANDING_RUN_UP_TICKS), sprint)

		if (solution.speed - solution.speedSlack <= reach(solution.mode.sprint)) return true
		return LaunchSolver.best(
			origin, target(origin), profile, spec.modes,
			maxEntrySpeed = { reach(it.sprint) },
			rise = realRise(view, origin),
		) != null
	}

	internal val readOffsets: List<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
		buildList {
			addAll(ORIGIN_STANCE_READS)
			conditions.forEach { addAll(it.reads()) }

			arc?.let { spec ->

				val steps = maxOf(abs(dx), abs(dz))
				for (step in 0..steps) {
					val alongX = if (steps == 0) 0 else (dx.toDouble() * step / steps).roundToInt()
					val alongZ = if (steps == 0) 0 else (dz.toDouble() * step / steps).roundToInt()
					val floorReach = minOf(dy, -(spec.bounceDrop ?: 0), 0) - 2
					for (y in floorReach..ARC_READ_CEILING) {
						for (ox in -1..1) {
							for (oz in -1..1) {
								add(VoxelPos(alongX + ox, y, alongZ + oz))
							}
						}
					}
				}
			}
		}
	}

	private companion object {

		const val IN_CELL_RUN_UP = 0.7

		const val STANDING_RUN_UP_TICKS = 24

		val ORIGIN_STANCE_READS = listOf(
			VoxelPos(0, -1, 0),
			VoxelPos(0, 0, 0),
			VoxelPos(0, 1, 0),
		)

		const val ARC_READ_CEILING = 4
	}
}
