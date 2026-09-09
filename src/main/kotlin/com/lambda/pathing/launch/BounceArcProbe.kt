package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxelView
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

object BounceArcProbe {

	class Reachable(
		val solution: BounceSolution,
		readsSupplier: () -> LongOpenHashSet,
	) {

		val reads: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
			JumpArcProbe.unpackReads(readsSupplier())
		}
	}

	fun probe(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		drop: Int,
		rise: Int,
		profile: BallisticProfile = BallisticProfile.VANILLA,
		launchHeight: Double = from.y.toDouble(),
	): Reachable? {
		val solution = solve(view, from, dx, dz, drop, rise, profile, launchHeight, reads = null)
			?: return null
		return Reachable(solution) {
			LongOpenHashSet().also {
				solve(view, from, dx, dz, drop, rise, profile, launchHeight, reads = it)
			}
		}
	}

	private fun solve(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		drop: Int,
		rise: Int,
		profile: BallisticProfile,
		launchHeight: Double,
		reads: LongOpenHashSet?,
	): BounceSolution? {
		val length = hypot(dx.toDouble(), dz.toDouble())
		if (length <= 0.0) return null

		val unitX = dx / length
		val unitZ = dz / length
		val padY = from.y - drop - 1

		var anyBouncy = false
		var along = 1.0
		while (along < length + 0.5) {
			if (bouncyCell(
					view, floor(from.x + 0.5 + unitX * along).toInt(), padY,
					floor(from.z + 0.5 + unitZ * along).toInt(), reads
				)
			) {
				anyBouncy = true
				break
			}
			along += 1.0
		}
		if (!anyBouncy) return null

		val launchOffset = launchHeight - from.y
		reads?.add(BlockPos.asLong(from.x + dx, from.y + rise - 1, from.z + dz))
		val landingOffset = view.surfaceOffset(from.x + dx, from.y + rise - 1, from.z + dz)
		val riseHeight = rise + landingOffset - launchOffset

		var ceilingBottom = Double.POSITIVE_INFINITY
		run {
			val feetCell = floor(launchHeight).toInt()
			var along = 0.0
			while (along <= minOf(length, LAUNCH_ASCENT_REACH)) {
				val x = floor(from.x + 0.5 + unitX * along).toInt()
				val z = floor(from.z + 0.5 + unitZ * along).toInt()
				for (y in feetCell + 2..feetCell + 4) {
					reads?.add(BlockPos.asLong(x, y, z))
					val v = view.voxel(x, y, z)
					if (!v.fullyPassable) {
						val bottom = y + (view.collisionShape(x, y, z)?.getMin(net.minecraft.util.math.Direction.Axis.Y) ?: 0.0)
						if (bottom < ceilingBottom) ceilingBottom = bottom
						break
					}
				}
				along += 1.0
			}
		}
		val headroom = if (ceilingBottom.isFinite()) ceilingBottom - BODY_HEIGHT - launchHeight else Double.POSITIVE_INFINITY

		val launchReach = launchReach(view, from, launchHeight, unitX, unitZ, reads)

		val cache = JumpArcProbe.SweepCellCache()

		fun contactPenalty(reach: Double): Double {
			val px = from.x + 0.5 + unitX * reach
			val pz = from.z + 0.5 + unitZ * reach
			val x = floor(px).toInt()
			val z = floor(pz).toInt()
			if (!bouncyCell(view, x, padY, z, reads)) return Double.POSITIVE_INFINITY
			return hypot(px - (x + 0.5), pz - (z + 0.5))
		}

		fun admissible(solution: BounceSolution): Boolean {
			if (!contactPenalty(solution.launchOffset + solution.contactDistance).isFinite()) return false
			val clearance = JumpArcProbe.sweepClearance(
				view, from, dx, dz, solution.arc,
				launchOffset = solution.launchOffset,
				launchHeight = launchHeight,
				reads = reads,
				cache = cache,
			) ?: return false
			return clearance >= 0.0
		}

		for ((jump, sprint) in BounceSolver.STANDING_STYLES) {
			val solution = solveRefined(view, from, dx, dz, length, drop, launchOffset, reads) { contactDepth ->
				BounceSolver.solveStanding(
					horizontalDistance = length, drop = drop, rise = rise,
					jump = jump, sprint = sprint, profile = profile,
					contactDepth = contactDepth, riseHeight = riseHeight,
					contactPenalty = ::contactPenalty,
					headroom = headroom,
					launchReach = launchReach,
				)
			} ?: continue
			if (!admissible(solution)) continue
			return solution
		}

		for ((jump, sprint, holdForward, holdTicks) in COMBOS) {
			val solution = solveRefined(view, from, dx, dz, length, drop, launchOffset, reads) { contactDepth ->
				BounceSolver.solve(
					horizontalDistance = length, drop = drop, rise = rise, profile = profile,
					sprint = sprint, holdForward = holdForward,
					jump = jump, holdTicks = holdTicks,
					contactDepth = contactDepth, riseHeight = riseHeight,
					headroom = headroom,
					launchReach = launchReach,
				)
			} ?: continue
			if (!admissible(solution)) continue
			return solution
		}
		return null
	}

	data class LaunchCombo(
		val jump: Boolean,
		val sprint: Boolean,
		val holdForward: Boolean,
		val holdTicks: Int = Int.MAX_VALUE,
	)

	private inline fun solveRefined(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		length: Double,
		drop: Int,
		launchOffset: Double,
		reads: LongOpenHashSet?,
		solveAtDepth: (Double) -> BounceSolution?,
	): BounceSolution? {
		var contactOffset = seedContactOffset(view, from, dx, dz, length, drop, reads)
		repeat(CONTACT_REFINEMENTS) {
			val solution = solveAtDepth(drop + launchOffset - contactOffset) ?: return null
			val observed = contactSurfaceOffset(view, from, dx, dz, length, drop, solution, reads)
			if (abs(observed - contactOffset) < CONTACT_CONVERGENCE) return solution
			contactOffset = observed
		}
		return null
	}

	private fun seedContactOffset(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		length: Double,
		drop: Int,
		reads: LongOpenHashSet?,
	): Double {
		val padY = from.y - drop - 1
		var along = 1.0
		while (along < length) {
			val x = floor(from.x + 0.5 + (dx / length) * along).toInt()
			val z = floor(from.z + 0.5 + (dz / length) * along).toInt()
			reads?.add(BlockPos.asLong(x, padY, z))
			if (view.voxel(x, padY, z).standingSurface != null) return view.surfaceOffset(x, padY, z)
			along += 1.0
		}
		return 0.0
	}

	private fun contactSurfaceOffset(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		length: Double,
		drop: Int,
		solution: BounceSolution,
		reads: LongOpenHashSet?,
	): Double {
		val reach = solution.launchOffset + solution.contactDistance
		val x = floor(from.x + 0.5 + (dx / length) * reach).toInt()
		val z = floor(from.z + 0.5 + (dz / length) * reach).toInt()
		val padY = from.y - drop - 1
		reads?.add(BlockPos.asLong(x, padY, z))
		return view.surfaceOffset(x, padY, z)
	}

	private fun bouncyCell(
		view: CoarseVoxelView,
		x: Int,
		padY: Int,
		z: Int,
		reads: LongOpenHashSet?,
	): Boolean {
		reads?.add(BlockPos.asLong(x, padY, z))
		val pad = view.voxel(x, padY, z)
		if (pad.bouncy) return true
		val surface = pad.standingSurface
		if (surface != null && surface <= THIN_COVER_SURFACE) {
			reads?.add(BlockPos.asLong(x, padY - 1, z))
			if (view.voxel(x, padY - 1, z).bouncy) return true
		}
		return false
	}

	private val PARTIAL_HOLDS = intArrayOf(20, 16, 12, 8, 5, 3, 2)

	private val COMBOS = buildList {
		for (jump in listOf(true, false)) {
			add(LaunchCombo(jump, sprint = true, holdForward = true))
			add(LaunchCombo(jump, sprint = false, holdForward = true))
			for (holdTicks in PARTIAL_HOLDS) {

				add(LaunchCombo(jump, sprint = true, holdForward = true, holdTicks = holdTicks))
				add(LaunchCombo(jump, sprint = false, holdForward = true, holdTicks = holdTicks))
			}
			add(LaunchCombo(jump, sprint = false, holdForward = false))
		}
	}

	private fun launchReach(
		view: CoarseVoxelView,
		from: Stance,
		launchHeight: Double,
		unitX: Double,
		unitZ: Double,
		reads: LongOpenHashSet?,
	): Double {
		val direct = view.voxel(from.x, from.y - 1, from.z).standingSurface != null
		val supportY = if (direct) from.y - 1 else from.y - 2
		reads?.add(BlockPos.asLong(from.x, supportY, from.z))
		val shape = view.collisionShape(from.x, supportY, from.z)
			?: return BounceSolver.LAUNCH_OFFSET
		val surfaceLocal = launchHeight - supportY
		var extent = Double.NEGATIVE_INFINITY
		for (box in shape.boundingBoxes) {
			if (box.maxY < surfaceLocal - SUPPORT_SURFACE_EPSILON) continue
			val alongX = maxOf((box.minX - 0.5) * unitX, (box.maxX - 0.5) * unitX)
			val alongZ = maxOf((box.minZ - 0.5) * unitZ, (box.maxZ - 0.5) * unitZ)
			extent = maxOf(extent, alongX + alongZ)
		}
		if (extent == Double.NEGATIVE_INFINITY) return BounceSolver.LAUNCH_OFFSET
		return (extent + BODY_HALF_WIDTH).coerceIn(BODY_HALF_WIDTH, BounceSolver.LAUNCH_OFFSET)
	}

	private const val BODY_HALF_WIDTH = Kinematics.BODY_HALF_WIDTH

	private const val SUPPORT_SURFACE_EPSILON = 1.0E-4

	private const val THIN_COVER_SURFACE = 0.2

	private const val BODY_HEIGHT = Kinematics.BODY_HEIGHT

	private const val LAUNCH_ASCENT_REACH = 3.5

	private const val CONTACT_REFINEMENTS = 3

	private const val CONTACT_CONVERGENCE = 1.0E-9
}
