package com.lambda.pathing.launch

import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.Medium
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import kotlin.math.hypot

class Probed<S>(val solution: S, val reads: () -> LongOpenHashSet)

internal inline fun <S : Any> probed(crossinline solve: (LongOpenHashSet?) -> S?): Probed<S>? {
	val solution = solve(null) ?: return null
	return Probed(solution) { LongOpenHashSet().also { solve(it) } }
}

internal sealed interface Sweep {
	data object Clear : Sweep
	data object Blocked : Sweep
	data object Partial : Sweep
	class Clipped(val x: Int, val z: Int) : Sweep
}

internal class ArcPath(
	private val launchX: Double,
	private val launchZ: Double,
	private val unitX: Double,
	private val unitZ: Double,
	val bendAt: Double = Double.POSITIVE_INFINITY,
	private val bendX: Double = launchX,
	private val bendZ: Double = launchZ,
	private val turnedX: Double = unitX,
	private val turnedZ: Double = unitZ,
) {
	fun x(distance: Double): Double = if (distance <= bendAt) launchX + unitX * distance else bendX + turnedX * (distance - bendAt)

	fun z(distance: Double): Double = if (distance <= bendAt) launchZ + unitZ * distance else bendZ + turnedZ * (distance - bendAt)

	fun offset(x: Double, z: Double) = ArcPath(launchX + x, launchZ + z, unitX, unitZ, bendAt, bendX + x, bendZ + z, turnedX, turnedZ)

	companion object {
		fun through(originX: Double, originZ: Double, viaX: Double, viaZ: Double, toX: Double, toZ: Double, launchOffset: Double): ArcPath? {
			val legX = viaX - originX
			val legZ = viaZ - originZ
			val leg = hypot(legX, legZ)
			val turnX = toX - viaX
			val turnZ = toZ - viaZ
			val turn = hypot(turnX, turnZ)
			if (leg <= DEGENERATE_LENGTH || turn <= DEGENERATE_LENGTH) return null
			if (launchOffset >= leg) {
				val past = launchOffset - leg
				return ArcPath(viaX + turnX / turn * past, viaZ + turnZ / turn * past, turnX / turn, turnZ / turn)
			}
			return ArcPath(
				originX + legX / leg * launchOffset, originZ + legZ / leg * launchOffset, legX / leg, legZ / leg,
				bendAt = leg - launchOffset, bendX = viaX, bendZ = viaZ, turnedX = turnX / turn, turnedZ = turnZ / turn,
			)
		}
	}
}

internal class ArcSweep(
	private val view: CoarseVoxelView,
	private val from: Stance,
	private val dx: Int,
	private val dz: Int,
	private val reads: LongOpenHashSet?,
) {
	private val length = hypot(dx.toDouble(), dz.toDouble())
	private val unitX = dx / length
	private val unitZ = dz / length

	fun sweep(
		arc: ArcSample,
		launchOffset: Double,
		launchHeight: Double,
		lateralOffset: Double = 0.0,
		halfWidth: Double = CORE_HALF_WIDTH,
	): Sweep = sweep(
		arc,
		ArcPath(
			from.x + 0.5 + unitX * launchOffset - unitZ * lateralOffset,
			from.z + 0.5 + unitZ * launchOffset + unitX * lateralOffset,
			unitX, unitZ,
		),
		launchHeight, halfWidth,
	)

	fun sweep(arc: ArcSample, path: ArcPath, launchHeight: Double, halfWidth: Double = CORE_HALF_WIDTH): Sweep {
		forEachSweptCell(arc, path, launchHeight, halfWidth) { x, y, z, swept ->
			reads?.add(BlockPos.asLong(x, y, z))
			when (view.collisionClass(x, y, z)) {
				CollisionClass.EMPTY -> {}
				CollisionClass.UNKNOWN -> return Sweep.Blocked
				CollisionClass.FULL ->
					if (swept.intersects(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0)) {
						return Sweep.Clipped(x, z)
					}
				CollisionClass.PARTIAL -> {
					val shape = view.collisionShape(x, y, z) ?: return Sweep.Blocked
					for (bounds in shape.boundingBoxes) {
						if (bounds.offset(x.toDouble(), y.toDouble(), z.toDouble()).intersects(swept)) return Sweep.Partial
					}
				}
			}
		}
		return if (grips(arc, path, launchHeight)) Sweep.Blocked else Sweep.Clear
	}

	private fun grips(arc: ArcSample, path: ArcPath, launchHeight: Double): Boolean {
		for (index in -1 until arc.heights.size) {
			val along = if (index < 0) 0.0 else arc.distances[index]
			val x = MathHelper.floor(path.x(along))
			val y = MathHelper.floor(launchHeight + if (index < 0) 0.0 else arc.heights[index])
			val z = MathHelper.floor(path.z(along))
			if (x == from.x + dx && z == from.z + dz) continue
			reads?.add(BlockPos.asLong(x, y, z))
			if (view.medium(x, y, z) == Medium.CLIMBABLE) return true
		}
		return false
	}
}

internal inline fun forEachSweptCell(
	arc: ArcSample,
	path: ArcPath,
	launchHeight: Double,
	halfWidth: Double,
	visit: (x: Int, y: Int, z: Int, swept: Box) -> Unit,
) {
	val heights = arc.heights
	val distances = arc.distances
	var previous = coreBox(path.x(0.0), launchHeight, path.z(0.0), halfWidth)
	var previousAlong = 0.0
	var previousHeight = 0.0
	for (index in heights.indices) {
		val along = distances[index]
		val height = heights[index]
		if (previousAlong < path.bendAt && path.bendAt < along) {
			val bendHeight = previousHeight + (height - previousHeight) * (path.bendAt - previousAlong) / (along - previousAlong)
			val bend = coreBox(path.x(path.bendAt), launchHeight + bendHeight, path.z(path.bendAt), halfWidth)
			visitSwept(previous.union(bend), visit)
			previous = bend
		}
		val box = coreBox(path.x(along), launchHeight + height, path.z(along), halfWidth)
		visitSwept(previous.union(box), visit)
		previous = box
		previousAlong = along
		previousHeight = height
	}
}

internal inline fun visitSwept(swept: Box, visit: (x: Int, y: Int, z: Int, swept: Box) -> Unit) {
	val margin = swept.expand(SWEEP_MARGIN)
	for (y in MathHelper.floor(margin.minY)..MathHelper.floor(margin.maxY)) {
		for (z in MathHelper.floor(margin.minZ)..MathHelper.floor(margin.maxZ)) {
			for (x in MathHelper.floor(margin.minX)..MathHelper.floor(margin.maxX)) visit(x, y, z, swept)
		}
	}
}

internal fun coreBox(x: Double, y: Double, z: Double, halfWidth: Double = CORE_HALF_WIDTH) = Box(
	x - halfWidth, y, z - halfWidth,
	x + halfWidth, y + BODY_HEIGHT, z + halfWidth,
)

internal const val CORE_HALF_WIDTH = 0.2
internal const val SWEEP_MARGIN = 0.5
internal const val BODY_HEIGHT = Vanilla.PLAYER_HEIGHT.toDouble()
internal const val BODY_HALF_WIDTH = Vanilla.PLAYER_HALF_WIDTH.toDouble()
internal const val CORNER_CLEARANCE = BODY_HALF_WIDTH + 0.1
