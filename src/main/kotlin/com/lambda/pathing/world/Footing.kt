package com.lambda.pathing.world

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.Vanilla
import it.unimi.dsi.fastutil.booleans.BooleanArrayList
import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import net.minecraft.util.math.BlockPos
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

	private const val CACHE_CAPACITY = 256

	fun involvesPartial(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean =
		view.voxel(x, y, z).partial || view.voxel(x, y + 1, z).partial || view.voxel(x, y - 1, z).partial || view.voxel(x, y - 2, z).partial

	fun stands(view: CoarseVoxelView, stance: Stance): Boolean = !holds(view, stance).isEmpty

	fun holds(view: CoarseVoxelView, stance: Stance): Holds {
		val revision = view.revision
		val cache = caches.get()
		if (cache.view.get() !== view || cache.revision.get() !== revision) cache.reset(view, revision)
		val key = BlockPos.asLong(stance.x, stance.y, stance.z)
		cache.entries[key]?.let { return it }
		val holds = Patch.around(view, stance).holds(stance)
		if (revision != null && known(view, stance)) cache.entries[key] = holds
		return holds
	}

	inline fun forEachRead(stance: Stance, visit: (Int, Int, Int) -> Unit) {
		for (x in stance.x - 1..stance.x + 1) for (y in stance.y - 2..stance.y + 2) for (z in stance.z - 1..stance.z + 1) visit(x, y, z)
	}

	class Holds internal constructor(internal val keys: LongArray, private val heights: DoubleArray) {
		private val lookup by lazy(LazyThreadSafetyMode.NONE) { Long2DoubleOpenHashMap(keys, heights) }

		val isEmpty: Boolean get() = keys.isEmpty()

		fun at(x: Double, z: Double): Double? {
			val key = gridKey((x / STEP).roundToInt(), (z / STEP).roundToInt())
			return if (lookup.containsKey(key)) lookup.get(key) else null
		}

		fun nearest(x: Double, z: Double): HorizontalPoint? {
			var best = -1
			var bestDistance = Double.MAX_VALUE
			for (i in keys.indices) {
				val distance = hypot(gridX(keys[i]) * STEP - x, gridZ(keys[i]) * STEP - z)
				if (distance < bestDistance) {
					best = i
					bestDistance = distance
				}
			}
			return if (best < 0) null else HorizontalPoint(gridX(keys[best]) * STEP, heights[best], gridZ(keys[best]) * STEP)
		}
	}

	private fun gridKey(gridX: Int, gridZ: Int): Long = gridX.toLong() shl 32 or (gridZ.toLong() and 0xFFFFFFFFL)

	private fun gridX(key: Long): Int = (key shr 32).toInt()

	private fun gridZ(key: Long): Int = key.toInt()

	private fun known(view: CoarseVoxelView, stance: Stance): Boolean {
		for (x in intArrayOf(stance.x - 1, stance.x + 1)) for (y in intArrayOf(stance.y - 2, stance.y + 2)) for (z in intArrayOf(stance.z - 1, stance.z + 1)) {
			if (!view.isKnown(x, y, z)) return false
		}
		return true
	}

	private class Cache {
		var view = WeakReference<CoarseVoxelView>(null)
		var revision = WeakReference<Any>(null)
		val entries = object : LinkedHashMap<Long, Holds>(CACHE_CAPACITY, 0.75f, true) {
			override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Holds>) = size > CACHE_CAPACITY
		}

		fun reset(view: CoarseVoxelView, revision: Any?) {
			entries.clear()
			this.view = WeakReference(view)
			this.revision = WeakReference(revision)
		}
	}

	private val caches = ThreadLocal.withInitial(::Cache)

	fun walkable(view: CoarseVoxelView, from: Stance, to: Stance, maxRise: Double): Boolean {
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
		private val feet = DoubleArray(nx * nz) { boxes.standingHeight(x(it), z(it), low - 1.0, high.toDouble()) }

		fun x(index: Int): Double = (kx0 + index % nx) * STEP

		fun z(index: Int): Double = (kz0 + index / nx) * STEP

		fun point(index: Int): HorizontalPoint = HorizontalPoint(x(index), feet[index], z(index))

		fun footholds(stance: Stance): LongArray {
			val own = BooleanArray(nx * nz) { index ->
				val height = feet[index]
				!height.isNaN() && height > stance.y - 1.0 + EPSILON && height <= stance.y + EPSILON &&
						boxes.supportsFrom(x(index), z(index), height, stance.x, stance.z)
			}
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
			return LongArray(best.size) { key(best.getInt(it)) }
		}

		fun holds(stance: Stance): Holds {
			val keys = footholds(stance)
			return Holds(keys, DoubleArray(keys.size) { feet[indexOf(keys[it])] })
		}

		fun indices(keys: LongArray): IntArrayList {
			val indices = IntArrayList(keys.size)
			for (key in keys) {
				val index = indexOf(key)
				if (index >= 0 && !feet[index].isNaN()) indices.add(index)
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
				if (feet[index].isNaN() || abs(feet[index] - point.y) > WALK_RISE) continue
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
			val rise = feet[top] - feet[bottom]
			if (rise <= EPSILON || rise > JUMP_RISE + EPSILON) return false
			if (abs(x(top) - x(bottom)) > CLIMB_REACH || abs(z(top) - z(bottom)) > CLIMB_REACH) return false
			return boxes.clear(x(bottom), x(bottom), z(bottom), z(bottom), feet[bottom], feet[top] + HEIGHT) &&
					boxes.clear(min(x(bottom), x(top)), max(x(bottom), x(top)), min(z(bottom), z(top)), max(z(bottom), z(top)), feet[top], feet[top] + HEIGHT)
		}

		fun simplify(path: IntArrayList): List<Int> {
			if (path.size <= 2) return path.toList()
			val kept = arrayListOf(path.getInt(0))
			for (i in 1 until path.size - 1) {
				val previous = path.getInt(i - 1)
				val at = path.getInt(i)
				val next = path.getInt(i + 1)
				if (at - previous != next - at || abs(feet[at] - feet[previous]) > EPSILON) kept += at
			}
			kept += path.getInt(path.size - 1)
			return kept
		}

		private fun key(index: Int): Long = gridKey(kx0 + index % nx, kz0 + index / nx)

		private fun indexOf(key: Long): Int {
			val gx = (key shr 32).toInt() - kx0
			val gz = key.toInt() - kz0
			return if (gx in 0 until nx && gz in 0 until nz) gz * nx + gx else -1
		}

		private fun steps(from: Int, to: Int, maxRise: Double): Boolean {
			if (feet[to].isNaN()) return false
			val rise = feet[to] - feet[from]
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

			fun around(view: CoarseVoxelView, stance: Stance) = Patch(view, stance.x, stance.x, stance.z, stance.z, stance.y, stance.y)

			fun spanning(view: CoarseVoxelView, a: Stance, b: Stance) =
				Patch(view, min(a.x, b.x), max(a.x, b.x), min(a.z, b.z), max(a.z, b.z), min(a.y, b.y), max(a.y, b.y))
		}
	}

	private class Boxes(view: CoarseVoxelView, x0: Int, x1: Int, y0: Int, y1: Int, z0: Int, z1: Int) {
		private val bounds: DoubleArray
		private val columns: IntArray
		private val floors: BooleanArray

		init {
			val collected = DoubleArrayList()
			val owners = IntArrayList()
			val holding = BooleanArrayList()
			fun add(x: Int, y: Int, z: Int, minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double, floor: Boolean) {
				collected.add(x + minX); collected.add(y + minY); collected.add(z + minZ)
				collected.add(x + maxX); collected.add(y + maxY); collected.add(z + maxZ)
				owners.add(x); owners.add(z)
				holding.add(floor)
			}
			for (x in x0..x1) for (y in y0..y1) for (z in z0..z1) {
				val shape = view.collisionShape(x, y, z)
				val floor = shape != null && view.voxel(x, y, z).medium.let { it != Medium.WATER && it != Medium.UNKNOWN }
				if (shape == null) add(x, y, z, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, floor)
				else for (box in shape.boundingBoxes) add(x, y, z, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, floor)
			}
			bounds = collected.toDoubleArray()
			columns = owners.toIntArray()
			floors = holding.toBooleanArray()
		}

		fun standingHeight(x: Double, z: Double, floorFeet: Double, ceilingFeet: Double): Double {
			var height = Double.NaN
			for (i in floors.indices) {
				val top = bounds[6 * i + 4]
				if (!floors[i] || top <= floorFeet + EPSILON || top > ceilingFeet + EPSILON || !(top > height || height.isNaN())) continue
				if (supports(i, x, z)) height = top
			}
			return if (!height.isNaN() && clear(x, x, z, z, height, height + HEIGHT)) height else Double.NaN
		}

		fun supportsFrom(x: Double, z: Double, height: Double, cellX: Int, cellZ: Int): Boolean {
			for (i in floors.indices) {
				if (!floors[i] || columns[2 * i] != cellX || columns[2 * i + 1] != cellZ || abs(bounds[6 * i + 4] - height) > EPSILON) continue
				if (supports(i, x, z)) return true
			}
			return false
		}

		fun clear(fromX: Double, toX: Double, fromZ: Double, toZ: Double, bottom: Double, top: Double): Boolean {
			for (i in floors.indices) {
				val b = 6 * i
				if (bounds[b] >= toX + HALF_WIDTH - EPSILON || bounds[b + 3] <= fromX - HALF_WIDTH + EPSILON) continue
				if (bounds[b + 2] >= toZ + HALF_WIDTH - EPSILON || bounds[b + 5] <= fromZ - HALF_WIDTH + EPSILON) continue
				if (bounds[b + 1] >= top - EPSILON || bounds[b + 4] <= bottom + EPSILON) continue
				return false
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
