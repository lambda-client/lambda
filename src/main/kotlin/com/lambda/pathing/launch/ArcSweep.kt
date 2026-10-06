package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.shape.VoxelShape
import kotlin.math.hypot

class Probed<S>(val solution: S, val reads: () -> LongOpenHashSet)

internal inline fun <S : Any> probed(crossinline solve: (LongOpenHashSet?) -> S?): Probed<S>? {
	val solution = solve(null) ?: return null
	return Probed(solution) { LongOpenHashSet().also { solve(it) } }
}

internal enum class Sweep { CLEAR, BLOCKED, PARTIAL }

internal class ArcSweep(
	private val view: CoarseVoxelView,
	private val from: Stance,
	dx: Int,
	dz: Int,
	private val reads: LongOpenHashSet?,
) {
	private val length = hypot(dx.toDouble(), dz.toDouble())
	private val unitX = dx / length
	private val unitZ = dz / length
	private val classes = Long2ByteOpenHashMap().apply { defaultReturnValue(UNVISITED) }
	private var shapes: Long2ObjectOpenHashMap<VoxelShape>? = null

	fun sweep(
		arc: ArcSample,
		launchOffset: Double,
		launchHeight: Double,
		lateralOffset: Double = 0.0,
		halfWidth: Double = CORE_HALF_WIDTH,
	): Sweep {
		val launchX = from.x + 0.5 + unitX * launchOffset - unitZ * lateralOffset
		val launchZ = from.z + 0.5 + unitZ * launchOffset + unitX * lateralOffset
		forEachSweptCell(arc, launchX, launchHeight, launchZ, unitX, unitZ, halfWidth) { x, y, z, swept ->
			val key = BlockPos.asLong(x, y, z)
			reads?.add(key)

			val known = classes.get(key)
			val cellClass = if (known == UNVISITED) {
				view.collisionClass(x, y, z).also { classes.put(key, it.ordinal.toByte()) }
			} else {
				COLLISION_CLASSES[known.toInt()]
			}
			when (cellClass) {
				CollisionClass.EMPTY -> {}
				CollisionClass.UNKNOWN -> return Sweep.BLOCKED
				CollisionClass.FULL ->
					if (swept.intersects(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0)) {
						return Sweep.BLOCKED
					}
				CollisionClass.PARTIAL -> {
					val cached = shapes ?: Long2ObjectOpenHashMap<VoxelShape>().also { shapes = it }
					val shape = cached.get(key)
						?: view.collisionShape(x, y, z)?.also { cached.put(key, it) }
						?: return Sweep.BLOCKED
					for (bounds in shape.boundingBoxes) {
						if (bounds.offset(x.toDouble(), y.toDouble(), z.toDouble()).intersects(swept)) return Sweep.PARTIAL
					}
				}
			}
		}
		return Sweep.CLEAR
	}

	private companion object {
		const val UNVISITED: Byte = -1
	}
}

internal inline fun forEachSweptCell(
	arc: ArcSample,
	launchX: Double,
	launchHeight: Double,
	launchZ: Double,
	unitX: Double,
	unitZ: Double,
	halfWidth: Double,
	visit: (x: Int, y: Int, z: Int, swept: Box) -> Unit,
) {
	val heights = arc.heights
	val distances = arc.distances
	var previous = coreBox(launchX, launchHeight, launchZ, halfWidth)
	for (index in heights.indices) {
		val along = distances[index]
		val box = coreBox(launchX + unitX * along, launchHeight + heights[index], launchZ + unitZ * along, halfWidth)
		val swept = previous.union(box)
		previous = box
		val margin = swept.expand(SWEEP_MARGIN)
		for (y in MathHelper.floor(margin.minY)..MathHelper.floor(margin.maxY)) {
			for (z in MathHelper.floor(margin.minZ)..MathHelper.floor(margin.maxZ)) {
				for (x in MathHelper.floor(margin.minX)..MathHelper.floor(margin.maxX)) visit(x, y, z, swept)
			}
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
private val COLLISION_CLASSES = CollisionClass.entries.toTypedArray()
