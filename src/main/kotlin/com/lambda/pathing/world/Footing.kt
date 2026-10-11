package com.lambda.pathing.world

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.Vanilla
import it.unimi.dsi.fastutil.booleans.BooleanArrayList
import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Footing {
	const val WALK_RISE = Vanilla.STEP_HEIGHT

	const val JUMP_RISE = 1.25

	private const val FALL_DROP = 1.5

	private const val STEP = 1.0 / 16

	private const val HALF_WIDTH = Vanilla.PLAYER_HALF_WIDTH.toDouble()

	private const val HEIGHT = Vanilla.PLAYER_HEIGHT.toDouble()

	private const val MIN_SUPPORT = 0.02

	private const val CLIMB_REACH = 0.5

	private const val EPSILON = 1e-6

	private const val MARGIN = 5

	private const val CACHE_CAPACITY = 1 shl 15

	private const val WALK_RESULTS = 32

	fun involvesPartial(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean =
		view.voxel(x, y, z).partial || view.voxel(x, y + 1, z).partial || view.voxel(x, y - 1, z).partial || view.voxel(x, y - 2, z).partial

	fun stands(view: CoarseVoxelView, stance: Stance): Boolean {
		val cache = cache(view)
		cache.entries.get(BlockPos.asLong(stance.x, stance.y, stance.z))?.let { return !it.isEmpty }
		return floored(view, stance) && Patch.around(view, stance).anyFoothold(stance)
	}

	private fun cache(view: CoarseVoxelView): Cache {
		val revision = view.revision
		val cache = caches.get()
		if (cache.view.get() !== view || cache.revision.get() !== revision) cache.reset(view, revision)
		return cache
	}

	fun holds(view: CoarseVoxelView, stance: Stance): Holds {
		val revision = view.revision
		val cache = cache(view)
		val key = BlockPos.asLong(stance.x, stance.y, stance.z)
		cache.entries.getAndMoveToLast(key)?.let { return it }
		val holds = if (floored(view, stance)) Patch.around(view, stance).holds(stance) else Holds.EMPTY
		if (revision != null && known(view, stance)) {
			cache.entries.putAndMoveToLast(key, holds)
			if (cache.entries.size > CACHE_CAPACITY) cache.entries.removeFirst()
		}
		return holds
	}

	private fun floored(view: CoarseVoxelView, stance: Stance): Boolean {
		if (view.collisionClass(stance.x, stance.y, stance.z) == CollisionClass.FULL) return false
		val below = view.collisionClass(stance.x, stance.y - 1, stance.z)
		if (below != CollisionClass.EMPTY) return true
		if (view.collisionClass(stance.x, stance.y - 2, stance.z) != CollisionClass.PARTIAL) return false
		return view.collisionShape(stance.x, stance.y - 2, stance.z)?.getMax(Direction.Axis.Y)?.let { it > 1.0 } ?: true
	}

	inline fun forEachRead(stance: Stance, visit: (Int, Int, Int) -> Unit) {
		for (x in stance.x - 1..stance.x + 1) for (y in stance.y - 2..stance.y + 2) for (z in stance.z - 1..stance.z + 1) visit(x, y, z)
	}

	class Holds internal constructor(private val baseX: Int, private val baseZ: Int, private val levels: DoubleArray, private val masks: LongArray) {
		val isEmpty: Boolean get() = levels.isEmpty()

		fun at(x: Double, z: Double): Double? = heightAt(x, z).takeUnless { it.isNaN() }

		fun heightAt(x: Double, z: Double): Double {
			if (levels.isEmpty()) return Double.NaN
			val gx = (x / STEP).roundToInt() - baseX
			val gz = (z / STEP).roundToInt() - baseZ
			if (gx < 0 || gx >= SIDE || gz < 0 || gz >= SIDE) return Double.NaN
			val index = gz * SIDE + gx
			val bit = 1L shl index
			for (level in levels.indices) if (masks[level * WORDS + (index ushr 6)] and bit != 0L) return levels[level]
			return Double.NaN
		}

		internal val keys: LongArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
			val keys = LongArray(masks.sumOf { java.lang.Long.bitCount(it) })
			var next = 0
			forEach { gx, gz, _ -> keys[next++] = gridKey(gx, gz) }
			keys
		}

		fun nearest(x: Double, z: Double): HorizontalPoint? {
			var best: HorizontalPoint? = null
			var bestDistance = Double.MAX_VALUE
			forEach { gx, gz, height ->
				val distance = hypot(gx * STEP - x, gz * STEP - z)
				if (distance < bestDistance) {
					best = HorizontalPoint(gx * STEP, height, gz * STEP)
					bestDistance = distance
				}
			}
			return best
		}

		private inline fun forEach(visit: (gx: Int, gz: Int, height: Double) -> Unit) {
			for (level in levels.indices) for (word in 0 until WORDS) {
				var bits = masks[level * WORDS + word]
				while (bits != 0L) {
					val index = (word shl 6) + java.lang.Long.numberOfTrailingZeros(bits)
					bits = bits and (bits - 1)
					visit(baseX + index % SIDE, baseZ + index / SIDE, levels[level])
				}
			}
		}

		internal companion object {
			const val SIDE = 16 + 2 * MARGIN + 1

			const val WORDS = (SIDE * SIDE + 63) / 64

			val EMPTY = Holds(0, 0, DoubleArray(0), LongArray(0))
		}
	}

	private fun gridKey(gridX: Int, gridZ: Int): Long = gridX.toLong() shl 32 or (gridZ.toLong() and 0xFFFFFFFFL)

	private fun known(view: CoarseVoxelView, stance: Stance): Boolean {
		for (x in intArrayOf(stance.x - 1, stance.x + 1)) for (y in intArrayOf(stance.y - 2, stance.y + 2)) for (z in intArrayOf(stance.z - 1, stance.z + 1)) {
			if (!view.isKnown(x, y, z)) return false
		}
		return true
	}

	private class Cache {
		var view = WeakReference<CoarseVoxelView>(null)
		var revision = WeakReference<Any>(null)
		val entries = Long2ObjectLinkedOpenHashMap<Holds>()
		val walks = Long2LongOpenHashMap()
		val climbs = Long2LongOpenHashMap()

		fun reset(view: CoarseVoxelView, revision: Any?) {
			entries.clear()
			walks.clear()
			climbs.clear()
			this.view = WeakReference(view)
			this.revision = WeakReference(revision)
		}
	}

	private val caches = ThreadLocal.withInitial(::Cache)

	fun walkable(view: CoarseVoxelView, from: Stance, to: Stance, maxRise: Double): Boolean {
		val dx = to.x - from.x
		val dy = to.y - from.y
		val dz = to.z - from.z
		val walks = cache(view).let { if (maxRise == WALK_RISE) it.walks else if (maxRise == JUMP_RISE) it.climbs else null }
		if (walks == null || abs(dx) > 1 || abs(dy) > 1 || abs(dz) > 1 || view.revision == null) return search(view, from, to, maxRise)
		val code = (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1)
		val key = BlockPos.asLong(from.x, from.y, from.z)
		val memo = walks.get(key)
		if (memo and (1L shl code) != 0L) return memo and (1L shl (code + WALK_RESULTS)) != 0L
		val walkable = search(view, from, to, maxRise)
		if (known(view, from) && known(view, to)) {
			if (walks.size > CACHE_CAPACITY) walks.clear()
			walks.put(key, walks.get(key) or (1L shl code) or (if (walkable) 1L shl (code + WALK_RESULTS) else 0L))
		}
		return walkable
	}

	private fun search(view: CoarseVoxelView, from: Stance, to: Stance, maxRise: Double): Boolean {
		val start = holds(view, from).keys
		if (start.isEmpty()) return false
		val goal = holds(view, to).keys
		if (goal.isEmpty()) return false
		val patch = Patch.spanning(view, from, to)
		return patch.search(patch.indices(start), maxRise, patch.mask(goal)) != null
	}

	fun climbable(view: CoarseVoxelView, from: Stance, to: Stance): Boolean {
		val lower = holds(view, from).keys
		val upper = holds(view, to).keys
		if (lower.isEmpty() || upper.isEmpty()) return false
		val patch = Patch.spanning(view, from, to)
		val bottoms = patch.indices(lower)
		val tops = patch.indices(upper)
		for (i in 0 until tops.size) for (j in 0 until bottoms.size) if (patch.climbs(bottoms.getInt(j), tops.getInt(i))) return true
		return false
	}

	fun route(view: CoarseVoxelView, from: HorizontalPoint, to: Stance, maxRise: Double): List<HorizontalPoint>? {
		val goal = holds(view, to).keys
		if (goal.isEmpty()) return null
		val origin = Stance(floor(from.x).toInt(), floor(from.y - EPSILON).toInt() + 1, floor(from.z).toInt())
		val patch = Patch.spanning(view, origin, to)
		val start = patch.nearestValid(from) ?: return null
		val path = patch.search(IntArrayList.of(start), maxRise, patch.mask(goal)) ?: return null
		return patch.simplify(path).map(patch::point)
	}

	fun spot(view: CoarseVoxelView, stance: Stance, near: HorizontalPoint? = null): HorizontalPoint? =
		holds(view, stance).nearest(near?.x ?: (stance.x + 0.5), near?.z ?: (stance.z + 0.5))

	private class Patch(view: CoarseVoxelView, cellX0: Int, cellX1: Int, cellZ0: Int, cellZ1: Int, low: Int, high: Int) {
		private val kx0 = cellX0 * 16 - MARGIN
		private val kz0 = cellZ0 * 16 - MARGIN
		private val nx = (cellX1 + 1) * 16 + MARGIN - kx0 + 1
		private val nz = (cellZ1 + 1) * 16 + MARGIN - kz0 + 1
		private val boxes = Boxes(view, cellX0 - 1, cellX1 + 1, low - 2, high + 2, cellZ0 - 1, cellZ1 + 1)
		private val floorFeet = low - 1.0
		private val ceilingFeet = high.toDouble()
		private val heights = DoubleArray(nx * nz) { UNCOMPUTED }

		private fun feet(index: Int): Double {
			val height = heights[index]
			if (height != UNCOMPUTED) return height
			return boxes.standingHeight(x(index), z(index), floorFeet, ceilingFeet).also { heights[index] = it }
		}

		fun x(index: Int): Double = (kx0 + index % nx) * STEP

		fun z(index: Int): Double = (kz0 + index / nx) * STEP

		fun point(index: Int): HorizontalPoint = HorizontalPoint(x(index), feet(index), z(index))

		fun anyFoothold(stance: Stance): Boolean {
			for (index in CENTER_OUT) if (holdsAt(index, stance)) return true
			return false
		}

		private fun holdsAt(index: Int, stance: Stance): Boolean {
			val height = feet(index)
			return !height.isNaN() && height > stance.y - 1.0 + EPSILON && height <= stance.y + EPSILON &&
					boxes.supportsFrom(x(index), z(index), height, stance.x, stance.z)
		}

		private fun footholds(stance: Stance): IntArrayList {
			val own = BooleanArray(nx * nz) { holdsAt(it, stance) }
			val labels = BooleanArray(nx * nz)
			var best = IntArrayList()
			var bestDistance = Double.MAX_VALUE
			for (seed in own.indices) {
				if (!own[seed] || labels[seed]) continue
				val held = IntArrayList()
				var nearest = Double.MAX_VALUE
				val members = flood(seed, labels)
				for (i in 0 until members.size) {
					val index = members.getInt(i)
					if (!own[index]) continue
					held.add(index)
					nearest = min(nearest, hypot(x(index) - stance.x - 0.5, z(index) - stance.z - 0.5))
				}
				if (held.size > best.size || held.size == best.size && nearest < bestDistance) {
					best = held
					bestDistance = nearest
				}
			}
			return best
		}

		fun holds(stance: Stance): Holds {
			val held = footholds(stance)
			if (held.isEmpty) return Holds.EMPTY
			val levels = DoubleArrayList()
			val masks = it.unimi.dsi.fastutil.longs.LongArrayList()
			for (i in 0 until held.size) {
				val index = held.getInt(i)
				var level = levels.indexOf(feet(index))
				if (level < 0) {
					level = levels.size
					levels.add(feet(index))
					repeat(Holds.WORDS) { masks.add(0L) }
				}
				val slot = level * Holds.WORDS + (index ushr 6)
				masks.set(slot, masks.getLong(slot) or (1L shl index))
			}
			return Holds(kx0, kz0, levels.toDoubleArray(), masks.toLongArray())
		}

		fun indices(keys: LongArray): IntArrayList {
			val indices = IntArrayList(keys.size)
			for (key in keys) {
				val index = indexOf(key)
				if (index >= 0 && !feet(index).isNaN()) indices.add(index)
			}
			return indices
		}

		fun mask(keys: LongArray): BooleanArray {
			val mask = BooleanArray(nx * nz)
			for (key in keys) {
				val index = indexOf(key)
				if (index >= 0) mask[index] = true
			}
			return mask
		}

		fun nearestValid(point: HorizontalPoint): Int? {
			val cx = (point.x / STEP).roundToInt() - kx0
			val cz = (point.z / STEP).roundToInt() - kz0
			var best: Int? = null
			var bestDistance = Double.MAX_VALUE
			for (gz in max(0, cz - MARGIN)..min(nz - 1, cz + MARGIN)) for (gx in max(0, cx - MARGIN)..min(nx - 1, cx + MARGIN)) {
				val index = gz * nx + gx
				if (feet(index).isNaN() || abs(feet(index) - point.y) > WALK_RISE) continue
				val distance = hypot(x(index) - point.x, z(index) - point.z)
				if (distance < bestDistance) {
					best = index
					bestDistance = distance
				}
			}
			return best
		}

		fun search(start: IntArrayList, maxRise: Double, goal: BooleanArray): IntArrayList? {
			val previous = IntArray(nx * nz) { UNSEEN }
			val queue = IntArrayList()
			for (i in 0 until start.size) {
				val index = start.getInt(i)
				if (previous[index] != UNSEEN) continue
				previous[index] = NONE
				queue.add(index)
			}
			var head = 0
			while (head < queue.size) {
				val index = queue.getInt(head++)
				if (goal[index]) {
					val path = IntArrayList()
					var at = index
					while (at != NONE) {
						path.add(at)
						at = previous[at]
					}
					path.reverse()
					return path
				}
				forEachNeighbor(index) { next ->
					if (previous[next] == UNSEEN && steps(index, next, maxRise)) {
						previous[next] = index
						queue.add(next)
					}
				}
			}
			return null
		}

		private fun flood(seed: Int, labels: BooleanArray): IntArrayList {
			val members = IntArrayList.of(seed)
			labels[seed] = true
			var head = 0
			while (head < members.size) {
				val index = members.getInt(head++)
				forEachNeighbor(index) { next ->
					if (!labels[next] && steps(index, next, WALK_RISE) && steps(next, index, WALK_RISE)) {
						labels[next] = true
						members.add(next)
					}
				}
			}
			return members
		}

		fun climbs(bottom: Int, top: Int): Boolean {
			val rise = feet(top) - feet(bottom)
			if (rise <= EPSILON || rise > JUMP_RISE + EPSILON) return false
			if (abs(x(top) - x(bottom)) > CLIMB_REACH || abs(z(top) - z(bottom)) > CLIMB_REACH) return false
			return boxes.clear(x(bottom), x(bottom), z(bottom), z(bottom), feet(bottom), feet(top) + HEIGHT) &&
					boxes.clear(min(x(bottom), x(top)), max(x(bottom), x(top)), min(z(bottom), z(top)), max(z(bottom), z(top)), feet(top), feet(top) + HEIGHT)
		}

		fun simplify(path: IntArrayList): List<Int> {
			if (path.size <= 2) return path.toList()
			val kept = arrayListOf(path.getInt(0))
			for (i in 1 until path.size - 1) {
				val previous = path.getInt(i - 1)
				val at = path.getInt(i)
				val next = path.getInt(i + 1)
				if (at - previous != next - at || abs(feet(at) - feet(previous)) > EPSILON) kept += at
			}
			kept += path.getInt(path.size - 1)
			return kept
		}

		private fun indexOf(key: Long): Int {
			val gx = (key shr 32).toInt() - kx0
			val gz = key.toInt() - kz0
			return if (gx in 0 until nx && gz in 0 until nz) gz * nx + gx else -1
		}

		private fun steps(from: Int, to: Int, maxRise: Double): Boolean {
			if (feet(to).isNaN()) return false
			val rise = feet(to) - feet(from)
			return rise <= maxRise + EPSILON && -rise <= FALL_DROP + EPSILON
		}

		private inline fun forEachNeighbor(index: Int, action: (Int) -> Unit) {
			val gx = index % nx
			if (gx > 0) action(index - 1)
			if (gx < nx - 1) action(index + 1)
			if (index >= nx) action(index - nx)
			if (index < nx * (nz - 1)) action(index + nx)
		}

		companion object {
			const val UNSEEN = -2
			const val NONE = -1

			const val UNCOMPUTED = Double.NEGATIVE_INFINITY

			val CENTER_OUT: IntArray = (0 until Holds.SIDE * Holds.SIDE).sortedBy { index ->
				val dx = index % Holds.SIDE - Holds.SIDE / 2
				val dz = index / Holds.SIDE - Holds.SIDE / 2
				dx * dx + dz * dz
			}.toIntArray()

			fun around(view: CoarseVoxelView, stance: Stance) = Patch(view, stance.x, stance.x, stance.z, stance.z, stance.y, stance.y)

			fun spanning(view: CoarseVoxelView, a: Stance, b: Stance) =
				Patch(view, min(a.x, b.x), max(a.x, b.x), min(a.z, b.z), max(a.z, b.z), min(a.y, b.y), max(a.y, b.y))
		}
	}

	private class Boxes(view: CoarseVoxelView, private val x0: Int, x1: Int, y0: Int, y1: Int, private val z0: Int, z1: Int) {
		private val width = x1 - x0 + 1
		private val depth = z1 - z0 + 1
		private val bounds: DoubleArray
		private val floors: BooleanArray
		private val starts = IntArray(width * depth + 1)

		init {
			val collected = DoubleArrayList()
			val holding = BooleanArrayList()
			fun add(x: Int, y: Int, z: Int, minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, floor: Boolean) {
				collected.add(x + minX); collected.add(y + minY); collected.add(z + minZ)
				collected.add(x + maxX); collected.add(y + maxY); collected.add(z + maxZ)
				holding.add(floor)
			}
			var column = 0
			for (z in z0..z1) for (x in x0..x1) {
				starts[column++] = holding.size
				for (y in y0..y1) {
					val boxes = view.collisionBoxes(x, y, z)
					if (boxes == null) {
						add(x, y, z, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, floor = false)
						continue
					}
					val floor = view.voxel(x, y, z).medium.let { it != Medium.WATER && it != Medium.UNKNOWN }
					for (box in 0 until boxes.size / 6) {
						val b = 6 * box
						add(x, y, z, boxes[b], boxes[b + 1], boxes[b + 2], boxes[b + 3], boxes[b + 4], boxes[b + 5], floor)
					}
				}
			}
			starts[column] = holding.size
			bounds = collected.toDoubleArray()
			floors = holding.toBooleanArray()
		}

		private fun columnX(x: Double): Int = (floor(x).toInt() - x0).coerceIn(0, width - 1)

		private fun columnZ(z: Double): Int = (floor(z).toInt() - z0).coerceIn(0, depth - 1)

		fun standingHeight(x: Double, z: Double, floorFeet: Double, ceilingFeet: Double): Double {
			var height = Double.NaN
			for (cz in columnZ(z - HALF_WIDTH)..columnZ(z + HALF_WIDTH)) for (cx in columnX(x - HALF_WIDTH)..columnX(x + HALF_WIDTH)) {
				val column = cz * width + cx
				for (i in starts[column] until starts[column + 1]) {
					val top = bounds[6 * i + 4]
					if (!floors[i] || top <= floorFeet + EPSILON || top > ceilingFeet + EPSILON || !(top > height || height.isNaN())) continue
					if (supports(i, x, z)) height = top
				}
			}
			return if (!height.isNaN() && clear(x, x, z, z, height, height + HEIGHT)) height else Double.NaN
		}

		fun supportsFrom(x: Double, z: Double, height: Double, cellX: Int, cellZ: Int): Boolean {
			if (cellX - x0 !in 0 until width || cellZ - z0 !in 0 until depth) return false
			val column = (cellZ - z0) * width + (cellX - x0)
			for (i in starts[column] until starts[column + 1]) {
				if (!floors[i] || abs(bounds[6 * i + 4] - height) > EPSILON) continue
				if (supports(i, x, z)) return true
			}
			return false
		}

		fun clear(fromX: Double, toX: Double, fromZ: Double, toZ: Double, bottom: Double, top: Double): Boolean {
			for (cz in columnZ(fromZ - HALF_WIDTH)..columnZ(toZ + HALF_WIDTH)) for (cx in columnX(fromX - HALF_WIDTH)..columnX(toX + HALF_WIDTH)) {
				val column = cz * width + cx
				for (i in starts[column] until starts[column + 1]) {
					val b = 6 * i
					if (bounds[b] >= toX + HALF_WIDTH - EPSILON || bounds[b + 3] <= fromX - HALF_WIDTH + EPSILON) continue
					if (bounds[b + 2] >= toZ + HALF_WIDTH - EPSILON || bounds[b + 5] <= fromZ - HALF_WIDTH + EPSILON) continue
					if (bounds[b + 1] >= top - EPSILON || bounds[b + 4] <= bottom + EPSILON) continue
					return false
				}
			}
			return true
		}

		private fun supports(i: Int, x: Double, z: Double): Boolean {
			val b = 6 * i
			return min(bounds[b + 3], x + HALF_WIDTH) - max(bounds[b], x - HALF_WIDTH) >= MIN_SUPPORT &&
					min(bounds[b + 5], z + HALF_WIDTH) - max(bounds[b + 2], z - HALF_WIDTH) >= MIN_SUPPORT
		}
	}
}
