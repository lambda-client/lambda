/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.world.snapshot

import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.SimulationEnvironment
import com.lambda.pathing.physics.SimulationSnapshotOutOfBoundsException
import com.lambda.pathing.physics.SnapshotSectionUnavailableException
import com.lambda.pathing.physics.UnsupportedBlockPhysicsException
import com.lambda.pathing.physics.VanillaBlockCollisionResolver
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.Medium
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import java.util.Collections
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.function.BooleanBiFunction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.World

/**
 * An immutable, palette-compressed block snapshot serving both the physics
 * ([SimulationEnvironment]) and the coarse ([CoarseVoxelView]) contracts.
 *
 * Sections live in a copy-on-write [SectionStore] behind a shared [StoreRef], so a
 * streaming owner ([com.lambda.pathing.world.PathingWorld]) can install and drop
 * sections while planner threads read a consistent table. When [sparse] is set, a key
 * absent from the store is *unavailable* (not yet captured); otherwise absent cells fall
 * back to [defaultBlock]. [missingSection] and [unavailableSectionKeys] are seams for
 * lazily generated fixtures. [edits] overlays planned block changes on top of the store.
 */
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

    /** The mutable cell of a copy-on-write table; writers serialise on the ref itself. */
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

    /** This snapshot with [edits] applied on top of the shared section table. */
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

    internal fun storageStats(): SnapshotStorageStats {
        var palette = 0
        var bytes = 0
        val store = store
        store.forEach { _, section ->
            palette += section.paletteSize
            bytes += section.indexStorageBytes
        }
        return SnapshotStorageStats(sections = store.size, paletteEntries = palette, indexBytes = bytes)
    }

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
                val physics = blockInside(x, y, z) ?: error("Incomplete snapshot at ($x,$y,$z)")
                action(BlockPos.asLong(x, y, z), physics)
            }
        }
    }

    override val simulableStanceY: IntRange = bounds.simulableStanceY

    private fun inBounds(x: Int, y: Int, z: Int): Boolean =
        x in bounds.minX..bounds.maxX && y in bounds.minY..bounds.maxY && z in bounds.minZ..bounds.maxZ

    override fun isKnown(x: Int, y: Int, z: Int): Boolean {
        if (!inBounds(x, y, z)) return false
        val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        val installed = store[key]
        physicsIn(resolveSection(installed, key, x shr 4, y shr 4, z shr 4, exact = false), x, y, z) ?: return false
        return !isUnavailable(key, installed)
    }

    internal fun isUnavailable(x: Int, y: Int, z: Int): Boolean {
        val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        return isUnavailable(key, store[key])
    }

    private fun isUnavailable(key: Long, installed: ImmutableSnapshotSection?): Boolean =
        (sparse && installed == null) || unavailableSectionKeys?.contains(key) == true

    override fun slipperiness(pos: BlockPos): Double = checkedBlockAt(pos, null).slipperiness

    override fun velocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos, null).velocityMultiplier

    override fun jumpVelocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos, null).jumpVelocityMultiplier

    override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel {
        if (!inBounds(x, y, z)) return CoarseVoxel.UNKNOWN
        val block = blockInside(x, y, z) ?: return CoarseVoxel.UNKNOWN
        return if (block.unsupportedPhysics == null) block.coarseVoxel else CoarseVoxel.HAZARD
    }

    override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape {
        if (!inBounds(x, y, z)) return VoxelShapes.fullCube()
        val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        val installed = store[key]
        if (isUnavailable(key, installed)) return VoxelShapes.empty()
        val block = physicsIn(resolveSection(installed, key, x shr 4, y shr 4, z shr 4, exact = false), x, y, z)
            ?: return VoxelShapes.fullCube()
        return if (block.unsupportedPhysics == null) block.collisionShape else VoxelShapes.fullCube()
    }

    override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass {
        if (!inBounds(x, y, z)) return CollisionClass.FULL
        val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        val installed = store[key]
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
        collisionShapes = { collectBlockShapes(it, observer) },
    )

    private fun collectBlockShapes(query: Box, observer: SnapshotReadObserver?): List<VoxelShape> {
        val minX = MathHelper.floor(query.minX - COLLISION_EPSILON) - 1
        val maxX = MathHelper.floor(query.maxX + COLLISION_EPSILON) + 1
        val minY = MathHelper.floor(query.minY - COLLISION_EPSILON) - 1
        val maxY = MathHelper.floor(query.maxY + COLLISION_EPSILON) + 1
        val minZ = MathHelper.floor(query.minZ - COLLISION_EPSILON) - 1
        val maxZ = MathHelper.floor(query.maxZ + COLLISION_EPSILON) + 1

        var result: ArrayList<VoxelShape>? = null
        val mutable = BlockPos.Mutable()
        val cursor = ExactReadCursor(store)
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val pos = mutable.set(x, y, z)
                    observer?.onRead(pos)
                    val block = cursor.blockAt(pos)
                    val unsupported = block.unsupportedPhysics
                    if (unsupported != null && query.intersectsUnitBlock(x, y, z)) {
                        throw UnsupportedBlockPhysicsException(pos.toImmutable(), unsupported)
                    }
                    if (block.collisionShape.isEmpty) continue
                    (result ?: ArrayList<VoxelShape>().also { result = it }) +=
                        block.collisionShape.offset(x.toDouble(), y.toDouble(), z.toDouble())
                }
            }
        }
        return result ?: emptyList()
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
        blockInside(pos.x, pos.y, pos.z)?.coarseVoxel?.medium == Medium.CLIMBABLE

    override fun bounceFactor(pos: BlockPos): Double = checkedBlockAt(pos, null).bounceFactor

    override fun dampensSteppingSpeed(pos: BlockPos): Boolean =
        checkedBlockAt(pos, null).dampensSteppingSpeed

    private fun isFenceLike(pos: BlockPos, observer: SnapshotReadObserver?): Boolean =
        checkedBlockAt(pos, observer).fenceLike

    /**
     * @see net.minecraft.world.CollisionView.findSupportingBlockPos
     */
    private fun findSupportingBlockPos(
        box: Box,
        entityPos: Vec3d,
        observer: SnapshotReadObserver?,
    ): BlockPos? {
        val minX = MathHelper.floor(box.minX - COLLISION_EPSILON) - 1
        val maxX = MathHelper.floor(box.maxX + COLLISION_EPSILON) + 1
        val minY = MathHelper.floor(box.minY - COLLISION_EPSILON) - 1
        val maxY = MathHelper.floor(box.maxY + COLLISION_EPSILON) + 1
        val minZ = MathHelper.floor(box.minZ - COLLISION_EPSILON) - 1
        val maxZ = MathHelper.floor(box.maxZ + COLLISION_EPSILON) + 1

        var best: BlockPos? = null
        var bestDistance = Double.MAX_VALUE
        val mutable = BlockPos.Mutable()
        val cursor = ExactReadCursor(store)

        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val pos = mutable.set(x, y, z)
                    observer?.onRead(pos)
                    val block = cursor.blockAt(pos)
                    if (block.collisionShape.isEmpty) continue

                    if (!block.intersectsCollisionBox(box, x, y, z)) continue

                    val candidate = pos.toImmutable()
                    val distance = candidate.getSquaredDistance(entityPos)
                    if (distance < bestDistance ||
                        (distance == bestDistance && (best == null || best < candidate))
                    ) {
                        best = candidate
                        bestDistance = distance
                    }
                }
            }
        }
        return best
    }

    /**
     * Exact reads for one collision query: resolves the section once per section the
     * scan crosses instead of once per cell, and throws the same way [blockAt] does.
     */
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

    private fun blockInside(x: Int, y: Int, z: Int): SnapshotBlockPhysics? {
        val sectionX = x shr 4
        val sectionY = y shr 4
        val sectionZ = z shr 4
        val key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
        return physicsIn(resolveSection(store[key], key, sectionX, sectionY, sectionZ, exact = false), x, y, z)
    }

    /** The installed section unless an exact read must not trust an unavailable key; else the lazy fallback. */
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
        private const val COLLISION_EPSILON = 1.0E-7

        internal fun beginCapture(
            world: World,
            player: ClientPlayerEntity,
            bounds: SimulationSnapshotBounds,
        ): SnapshotCaptureJob = SnapshotCaptureJob(world, player, bounds)

        fun capture(
            world: World,
            player: ClientPlayerEntity,
            bounds: SimulationSnapshotBounds,
        ): SnapshotSimulationEnvironment {
            val capture = beginCapture(world, player, bounds)
            while (true) {
                when (val result = capture.advance(Int.MAX_VALUE, Long.MAX_VALUE)) {
                    is SnapshotCaptureResult.Complete -> return result.snapshot
                    is SnapshotCaptureResult.Failed -> error(result.message)
                    is SnapshotCaptureResult.Progress -> Unit
                }
            }
        }

        /** A streaming snapshot whose sections are installed by its owner over time. */
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
                val sectionKey = ChunkSectionPos.asLong(pos.x shr 4, pos.y shr 4, pos.z shr 4)
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
        private val reads = HashSet<VoxelPos>()
        private val pendingFrameReads = HashSet<VoxelPos>()
        private val observer = SnapshotReadObserver { pos ->
            val voxel = VoxelPos(pos.x, pos.y, pos.z)
            reads += voxel
            pendingFrameReads += voxel
        }

        fun dependencies(): Set<VoxelPos> = Collections.unmodifiableSet(HashSet(reads))

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
