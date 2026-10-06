package com.lambda.pathing.world.snapshot

import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.BlockCollisions
import com.lambda.pathing.physics.SimulationEnvironment
import com.lambda.pathing.physics.SimulationSnapshotOutOfBoundsException
import com.lambda.pathing.physics.SnapshotSectionUnavailableException
import com.lambda.pathing.physics.UnsupportedBlockPhysicsException
import com.lambda.pathing.physics.VanillaBlockCollisionResolver
import com.lambda.pathing.physics.closestSupportingBlock
import com.lambda.pathing.physics.forEachCollisionCandidate
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.Medium
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import net.minecraft.util.function.BooleanBiFunction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import java.util.*

class SnapshotSimulationEnvironment internal constructor(
	val bounds: SimulationSnapshotBounds,
	private val storeRef: StoreRef,
	private val defaultBlock: SnapshotBlockPhysics?,
	private val sparse: Boolean = false,
	private val missingSection: ((Int, Int, Int, Boolean) -> ImmutableSnapshotSection)? = null,
	private val unavailableSectionKeys: Set<Long>? = null,
	private val onExactMiss: ((Long) -> Unit)? = null,
	private val edits: Long2ObjectMap<SnapshotBlockPhysics>? = null,
) : SimulationEnvironment, CoarseVoxelView {
	internal constructor(
		bounds: SimulationSnapshotBounds,
		sections: Map<Long, ImmutableSnapshotSection>,
		defaultBlock: SnapshotBlockPhysics?,
		missingSection: ((Int, Int, Int, Boolean) -> ImmutableSnapshotSection)? = null,
		unavailableSectionKeys: Set<Long>? = null,
	) : this(
		bounds = bounds,
		storeRef = StoreRef(SectionStore.of(sections)),
		defaultBlock = defaultBlock,
		missingSection = missingSection,
		unavailableSectionKeys = unavailableSectionKeys,
	)

	internal class StoreRef(@Volatile var store: SectionStore) {
		fun install(key: Long, section: ImmutableSnapshotSection) = synchronized(this) {
			store = store.with(key, section)
		}

		fun remove(keys: LongArrayList) = synchronized(this) {
			store = store.without(keys)
		}
	}

	private fun interface SnapshotReadObserver {
		fun onRead(pos: BlockPos)
	}

	private val store: SectionStore get() = storeRef.store

	internal fun hasSection(key: Long): Boolean = store.contains(key)

	internal fun install(key: Long, section: ImmutableSnapshotSection) = storeRef.install(key, section)

	internal fun removeSections(keys: LongArrayList) = storeRef.remove(keys)

	internal inline fun forEachSectionKey(action: (Long) -> Unit) = store.forEach { key, _ -> action(key) }

	fun withEdits(edits: Map<BlockPos, SnapshotBlockPhysics>): SnapshotSimulationEnvironment {
		val packed = Long2ObjectOpenHashMap<SnapshotBlockPhysics>(edits.size)
		edits.forEach { (pos, physics) ->
			require(pos in bounds) { "Edit $pos lies outside $bounds" }
			packed.put(BlockPos.asLong(pos.x, pos.y, pos.z), physics)
		}
		return SnapshotSimulationEnvironment(
			bounds, storeRef, defaultBlock, sparse, missingSection, unavailableSectionKeys, onExactMiss, packed,
		)
	}

	internal val sectionCount: Int get() = store.size

	internal fun forEachSnapshotBlock(action: (Long, SnapshotBlockPhysics) -> Unit) {
		if (sparse) {
			store.forEach { key, section ->
				val sectionX = ChunkSectionPos.unpackX(key)
				val sectionY = ChunkSectionPos.unpackY(key)
				val sectionZ = ChunkSectionPos.unpackZ(key)
				for (localY in 0..15) for (localZ in 0..15) for (localX in 0..15) {
					val x = (sectionX shl 4) + localX
					val y = (sectionY shl 4) + localY
					val z = (sectionZ shl 4) + localZ
					if (x in bounds.minX..bounds.maxX && y in bounds.minY..bounds.maxY &&
						z in bounds.minZ..bounds.maxZ
					) action(BlockPos.asLong(x, y, z), section[x, y, z])
				}
			}
		} else {
			for (y in bounds.minY..bounds.maxY) for (z in bounds.minZ..bounds.maxZ) for (x in bounds.minX..bounds.maxX) {
				val physics = blockInside(liveSections, x, y, z) ?: error("Incomplete snapshot at ($x,$y,$z)")
				action(BlockPos.asLong(x, y, z), physics)
			}
		}
	}

	override val simulableStanceY: IntRange = bounds.simulableStanceY

	private fun inBounds(x: Int, y: Int, z: Int): Boolean =
		x in bounds.minX..bounds.maxX && y in bounds.minY..bounds.maxY && z in bounds.minZ..bounds.maxZ

	override fun isKnown(x: Int, y: Int, z: Int): Boolean = isKnown(liveSections, x, y, z)

	private fun isKnown(sections: SectionSource, x: Int, y: Int, z: Int): Boolean {
		if (!inBounds(x, y, z)) return false
		val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
		val installed = sections.at(key)
		physicsIn(resolveSection(installed, key, x shr 4, y shr 4, z shr 4, exact = false), x, y, z) ?: return false
		return !isUnavailable(key, installed)
	}

	private fun isUnavailable(key: Long, installed: ImmutableSnapshotSection?): Boolean =
		(sparse && installed == null) || unavailableSectionKeys?.contains(key) == true

	override fun slipperiness(pos: BlockPos): Double = checkedBlockAt(pos, null).slipperiness

	override fun velocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos, null).velocityMultiplier

	override fun jumpVelocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos, null).jumpVelocityMultiplier

	override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = voxel(liveSections, x, y, z)

	private fun voxel(sections: SectionSource, x: Int, y: Int, z: Int): CoarseVoxel {
		if (!inBounds(x, y, z)) return CoarseVoxel.UNKNOWN
		val block = blockInside(sections, x, y, z) ?: return CoarseVoxel.UNKNOWN
		return if (block.unsupportedPhysics == null) block.coarseVoxel else CoarseVoxel.HAZARD
	}

	override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape = collisionShape(liveSections, x, y, z)

	private fun collisionShape(sections: SectionSource, x: Int, y: Int, z: Int): VoxelShape {
		if (!inBounds(x, y, z)) return VoxelShapes.fullCube()
		val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
		val installed = sections.at(key)
		if (isUnavailable(key, installed)) return VoxelShapes.empty()
		val block = physicsIn(resolveSection(installed, key, x shr 4, y shr 4, z shr 4, exact = false), x, y, z)
			?: return VoxelShapes.fullCube()
		return if (block.unsupportedPhysics == null) block.collisionShape else VoxelShapes.fullCube()
	}

	override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass = collisionClass(liveSections, x, y, z)

	private fun collisionClass(sections: SectionSource, x: Int, y: Int, z: Int): CollisionClass {
		if (!inBounds(x, y, z)) return CollisionClass.FULL
		val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
		val installed = sections.at(key)
		if (isUnavailable(key, installed)) return CollisionClass.EMPTY
		val block = physicsIn(resolveSection(installed, key, x shr 4, y shr 4, z shr 4, exact = false), x, y, z)
			?: return CollisionClass.FULL
		return block.collisionClass
	}

	override fun adjustMovementForCollisions(
		movement: Vec3d,
		boundingBox: Box,
		onGround: Boolean,
		stepHeight: Double,
	): Vec3d = adjustMovementForCollisions(movement, boundingBox, onGround, stepHeight, null)

	private fun adjustMovementForCollisions(
		movement: Vec3d,
		boundingBox: Box,
		onGround: Boolean,
		stepHeight: Double,
		observer: SnapshotReadObserver?,
	): Vec3d = VanillaBlockCollisionResolver.adjust(
		movement = movement,
		boundingBox = boundingBox,
		onGround = onGround,
		stepHeight = stepHeight,
		collisionShapes = { query ->
			BlockCollisions().also { collisions ->
				forEachCollisionShape(query, observer) { pos, shape -> collisions.add(pos.x, pos.y, pos.z, shape) }
			}
		},
	)

	private fun collectBlockShapes(query: Box, observer: SnapshotReadObserver?): List<VoxelShape> {
		val shapes = ArrayList<VoxelShape>()
		forEachCollisionShape(query, observer) { pos, shape ->
			shapes += shape.offset(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
		}
		return shapes
	}

	private inline fun forEachCollisionShape(
		query: Box,
		observer: SnapshotReadObserver?,
		action: (BlockPos, VoxelShape) -> Unit,
	) {
		val cursor = ExactReadCursor(store)
		forEachCollisionCandidate(query) { pos ->
			observer?.onRead(pos)
			val block = cursor.blockAt(pos)
			val unsupported = block.unsupportedPhysics
			if (unsupported != null && query.intersectsUnitBlock(pos.x, pos.y, pos.z)) {
				throw UnsupportedBlockPhysicsException(pos.toImmutable(), unsupported)
			}
			if (!block.collisionShape.isEmpty) action(pos, block.collisionShape)
		}
	}

	override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? =
		findSupportingBlockPos(box, entityPos, null)

	override fun isFenceLike(pos: BlockPos): Boolean = isFenceLike(pos, null)

	override fun isSpaceEmpty(box: Box): Boolean = isSpaceEmpty(box, null)

	private fun isSpaceEmpty(box: Box, observer: SnapshotReadObserver?): Boolean {
		val query = VoxelShapes.cuboid(box)
		return collectBlockShapes(box, observer).none { shape ->
			VoxelShapes.matchesAnywhere(shape, query, BooleanBiFunction.AND)
		}
	}

	override fun isClimbable(pos: BlockPos): Boolean =
		blockInside(liveSections, pos.x, pos.y, pos.z)?.coarseVoxel?.medium == Medium.CLIMBABLE

	override fun bounceFactor(pos: BlockPos): Double = checkedBlockAt(pos, null).bounceFactor

	override fun dampensSteppingSpeed(pos: BlockPos): Boolean =
		checkedBlockAt(pos, null).dampensSteppingSpeed

	private fun isFenceLike(pos: BlockPos, observer: SnapshotReadObserver?): Boolean =
		checkedBlockAt(pos, observer).fenceLike

	private fun findSupportingBlockPos(
		box: Box,
		entityPos: Vec3d,
		observer: SnapshotReadObserver?,
	): BlockPos? {
		val cursor = ExactReadCursor(store)
		return closestSupportingBlock(box, entityPos) { pos ->
			observer?.onRead(pos)
			val block = cursor.blockAt(pos)
			!block.collisionShape.isEmpty && block.intersectsCollisionBox(box, pos.x, pos.y, pos.z)
		}
	}

	private inner class ExactReadCursor(private val store: SectionStore) {
		private var resolved = false
		private var key = 0L
		private var section: ImmutableSnapshotSection? = null

		fun blockAt(pos: BlockPos): SnapshotBlockPhysics {
			if (pos !in bounds) throw SimulationSnapshotOutOfBoundsException(pos.toImmutable())
			val x = pos.x
			val y = pos.y
			val z = pos.z
			val sectionX = x shr 4
			val sectionY = y shr 4
			val sectionZ = z shr 4
			val k = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
			if (!resolved || k != key) {
				key = k
				section = resolveSection(store[k], k, sectionX, sectionY, sectionZ, exact = true)
				resolved = true
			}
			return physicsIn(section, x, y, z) ?: missingExact(pos, k, sectionX, sectionY, sectionZ)
		}
	}

	private fun blockAt(pos: BlockPos): SnapshotBlockPhysics {
		if (pos !in bounds) throw SimulationSnapshotOutOfBoundsException(pos.toImmutable())
		val sectionX = pos.x shr 4
		val sectionY = pos.y shr 4
		val sectionZ = pos.z shr 4
		val key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
		val section = resolveSection(store[key], key, sectionX, sectionY, sectionZ, exact = true)
		return physicsIn(section, pos.x, pos.y, pos.z) ?: missingExact(pos, key, sectionX, sectionY, sectionZ)
	}

	private fun missingExact(pos: BlockPos, key: Long, sectionX: Int, sectionY: Int, sectionZ: Int): Nothing {
		if (isUnavailable(key, store[key])) {
			onExactMiss?.invoke(key)
			throw SnapshotSectionUnavailableException(sectionX, sectionY, sectionZ)
		}
		throw IllegalStateException("Incomplete production snapshot at $pos")
	}

	private fun blockInside(sections: SectionSource, x: Int, y: Int, z: Int): SnapshotBlockPhysics? {
		val sectionX = x shr 4
		val sectionY = y shr 4
		val sectionZ = z shr 4
		val key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
		return physicsIn(resolveSection(sections.at(key), key, sectionX, sectionY, sectionZ, exact = false), x, y, z)
	}

	private fun interface SectionSource {
		fun at(key: Long): ImmutableSnapshotSection?
	}

	private val liveSections = SectionSource { store[it] }

	override fun cursor(): CoarseVoxelView = SectionCursor()

	override fun mayBounce(): Boolean = store.bouncySections > 0 || bouncyOutsideStore

	private val bouncyOutsideStore: Boolean =
		missingSection != null || defaultBlock?.coarseVoxel?.bouncy == true || edits?.values?.any { it.coarseVoxel.bouncy } == true

	private inner class SectionCursor : CoarseVoxelView, SectionSource {
		private var cached: SectionStore? = null
		private var filled = 0L
		private val keys = LongArray(CURSOR_SLOTS)
		private val sections = arrayOfNulls<ImmutableSnapshotSection>(CURSOR_SLOTS)

		override val simulableStanceY: IntRange get() = this@SnapshotSimulationEnvironment.simulableStanceY

		override fun at(key: Long): ImmutableSnapshotSection? {
			val current = store
			if (current !== cached) {
				cached = current
				filled = 0L
			}
			val slot = (((key ushr 42).toInt() and 3) shl 4) or ((key.toInt() and 3) shl 2) or ((key ushr 20).toInt() and 3)
			if (filled and (1L shl slot) != 0L && keys[slot] == key) return sections[slot]
			val section = current[key]
			keys[slot] = key
			sections[slot] = section
			filled = filled or (1L shl slot)
			return section
		}

		override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = voxel(this, x, y, z)

		override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape = collisionShape(this, x, y, z)

		override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass = collisionClass(this, x, y, z)

		override fun isKnown(x: Int, y: Int, z: Int): Boolean = isKnown(this, x, y, z)

		override fun mayBounce(): Boolean = this@SnapshotSimulationEnvironment.mayBounce()
	}

	private fun resolveSection(
		installed: ImmutableSnapshotSection?,
		key: Long,
		sectionX: Int,
		sectionY: Int,
		sectionZ: Int,
		exact: Boolean,
	): ImmutableSnapshotSection? =
		if (installed != null && (!exact || unavailableSectionKeys?.contains(key) != true)) installed
		else missingSection?.invoke(sectionX, sectionY, sectionZ, exact)

	private fun physicsIn(section: ImmutableSnapshotSection?, x: Int, y: Int, z: Int): SnapshotBlockPhysics? {
		edits?.get(BlockPos.asLong(x, y, z))?.let { return it }
		return section?.get(x, y, z) ?: defaultBlock
	}

	private fun SnapshotBlockPhysics.checked(pos: BlockPos): SnapshotBlockPhysics {
		val unsupported = unsupportedPhysics
		if (unsupported != null) throw UnsupportedBlockPhysicsException(pos.toImmutable(), unsupported)
		return this
	}

	private fun checkedBlockAt(pos: BlockPos, observer: SnapshotReadObserver?): SnapshotBlockPhysics {
		observer?.onRead(pos)
		return blockAt(pos).checked(pos)
	}

	fun trackingView(): TrackedSnapshotSimulationEnvironment = TrackedSnapshotSimulationEnvironment(this)

	private fun Box.intersectsUnitBlock(x: Int, y: Int, z: Int): Boolean =
		maxX > x && minX < x + 1.0 &&
				maxY > y && minY < y + 1.0 &&
				maxZ > z && minZ < z + 1.0

	companion object {
		private const val CURSOR_SLOTS = 64

		internal fun streaming(
			bounds: SimulationSnapshotBounds,
			onExactMiss: (Long) -> Unit,
		): SnapshotSimulationEnvironment = SnapshotSimulationEnvironment(
			bounds = bounds,
			storeRef = StoreRef(SectionStore.EMPTY),
			defaultBlock = null,
			sparse = true,
			onExactMiss = onExactMiss,
		)

		fun synthetic(
			bounds: SimulationSnapshotBounds,
			blocks: Map<BlockPos, SnapshotBlockPhysics>,
		): SnapshotSimulationEnvironment {
			val builders = HashMap<Long, ImmutableSnapshotSection.Builder>()
			blocks.forEach { (pos, physics) ->
				require(pos in bounds) { "Synthetic block $pos lies outside $bounds" }
				val sectionKey = ChunkSectionPos.toLong(pos)
				builders.getOrPut(sectionKey, ImmutableSnapshotSection::Builder)
					.set(pos.x, pos.y, pos.z, physics)
			}
			return SnapshotSimulationEnvironment(
				bounds = bounds,
				sections = builders.mapValues { (_, builder) -> builder.build() },
				defaultBlock = SnapshotBlockPhysics.AIR,
			)
		}
	}

	class TrackedSnapshotSimulationEnvironment internal constructor(
		private val snapshot: SnapshotSimulationEnvironment,
	) : SimulationEnvironment {
		private val pendingFrameReads = HashSet<VoxelPos>()
		private val observer = SnapshotReadObserver { pos -> pendingFrameReads += VoxelPos(pos.x, pos.y, pos.z) }

		fun takeFrameDependencies(): Set<VoxelPos> =
			Collections.unmodifiableSet(HashSet(pendingFrameReads)).also { pendingFrameReads.clear() }

		override fun slipperiness(pos: BlockPos): Double = snapshot.checkedBlockAt(pos, observer).slipperiness

		override fun velocityMultiplier(pos: BlockPos): Double = snapshot.checkedBlockAt(pos, observer).velocityMultiplier

		override fun jumpVelocityMultiplier(pos: BlockPos): Double = snapshot.checkedBlockAt(pos, observer).jumpVelocityMultiplier

		override fun adjustMovementForCollisions(
			movement: Vec3d,
			boundingBox: Box,
			onGround: Boolean,
			stepHeight: Double,
		): Vec3d = snapshot.adjustMovementForCollisions(movement, boundingBox, onGround, stepHeight, observer)

		override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? =
			snapshot.findSupportingBlockPos(box, entityPos, observer)

		override fun isFenceLike(pos: BlockPos): Boolean = snapshot.isFenceLike(pos, observer)

		override fun isSpaceEmpty(box: Box): Boolean = snapshot.isSpaceEmpty(box, observer)

		override fun isClimbable(pos: BlockPos): Boolean {
			observer.onRead(pos)
			return snapshot.isClimbable(pos)
		}

		override fun bounceFactor(pos: BlockPos): Double =
			snapshot.checkedBlockAt(pos, observer).bounceFactor

		override fun dampensSteppingSpeed(pos: BlockPos): Boolean =
			snapshot.checkedBlockAt(pos, observer).dampensSteppingSpeed
	}
}
