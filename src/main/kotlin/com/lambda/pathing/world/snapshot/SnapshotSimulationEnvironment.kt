package com.lambda.pathing.world.snapshot

import com.lambda.pathing.core.VoxelPos
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
	private val onExactMiss: ((Long) -> Unit)? = null,
	private val onRead: ((BlockPos) -> Unit)? = null,
) : SimulationEnvironment, CoarseVoxelView {
	internal constructor(
		bounds: SimulationSnapshotBounds,
		sections: Map<Long, ImmutableSnapshotSection>,
		defaultBlock: SnapshotBlockPhysics?,
	) : this(bounds, StoreRef(SectionStore.of(sections)), defaultBlock)

	internal class StoreRef(@Volatile var store: SectionStore) {
		fun install(key: Long, section: ImmutableSnapshotSection) = synchronized(this) {
			store = store.with(key, section)
		}

		fun remove(keys: LongArrayList) = synchronized(this) {
			store = store.without(keys)
		}
	}

	private val store: SectionStore get() = storeRef.store

	internal fun hasSection(key: Long): Boolean = store.contains(key)

	internal fun install(key: Long, section: ImmutableSnapshotSection) = storeRef.install(key, section)

	internal fun removeSections(keys: LongArrayList) = storeRef.remove(keys)

	internal inline fun forEachSectionKey(action: (Long) -> Unit) = store.forEach { key, _ -> action(key) }

	internal val sectionCount: Int get() = store.size

	internal fun forEachInstalledBlock(action: (Long, SnapshotBlockPhysics) -> Unit) {
		store.forEach { key, section ->
			val sectionX = ChunkSectionPos.unpackX(key)
			val sectionY = ChunkSectionPos.unpackY(key)
			val sectionZ = ChunkSectionPos.unpackZ(key)
			for (localY in 0..15) for (localZ in 0..15) for (localX in 0..15) {
				val x = (sectionX shl 4) + localX
				val y = (sectionY shl 4) + localY
				val z = (sectionZ shl 4) + localZ
				if (inBounds(x, y, z)) action(BlockPos.asLong(x, y, z), section[x, y, z])
			}
		}
	}

	override val simulableStanceY: IntRange = bounds.simulableStanceY

	override val revision: Any get() = store

	override fun mayBounce(): Boolean = store.bouncySections > 0

	override fun cursor(): CoarseVoxelView = SectionCursor()

	override fun isKnown(x: Int, y: Int, z: Int): Boolean = isKnownIn(x, y, z) { store[it] }

	override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = voxelIn(x, y, z) { store[it] }

	override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape = collisionShapeIn(x, y, z) { store[it] }

	override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass = collisionClassIn(x, y, z) { store[it] }

	override fun slipperiness(pos: BlockPos): Double = checkedBlockAt(pos).slipperiness

	override fun velocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos).velocityMultiplier

	override fun jumpVelocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos).jumpVelocityMultiplier

	override fun bounceFactor(pos: BlockPos): Double = checkedBlockAt(pos).bounceFactor

	override fun dampensSteppingSpeed(pos: BlockPos): Boolean = checkedBlockAt(pos).dampensSteppingSpeed

	override fun isFenceLike(pos: BlockPos): Boolean = checkedBlockAt(pos).fenceLike

	override fun isClimbable(pos: BlockPos): Boolean {
		onRead?.invoke(pos)
		return blockIn(pos.x, pos.y, pos.z) { store[it] }?.coarseVoxel?.medium == Medium.CLIMBABLE
	}

	override fun adjustMovementForCollisions(
		movement: Vec3d,
		boundingBox: Box,
		onGround: Boolean,
		stepHeight: Double,
	): Vec3d = VanillaBlockCollisionResolver.adjust(movement, boundingBox, onGround, stepHeight) { query, collisions ->
		forEachCollisionShape(query) { pos, shape -> collisions.add(pos.x, pos.y, pos.z, shape) }
	}

	override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? {
		val sections = PinnedSections(store)
		return closestSupportingBlock(box, entityPos) { pos ->
			onRead?.invoke(pos)
			val block = exactBlock(pos, sections::at)
			!block.collisionShape.isEmpty && block.intersectsCollisionBox(box, pos.x, pos.y, pos.z)
		}
	}

	override fun isSpaceEmpty(box: Box): Boolean {
		val query = VoxelShapes.cuboid(box)
		val shapes = ArrayList<VoxelShape>()
		forEachCollisionShape(box) { pos, shape ->
			shapes += shape.offset(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
		}
		return shapes.none { shape -> VoxelShapes.matchesAnywhere(shape, query, BooleanBiFunction.AND) }
	}

	fun trackingView(): TrackedSnapshotSimulationEnvironment = TrackedSnapshotSimulationEnvironment(this)

	private fun observedBy(onRead: (BlockPos) -> Unit) =
		SnapshotSimulationEnvironment(bounds, storeRef, defaultBlock, onExactMiss, onRead)

	private inline fun forEachCollisionShape(query: Box, action: (BlockPos, VoxelShape) -> Unit) {
		val sections = PinnedSections(store)
		forEachCollisionCandidate(query) { pos ->
			onRead?.invoke(pos)
			val block = exactBlock(pos, sections::at)
			val unsupported = block.unsupportedPhysics
			if (unsupported != null && query.intersectsUnitBlock(pos.x, pos.y, pos.z)) {
				throw UnsupportedBlockPhysicsException(pos.toImmutable(), unsupported)
			}
			if (!block.collisionShape.isEmpty) action(pos, block.collisionShape)
		}
	}

	private fun checkedBlockAt(pos: BlockPos): SnapshotBlockPhysics {
		onRead?.invoke(pos)
		val block = exactBlock(pos) { store[it] }
		val unsupported = block.unsupportedPhysics
		if (unsupported != null) throw UnsupportedBlockPhysicsException(pos.toImmutable(), unsupported)
		return block
	}

	private inline fun exactBlock(pos: BlockPos, sectionAt: (Long) -> ImmutableSnapshotSection?): SnapshotBlockPhysics {
		if (pos !in bounds) throw SimulationSnapshotOutOfBoundsException(pos.toImmutable())
		val key = ChunkSectionPos.asLong(pos.x shr 4, pos.y shr 4, pos.z shr 4)
		return sectionAt(key)?.get(pos.x, pos.y, pos.z) ?: defaultBlock ?: missingExact(pos, key)
	}

	private fun missingExact(pos: BlockPos, key: Long): Nothing {
		onExactMiss?.invoke(key)
		throw SnapshotSectionUnavailableException(pos.x shr 4, pos.y shr 4, pos.z shr 4)
	}

	private inline fun blockIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): SnapshotBlockPhysics? =
		sectionAt(ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4))?.get(x, y, z) ?: defaultBlock

	private inline fun isKnownIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): Boolean =
		inBounds(x, y, z) && blockIn(x, y, z, sectionAt) != null

	private inline fun voxelIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): CoarseVoxel {
		if (!inBounds(x, y, z)) return CoarseVoxel.UNKNOWN
		val block = blockIn(x, y, z, sectionAt) ?: return CoarseVoxel.UNKNOWN
		return if (block.unsupportedPhysics == null) block.coarseVoxel else CoarseVoxel.HAZARD
	}

	private inline fun collisionShapeIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): VoxelShape {
		if (!inBounds(x, y, z)) return VoxelShapes.fullCube()
		val block = blockIn(x, y, z, sectionAt) ?: return VoxelShapes.empty()
		return if (block.unsupportedPhysics == null) block.collisionShape else VoxelShapes.fullCube()
	}

	private inline fun collisionClassIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): CollisionClass {
		if (!inBounds(x, y, z)) return CollisionClass.FULL
		return blockIn(x, y, z, sectionAt)?.collisionClass ?: CollisionClass.EMPTY
	}

	private fun inBounds(x: Int, y: Int, z: Int): Boolean =
		x in bounds.minX..bounds.maxX && y in bounds.minY..bounds.maxY && z in bounds.minZ..bounds.maxZ

	private fun Box.intersectsUnitBlock(x: Int, y: Int, z: Int): Boolean =
		maxX > x && minX < x + 1.0 &&
				maxY > y && minY < y + 1.0 &&
				maxZ > z && minZ < z + 1.0

	private class PinnedSections(private val store: SectionStore) {
		private var resolved = false
		private var key = 0L
		private var section: ImmutableSnapshotSection? = null

		fun at(key: Long): ImmutableSnapshotSection? {
			if (!resolved || key != this.key) {
				this.key = key
				section = store[key]
				resolved = true
			}
			return section
		}
	}

	private inner class SectionCursor : CoarseVoxelView {
		private var cached: SectionStore? = null
		private var filled = 0L
		private val keys = LongArray(CURSOR_SLOTS)
		private val sections = arrayOfNulls<ImmutableSnapshotSection>(CURSOR_SLOTS)

		override val simulableStanceY: IntRange get() = this@SnapshotSimulationEnvironment.simulableStanceY

		override val revision: Any get() = store

		override fun mayBounce(): Boolean = this@SnapshotSimulationEnvironment.mayBounce()

		override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = voxelIn(x, y, z, ::at)

		override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape = collisionShapeIn(x, y, z, ::at)

		override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass = collisionClassIn(x, y, z, ::at)

		override fun isKnown(x: Int, y: Int, z: Int): Boolean = isKnownIn(x, y, z, ::at)

		private fun at(key: Long): ImmutableSnapshotSection? {
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
	}

	class TrackedSnapshotSimulationEnvironment private constructor(
		private val frameReads: HashSet<VoxelPos>,
		snapshot: SnapshotSimulationEnvironment,
	) : SimulationEnvironment by snapshot.observedBy({ pos -> frameReads += VoxelPos(pos.x, pos.y, pos.z) }) {
		internal constructor(snapshot: SnapshotSimulationEnvironment) : this(HashSet(), snapshot)

		fun takeFrameDependencies(): Set<VoxelPos> =
			Collections.unmodifiableSet(HashSet(frameReads)).also { frameReads.clear() }
	}

	companion object {
		private const val CURSOR_SLOTS = 64

		internal fun streaming(
			bounds: SimulationSnapshotBounds,
			onExactMiss: (Long) -> Unit,
		): SnapshotSimulationEnvironment =
			SnapshotSimulationEnvironment(bounds, StoreRef(SectionStore.EMPTY), defaultBlock = null, onExactMiss = onExactMiss)

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
}
