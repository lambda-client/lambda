package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
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

class Probed<S>(val solution: S, readsSupplier: () -> LongOpenHashSet) {
	val reads: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
		val packed = readsSupplier()
		val result = HashSet<VoxelPos>(packed.size * 2)
		val iterator = packed.iterator()
		while (iterator.hasNext()) {
			val key = iterator.nextLong()
			result += VoxelPos(BlockPos.unpackLongX(key), BlockPos.unpackLongY(key), BlockPos.unpackLongZ(key))
		}
		result
	}
}

internal inline fun <S : Any> probed(crossinline solve: (LongOpenHashSet?) -> S?): Probed<S>? {
	val solution = solve(null) ?: return null
	return Probed(solution) { LongOpenHashSet().also { solve(it) } }
}

internal enum class Sweep { CLEAR, BLOCKED, PARTIAL }

internal class SweepCellCache {
	val classes: Long2ByteOpenHashMap = Long2ByteOpenHashMap().apply { defaultReturnValue(UNVISITED) }
	var shapes: Long2ObjectOpenHashMap<VoxelShape>? = null

	companion object {
		const val UNVISITED: Byte = -1
	}
}

internal fun sweepArc(
	view: CoarseVoxelView,
	from: Stance,
	dx: Int,
	dz: Int,
	arc: ArcSample,
	launchOffset: Double,
	launchHeight: Double,
	reads: LongOpenHashSet?,
	cache: SweepCellCache,

	lateralOffset: Double = 0.0,
	halfWidth: Double = CORE_HALF_WIDTH,
): Sweep {
	val length = hypot(dx.toDouble(), dz.toDouble())
	if (length <= 0.0) return Sweep.BLOCKED
	val unitX = dx / length
	val unitZ = dz / length
	val launchX = from.x + 0.5 + unitX * launchOffset - unitZ * lateralOffset
	val launchZ = from.z + 0.5 + unitZ * launchOffset + unitX * lateralOffset

	val heights = arc.heights
	val distances = arc.distances

	var previous = coreBox(launchX, launchHeight, launchZ, halfWidth)
	for (index in heights.indices) {
		val along = distances[index]
		val box = coreBox(
			launchX + unitX * along,
			launchHeight + heights[index],
			launchZ + unitZ * along,
			halfWidth,
		)
		val swept = previous.union(box)
		previous = box

		val margin = swept.expand(SWEEP_MARGIN)
		for (y in MathHelper.floor(margin.minY)..MathHelper.floor(margin.maxY)) {
			for (z in MathHelper.floor(margin.minZ)..MathHelper.floor(margin.maxZ)) {
				for (x in MathHelper.floor(margin.minX)..MathHelper.floor(margin.maxX)) {
					val key = BlockPos.asLong(x, y, z)
					reads?.add(key)

					val known = cache.classes.get(key)
					val cellClass = if (known == SweepCellCache.UNVISITED) {
						view.collisionClass(x, y, z).also { cache.classes.put(key, it.ordinal.toByte()) }
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
							val shapes = cache.shapes
								?: Long2ObjectOpenHashMap<VoxelShape>().also { cache.shapes = it }
							val shape = shapes.get(key)
								?: view.collisionShape(x, y, z)?.also { shapes.put(key, it) }
								?: return Sweep.BLOCKED
							for (bounds in shape.boundingBoxes) {
								if (bounds.offset(x.toDouble(), y.toDouble(), z.toDouble()).intersects(swept)) return Sweep.PARTIAL
							}
						}
					}
				}
			}
		}
	}
	return Sweep.CLEAR
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
