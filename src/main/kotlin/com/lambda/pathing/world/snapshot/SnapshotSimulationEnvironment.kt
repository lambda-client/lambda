package com.lambda.pathing.world.snapshot

import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.FallRule
import com.lambda.pathing.physics.SimulationEnvironment
import com.lambda.pathing.physics.SimulationSnapshotOutOfBoundsException
import com.lambda.pathing.physics.SnapshotSectionUnavailableException
import com.lambda.pathing.physics.UnsupportedBlockPhysicsException
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.physics.VanillaBlockCollisionResolver
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.physics.closestSupportingBlock
import com.lambda.pathing.physics.collisionCandidateRange
import com.lambda.pathing.physics.forEachCollisionCandidate
import com.lambda.pathing.world.CellClass
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.RememberedTerrain
import it.unimi.dsi.fastutil.longs.LongArrayList
import net.minecraft.util.function.BooleanBiFunction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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

	internal class StoreRef(@Volatile var store: SectionStore)

	private val store: SectionStore get() = storeRef.store

	internal fun hasSection(key: Long): Boolean = store.contains(key)

	internal fun section(key: Long): ImmutableSnapshotSection? = store[key]

	internal fun installedBlock(x: Int, y: Int, z: Int): SnapshotBlockPhysics? = blockIn(x, y, z) { store[it] }

	internal fun install(key: Long, section: ImmutableSnapshotSection) = synchronized(storeRef) {
		storeRef.store = storeRef.store.with(key, section)
	}

	internal fun removeSections(keys: LongArrayList) = synchronized(storeRef) {
		storeRef.store = storeRef.store.without(keys)
	}

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
				if (bounds.contains(x, y, z)) action(BlockPos.asLong(x, y, z), section[x, y, z])
			}
		}
	}

	override val simulableStanceY: IntRange = bounds.simulableStanceY

	override val revision: Any get() = store

	override val mayBounce: Boolean get() = store.bouncySections > 0

	override fun mayBounceWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		store.bouncyWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override fun maySoftLandWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		store.softWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override val maySoftLand: Boolean get() = store.softSections > 0

	override fun cursor(): CoarseVoxelView = SectionCursor(this) { store[it] }

	internal fun remembering(remembered: (Long) -> ImmutableSnapshotSection?, loaded: (Int, Int) -> Boolean): RememberingView =
		RememberingView(remembered, loaded)

	override fun classifySection(sectionX: Int, sectionY: Int, sectionZ: Int, planes: Array<LongArray>) {
		if (!classifySectionIn(sectionX, sectionY, sectionZ, planes) { store[it] }) {
			super.classifySection(sectionX, sectionY, sectionZ, planes)
		}
	}

	private inline fun classifySectionIn(
		sectionX: Int,
		sectionY: Int,
		sectionZ: Int,
		planes: Array<LongArray>,
		sectionAt: (Long) -> ImmutableSnapshotSection?,
	): Boolean {
		val minX = sectionX shl 4
		val minY = sectionY shl 4
		val minZ = sectionZ shl 4
		if (!bounds.contains(minX, minY, minZ) || !bounds.contains(minX + 15, minY + 15, minZ + 15)) return false
		val section = sectionAt(ChunkSectionPos.asLong(sectionX, sectionY, sectionZ))
		if (section != null) section.classify(planes)
		else (defaultBlock?.cellClass ?: CellClass.SPECIAL).fill(planes)
		return true
	}

	override fun isKnown(x: Int, y: Int, z: Int): Boolean = isKnownIn(x, y, z) { store[it] }

	override fun isUnknown(x: Int, y: Int, z: Int): Boolean = isUnknownIn(x, y, z) { store[it] }

	override fun forEachKnownSection(action: (Long) -> Unit) = store.forEach { key, _ -> action(key) }

	override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = voxelIn(x, y, z) { store[it] }

	override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape = collisionShapeIn(x, y, z) { store[it] }

	override fun collisionBoxes(x: Int, y: Int, z: Int): DoubleArray = collisionBoxesIn(x, y, z) { store[it] }

	override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass = collisionClassIn(x, y, z) { store[it] }

	override fun slipperiness(pos: BlockPos): Double = blockAt(pos).slipperiness

	override fun velocityMultiplier(pos: BlockPos): Double = blockAt(pos).velocityMultiplier

	override fun jumpVelocityMultiplier(pos: BlockPos): Double = blockAt(pos).jumpVelocityMultiplier

	override fun bounceFactor(pos: BlockPos): Double = blockAt(pos).bounceFactor

	override fun dampensSteppingSpeed(pos: BlockPos): Boolean = blockAt(pos).dampensSteppingSpeed

	override fun hotFloor(pos: BlockPos): Boolean = blockAt(pos).hotFloor

	override fun fallRule(pos: BlockPos): FallRule = blockAt(pos).fallRule

	override fun fallRule(x: Int, y: Int, z: Int): FallRule = fallRuleIn(x, y, z) { store[it] }

	override fun mayContainHotFloor(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		store.hotWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override fun isFenceLike(pos: BlockPos): Boolean = blockAt(pos).fenceLike

	override fun water(pos: BlockPos): WaterPhysics? = blockAt(pos).water

	override fun mayContainWater(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
		store.waterWithin(minX, minY, minZ, maxX, maxY, maxZ)

	override fun containsFluid(box: Box): Boolean {
		val cell = BlockPos.Mutable()
		for (x in MathHelper.floor(box.minX) until MathHelper.ceil(box.maxX)) {
			for (y in MathHelper.floor(box.minY) until MathHelper.ceil(box.maxY)) {
				for (z in MathHelper.floor(box.minZ) until MathHelper.ceil(box.maxZ)) {
					val block = blockAt(cell.set(x, y, z))
					if (block.water != null || block.unsupportedPhysics?.kind == UnsupportedPhysicsKind.FLUID) return true
				}
			}
		}
		return false
	}

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
	}.also { moved -> rejectUnsupportedContact(boundingBox.stretch(moved)) }

	override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? {
		val sections = PinnedSections(store)
		return closestSupportingBlock(box, entityPos) { pos ->
			onRead?.invoke(pos)
			val block = exactBlock(pos, sections::at)
			block.collides && block.intersectsCollisionBox(box, pos.x, pos.y, pos.z)
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
		val minX = MathHelper.floor(query.minX - Vanilla.COLLISION_EPSILON)
		val maxX = MathHelper.floor(query.maxX + Vanilla.COLLISION_EPSILON)
		val minY = MathHelper.floor(query.minY - Vanilla.COLLISION_EPSILON)
		val maxY = MathHelper.floor(query.maxY + Vanilla.COLLISION_EPSILON)
		val minZ = MathHelper.floor(query.minZ - Vanilla.COLLISION_EPSILON)
		val maxZ = MathHelper.floor(query.maxZ + Vanilla.COLLISION_EPSILON)
		forEachCollisionCandidate(query) { pos ->
			val margins = (if (pos.x !in minX..maxX) 1 else 0) + (if (pos.y !in minY..maxY) 1 else 0) +
					(if (pos.z !in minZ..maxZ) 1 else 0)
			if (margins > 1) return@forEachCollisionCandidate
			onRead?.invoke(pos)
			val block = exactBlock(pos, sections::at)
			if (!block.collides || margins == 1 && !block.exceedsCube) return@forEachCollisionCandidate
			if (block.intersectsCollisionBox(query, pos.x, pos.y, pos.z)) action(pos, block.collisionShape)
		}
	}

	private fun blockAt(pos: BlockPos): SnapshotBlockPhysics {
		onRead?.invoke(pos)
		return exactBlock(pos) { store[it] }
	}

	private fun rejectUnsupportedContact(box: Box) {
		val pinned = store
		if (onRead == null && clearOfUnsupported(box, pinned)) return
		val sections = PinnedSections(pinned)
		forEachCollisionCandidate(box) { pos ->
			if (!box.intersectsUnitBlock(pos.x, pos.y, pos.z)) return@forEachCollisionCandidate
			onRead?.invoke(pos)
			val unsupported = exactBlock(pos, sections::at).unsupportedPhysics ?: return@forEachCollisionCandidate
			throw UnsupportedBlockPhysicsException(pos.toImmutable(), unsupported)
		}
	}

	private fun clearOfUnsupported(box: Box, pinned: SectionStore): Boolean = collisionCandidateRange(box) { minX, minY, minZ, maxX, maxY, maxZ ->
		if (!bounds.contains(minX, minY, minZ) || !bounds.contains(maxX, maxY, maxZ)) return false
		for (sectionX in (minX shr 4)..(maxX shr 4)) for (sectionY in (minY shr 4)..(maxY shr 4)) for (sectionZ in (minZ shr 4)..(maxZ shr 4)) {
			val section = pinned[ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)] ?: return false
			if (section.unsupported) return false
		}
		true
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
		bounds.contains(x, y, z) && blockIn(x, y, z, sectionAt) != null

	private inline fun isUnknownIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): Boolean =
		bounds.contains(x, y, z) && blockIn(x, y, z, sectionAt) == null

	private inline fun voxelIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): CoarseVoxel {
		if (!bounds.contains(x, y, z)) return CoarseVoxel.UNKNOWN
		val block = blockIn(x, y, z, sectionAt) ?: return CoarseVoxel.UNKNOWN
		return if (block.unsupportedPhysics == null) block.coarseVoxel else CoarseVoxel.HAZARD
	}

	private inline fun collisionShapeIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): VoxelShape {
		if (!bounds.contains(x, y, z)) return VoxelShapes.fullCube()
		return blockIn(x, y, z, sectionAt)?.coarseShape ?: VoxelShapes.empty()
	}

	private inline fun collisionBoxesIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): DoubleArray {
		if (!bounds.contains(x, y, z)) return SnapshotBlockPhysics.FULL_CUBE.coarseBoxes
		return blockIn(x, y, z, sectionAt)?.coarseBoxes ?: SnapshotBlockPhysics.AIR.coarseBoxes
	}

	private inline fun fallRuleIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): FallRule {
		if (!bounds.contains(x, y, z)) return FallRule.NORMAL
		return blockIn(x, y, z, sectionAt)?.fallRule ?: FallRule.NORMAL
	}

	private inline fun collisionClassIn(x: Int, y: Int, z: Int, sectionAt: (Long) -> ImmutableSnapshotSection?): CollisionClass {
		if (!bounds.contains(x, y, z)) return CollisionClass.FULL
		return blockIn(x, y, z, sectionAt)?.collisionClass ?: CollisionClass.EMPTY
	}

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

	internal inner class RememberingView(
		private val remembered: (Long) -> ImmutableSnapshotSection?,
		private val loaded: (Int, Int) -> Boolean,
	) : CoarseVoxelView, RememberedTerrain {
		@Volatile
		private var forgotten = false

		private val recalled = ConcurrentHashMap.newKeySet<Long>()

		@Volatile
		private var forgottenRevision: Pair<SectionStore, Unit>? = null

		override val simulableStanceY: IntRange get() = this@SnapshotSimulationEnvironment.simulableStanceY

		override val revision: Any
			get() {
				val current = store
				if (!forgotten) return current
				forgottenRevision?.takeIf { it.first === current }?.let { return it }
				return (current to Unit).also { forgottenRevision = it }
			}

		override fun remembers(box: CellBox): Boolean {
			if (forgotten || recalled.isEmpty()) return false
			for (sx in (box.xs.first shr 4)..(box.xs.last shr 4)) for (sz in (box.zs.first shr 4)..(box.zs.last shr 4)) {
				for (sy in (maxOf(box.ys.first, bounds.minY) shr 4)..(minOf(box.ys.last, bounds.maxY) shr 4)) {
					if (ChunkSectionPos.asLong(sx, sy, sz) in recalled) return true
				}
			}
			return false
		}

		override fun recall(around: Stance): List<CellBox> {
			if (forgotten) return emptyList()
			val boxes = ArrayList<CellBox>()
			for (sx in (around.x shr 4) - RECALL_CHUNKS..(around.x shr 4) + RECALL_CHUNKS) {
				for (sz in (around.z shr 4) - RECALL_CHUNKS..(around.z shr 4) + RECALL_CHUNKS) {
					if (loaded(sx, sz)) continue
					for (sy in (around.y shr 4) - 1..(around.y shr 4) + 1) {
						val key = ChunkSectionPos.asLong(sx, sy, sz)
						if (store[key] != null || key in recalled || remembered(key) == null || !recalled.add(key)) continue
						boxes += CellBox((sx shl 4)..(sx shl 4) + 15, (sy shl 4)..(sy shl 4) + 15, (sz shl 4)..(sz shl 4) + 15)
					}
				}
			}
			return boxes
		}

		override fun forget(): Boolean {
			if (forgotten || recalled.all { store[it] != null }) return false
			forgotten = true
			return true
		}

		override fun forEachKnownSection(action: (Long) -> Unit) {
			store.forEach { key, _ -> action(key) }
			if (!forgotten) recalled.forEach { key -> if (store[key] == null) action(key) }
		}

		override val mayBounce: Boolean get() = store.bouncySections > 0

		override fun mayBounceWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
			store.bouncyWithin(minX, minY, minZ, maxX, maxY, maxZ)

		override fun maySoftLandWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
			store.softWithin(minX, minY, minZ, maxX, maxY, maxZ)

		override val maySoftLand: Boolean get() = store.softSections > 0

		override fun cursor(): CoarseVoxelView = SectionCursor(this, ::at)

		override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = voxelIn(x, y, z, ::at)

		override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape = collisionShapeIn(x, y, z, ::at)

		override fun collisionBoxes(x: Int, y: Int, z: Int): DoubleArray = collisionBoxesIn(x, y, z, ::at)

		override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass = collisionClassIn(x, y, z, ::at)

		override fun fallRule(x: Int, y: Int, z: Int): FallRule = fallRuleIn(x, y, z, ::at)

		override fun isKnown(x: Int, y: Int, z: Int): Boolean = isKnownIn(x, y, z, ::at)

		override fun isUnknown(x: Int, y: Int, z: Int): Boolean = isUnknownIn(x, y, z, ::at)

		override fun classifySection(sectionX: Int, sectionY: Int, sectionZ: Int, planes: Array<LongArray>) {
			if (!classifySectionIn(sectionX, sectionY, sectionZ, planes, ::at)) {
				super.classifySection(sectionX, sectionY, sectionZ, planes)
			}
		}

		private fun at(key: Long): ImmutableSnapshotSection? = store[key] ?: if (forgotten || key !in recalled) null else remembered(key)
	}

	private inner class SectionCursor(
		private val owner: CoarseVoxelView,
		private val sectionAt: (Long) -> ImmutableSnapshotSection?,
	) : CoarseVoxelView {
		private var cached: Any? = null
		private var filled = 0L
		private val keys = LongArray(CURSOR_SLOTS)
		private val sections = arrayOfNulls<ImmutableSnapshotSection>(CURSOR_SLOTS)

		override val simulableStanceY: IntRange get() = this@SnapshotSimulationEnvironment.simulableStanceY

		override val revision: Any? get() = owner.revision

		override val terrain: Any get() = owner.terrain

		override val mayBounce: Boolean get() = store.bouncySections > 0

		override fun mayBounceWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
			store.bouncyWithin(minX, minY, minZ, maxX, maxY, maxZ)

		override fun maySoftLandWithin(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int): Boolean =
			store.softWithin(minX, minY, minZ, maxX, maxY, maxZ)

		override val maySoftLand: Boolean get() = store.softSections > 0

		override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = voxelIn(x, y, z, ::at)

		override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape = collisionShapeIn(x, y, z, ::at)

		override fun collisionBoxes(x: Int, y: Int, z: Int): DoubleArray = collisionBoxesIn(x, y, z, ::at)

		override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass = collisionClassIn(x, y, z, ::at)

		override fun fallRule(x: Int, y: Int, z: Int): FallRule = fallRuleIn(x, y, z, ::at)

		override fun isKnown(x: Int, y: Int, z: Int): Boolean = isKnownIn(x, y, z, ::at)

		override fun isUnknown(x: Int, y: Int, z: Int): Boolean = isUnknownIn(x, y, z, ::at)

		override fun classifySection(sectionX: Int, sectionY: Int, sectionZ: Int, planes: Array<LongArray>) {
			if (!classifySectionIn(sectionX, sectionY, sectionZ, planes, ::at)) {
				super.classifySection(sectionX, sectionY, sectionZ, planes)
			}
		}

		private fun at(key: Long): ImmutableSnapshotSection? {
			val current = owner.revision
			if (current !== cached) {
				cached = current
				filled = 0L
			}
			val slot = (((key ushr 42).toInt() and 3) shl 4) or ((key.toInt() and 3) shl 2) or ((key ushr 20).toInt() and 3)
			if (filled and (1L shl slot) != 0L && keys[slot] == key) return sections[slot]
			val section = sectionAt(key)
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

		private const val RECALL_CHUNKS = 2

		internal fun streaming(
			bounds: SimulationSnapshotBounds,
			onExactMiss: (Long) -> Unit,
		): SnapshotSimulationEnvironment =
			SnapshotSimulationEnvironment(bounds, StoreRef(SectionStore.EMPTY), defaultBlock = null, onExactMiss = onExactMiss)

		fun synthetic(
			bounds: SimulationSnapshotBounds,
			blocks: Map<BlockPos, SnapshotBlockPhysics>,
		): SnapshotSimulationEnvironment {
			blocks.keys.forEach { pos -> require(pos in bounds) { "Synthetic block $pos lies outside $bounds" } }
			return SnapshotSimulationEnvironment(bounds, ImmutableSnapshotSection.sectionsOf(blocks), SnapshotBlockPhysics.AIR)
		}
	}
}
