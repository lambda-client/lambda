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

package com.lambda.util.player.prediction

import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.Medium
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.function.BooleanBiFunction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.World
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/** Inclusive block bounds captured for one immutable simulation snapshot. */
data class SimulationSnapshotBounds(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
) {
    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "Invalid snapshot bounds: $this" }
    }

    operator fun contains(pos: BlockPos): Boolean =
        pos.x in minX..maxX && pos.y in minY..maxY && pos.z in minZ..maxZ

    /**
     * Stance heights whose whole motion envelope this snapshot covers.
     *
     * A stance is only usable if *every* move the trajectory layer may attempt from it
     * can be simulated, and the tallest of those is a sprint jump: apex 1.2522 blocks
     * above the stance, carrying a 1.8-block body, and collision resolution reads one
     * block past the box -- so it reads [CEILING_REACH] blocks up. The coarse mask
     * inspects only two (§5.1), so it will happily accept a ledge whose jump envelope
     * was never captured. Every jumping candidate from that ledge then dies reading
     * outside the snapshot, and the refusal reads as a physics problem rather than as
     * the capture problem it is.
     */
    val simulableStanceY: IntRange get() = (minY + FLOOR_REACH)..(maxY - CEILING_REACH)

    companion object {
        /** Blocks read above a stance: sprint-jump apex + body height, floored, plus the collision pad. */
        const val CEILING_REACH = 4

        /** Blocks read below a stance: the supporting block, plus the collision pad. */
        const val FLOOR_REACH = 2
    }
}

/** Immutable, context-resolved physics for one captured block position. */
data class SnapshotBlockPhysics(
    val collisionShape: VoxelShape,
    val slipperiness: Double = DEFAULT_SLIPPERINESS,
    val velocityMultiplier: Double = 1.0,
    val jumpVelocityMultiplier: Double = 1.0,
    val unsupportedPhysics: UnsupportedPhysics? = null,
    val coarseVoxel: CoarseVoxel = CoarseVoxel.UNKNOWN,
    /** Fences/walls/gates anchor the velocity-affecting pos to themselves. */
    val fenceLike: Boolean = false,
    /** Reflection applied to downward velocity on landing; zero for an ordinary stop. */
    val bounceFactor: Double = 0.0,
    /** Whether standing here drags a slow-moving body to a crawl, as slime does. */
    val dampensSteppingSpeed: Boolean = false,
) {
    /**
     * Classified once per distinct block, here, because these instances are palette
     * entries: a section holds a handful of them however many cells it has, so the sweep's
     * per-cell question collapses to an index fetch and a field read.
     *
     * Unsupported physics classifies [CollisionClass.FULL] to match [SnapshotSimulationEnvironment.collisionShape],
     * which reports a full cube there so no arc certifies through terrain the simulator
     * would refuse to fly.
     */
    val collisionClass: CollisionClass =
        if (unsupportedPhysics != null) CollisionClass.FULL else CollisionClass.of(collisionShape)

    companion object {
        const val DEFAULT_SLIPPERINESS = 0.6

        val AIR = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.AIR)
        val FULL_CUBE = SnapshotBlockPhysics(VoxelShapes.fullCube(), coarseVoxel = CoarseVoxel.FULL_BLOCK)

        /**
         * A block of arbitrary shape, with its coarse traits derived rather than declared.
         *
         * Shares [SnapshotSimulationEnvironment.coarseVoxelOf] with the live capture on
         * purpose: a synthetic slab that the planner reads differently from a real one
         * would make every test over non-cube terrain a test of the fixture.
         */
        fun of(
            shape: VoxelShape,
            slipperiness: Double = DEFAULT_SLIPPERINESS,
            velocityMultiplier: Double = 1.0,
            jumpVelocityMultiplier: Double = 1.0,
            fenceLike: Boolean = false,
            bounceFactor: Double = 0.0,
            dampensSteppingSpeed: Boolean = false,
        ) = SnapshotBlockPhysics(
            collisionShape = shape,
            slipperiness = slipperiness,
            velocityMultiplier = velocityMultiplier,
            jumpVelocityMultiplier = jumpVelocityMultiplier,
            coarseVoxel = SnapshotSimulationEnvironment.coarseVoxelOf(shape, bouncy = bounceFactor > 0.0),
            fenceLike = fenceLike,
            bounceFactor = bounceFactor,
            dampensSteppingSpeed = dampensSteppingSpeed,
        )
        /** Exact simulation never consumes this placeholder. */
        val UNAVAILABLE = SnapshotBlockPhysics(VoxelShapes.fullCube(), coarseVoxel = CoarseVoxel.UNKNOWN)
    }
}

enum class UnsupportedPhysicsKind {
    FLUID,
    CLIMBABLE,
    COBWEB,
    POWDER_SNOW,
    HONEY_SIDE_EFFECTS,
}

data class UnsupportedPhysics(
    val kind: UnsupportedPhysicsKind,
    val blockId: String? = null,
)

sealed class SimulationEnvironmentException(message: String) : IllegalStateException(message)

class SimulationSnapshotOutOfBoundsException(val pos: BlockPos) :
    SimulationEnvironmentException("Movement simulation read outside its snapshot at $pos")

class UnsupportedBlockPhysicsException(val pos: BlockPos, val physics: UnsupportedPhysics) :
    SimulationEnvironmentException("Unsupported movement physics at $pos: $physics")

/**
 * An exact read reached terrain the client has not streamed yet.
 *
 * Waiting for it would park the planner on a chunk that only the player's own motion
 * can bring into view, so the rollout that read it is rejected instead: everything
 * certified before the streaming frontier survives, and the branch that tried to cross
 * it dies like any other impassable terrain.
 */
class SnapshotSectionUnavailableException(val sectionX: Int, val sectionY: Int, val sectionZ: Int) :
    SimulationEnvironmentException(
        "Movement simulation read unloaded terrain in section ($sectionX, $sectionY, $sectionZ)"
    )

private fun interface SnapshotReadObserver {
    fun onRead(pos: BlockPos)
}

internal sealed interface SnapshotCaptureResult {
    data class Progress(val capturedCells: Long, val totalCells: Long) : SnapshotCaptureResult
    data class Complete(val snapshot: SnapshotSimulationEnvironment) : SnapshotCaptureResult
    data class Failed(val message: String) : SnapshotCaptureResult
}

/**
 * Resumable client-thread snapshot builder.
 *
 * A call to [advance] is bounded by both a cell quota and a monotonic deadline. The
 * mutable builders never escape this job; completion freezes every section before the
 * snapshot is handed to the worker thread.
 */
internal class SnapshotCaptureJob(
    private val world: World,
    player: ClientPlayerEntity,
    val bounds: SimulationSnapshotBounds,
) {
    private val sections = HashMap<Long, ImmutableSnapshotSection>()
    private val mutable = BlockPos.Mutable()
    private val shapeContext = ShapeContext.of(player)

    private val minSectionX = bounds.minX shr 4
    private val maxSectionX = bounds.maxX shr 4
    private val minSectionY = bounds.minY shr 4
    private val maxSectionY = bounds.maxY shr 4
    private val minSectionZ = bounds.minZ shr 4
    private val maxSectionZ = bounds.maxZ shr 4

    private var sectionX = minSectionX
    private var sectionY = minSectionY
    private var sectionZ = minSectionZ
    private var x = sectionMinX()
    private var y = sectionMinY()
    private var z = sectionMinZ()
    private var builder = ImmutableSnapshotSection.Builder()
    private var sectionWrites = 0
    private var capturedCells = 0L
    private var completed: SnapshotSimulationEnvironment? = null

    val totalCells: Long =
        (bounds.maxX.toLong() - bounds.minX + 1L) *
            (bounds.maxY.toLong() - bounds.minY + 1L) *
            (bounds.maxZ.toLong() - bounds.minZ + 1L)

    fun advance(maxCells: Int, deadlineNanos: Long): SnapshotCaptureResult {
        check(MinecraftClient.getInstance().isOnThread) {
            "Simulation snapshots must be captured on the client thread"
        }
        require(maxCells > 0) { "Snapshot capture cell quota must be positive" }
        completed?.let { return SnapshotCaptureResult.Complete(it) }

        var capturedThisCall = 0
        // Always make at least one-cell progress so an aggressively small budget or
        // a one-off JIT pause cannot starve capture forever.
        while (capturedThisCall < maxCells &&
            (capturedThisCall == 0 || System.nanoTime() < deadlineNanos)
        ) {
            if (!world.isChunkLoaded(sectionX, sectionZ)) {
                return SnapshotCaptureResult.Failed(
                    "snapshot requires unloaded chunk ($sectionX, $sectionZ); move closer and retry",
                )
            }

            val pos = mutable.set(x, y, z)
            val physics = with(SnapshotSimulationEnvironment) {
                world.getBlockState(pos).capturePhysics(world, pos, shapeContext)
            }
            builder.set(x, y, z, physics)
            sectionWrites++
            capturedCells++
            capturedThisCall++

            if (advanceCell()) {
                sections[ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)] =
                    builder.build(expectedWrites = sectionWrites)
                if (!advanceSection()) {
                    val snapshot = SnapshotSimulationEnvironment(bounds, sections, defaultBlock = null)
                    completed = snapshot
                    return SnapshotCaptureResult.Complete(snapshot)
                }
                builder = ImmutableSnapshotSection.Builder()
                sectionWrites = 0
                x = sectionMinX()
                y = sectionMinY()
                z = sectionMinZ()
            }
        }
        return SnapshotCaptureResult.Progress(capturedCells, totalCells)
    }

    /** True when the current section has been fully traversed. */
    private fun advanceCell(): Boolean {
        if (x < sectionMaxX()) {
            x++
            return false
        }
        x = sectionMinX()
        if (z < sectionMaxZ()) {
            z++
            return false
        }
        z = sectionMinZ()
        if (y < sectionMaxY()) {
            y++
            return false
        }
        return true
    }

    /** False when there are no sections left. */
    private fun advanceSection(): Boolean {
        if (sectionX < maxSectionX) {
            sectionX++
            return true
        }
        sectionX = minSectionX
        if (sectionZ < maxSectionZ) {
            sectionZ++
            return true
        }
        sectionZ = minSectionZ
        if (sectionY < maxSectionY) {
            sectionY++
            return true
        }
        return false
    }

    private fun sectionMinX() = maxOf(bounds.minX, sectionX shl 4)
    private fun sectionMaxX() = minOf(bounds.maxX, (sectionX shl 4) + 15)
    private fun sectionMinY() = maxOf(bounds.minY, sectionY shl 4)
    private fun sectionMaxY() = minOf(bounds.maxY, (sectionY shl 4) + 15)
    private fun sectionMinZ() = maxOf(bounds.minZ, sectionZ shl 4)
    private fun sectionMaxZ() = minOf(bounds.maxZ, (sectionZ shl 4) + 15)
}

/**
 * Bounded immutable environment for worker-thread rollouts.
 *
 * Collision shapes and block constants are resolved on the client thread at
 * capture time. Rollouts only read immutable paletted sections and immutable voxel
 * shapes; they never touch chunks, the world, or the live player. Reads beyond
 * [bounds] fail closed instead of treating uncaptured terrain as air.
 */
class SnapshotSimulationEnvironment internal constructor(
    val bounds: SimulationSnapshotBounds,
    sections: Map<Long, ImmutableSnapshotSection>,
    private val defaultBlock: SnapshotBlockPhysics?,
    shareSections: Boolean = false,
    private val missingSection: ((Int, Int, Int, Boolean) -> ImmutableSnapshotSection)? = null,
    private val sparseSectionCoordinates: Map<Long, Triple<Int, Int, Int>>? = null,
    private val unavailableSectionKeys: Set<Long>? = null,
    /** Live predicate for "this section is unknown" -- lets absence itself be the state. */
    private val sectionUnavailable: ((Long) -> Boolean)? = null,
    /** Invoked when an exact read hits unknown terrain, before the typed throw. */
    private val onExactMiss: ((Long) -> Unit)? = null,
) : SimulationEnvironment, CoarseVoxelView {
    private val sections: Map<Long, ImmutableSnapshotSection> = if (shareSections) sections else
        Long2ObjectOpenHashMap<ImmutableSnapshotSection>().apply { putAll(sections) }

    internal fun storageStats() = SnapshotStorageStats(
        sections = this.sections.size,
        paletteEntries = this.sections.values.sumOf(ImmutableSnapshotSection::paletteSize),
        indexBytes = this.sections.values.sumOf(ImmutableSnapshotSection::indexStorageBytes),
    )

    /** Streams captured cells for debug serialization without rebuilding a per-cell map. */
    internal fun forEachSnapshotBlock(action: (Long, SnapshotBlockPhysics) -> Unit) {
        sparseSectionCoordinates?.forEach { (key, coordinate) ->
            val section = sections[key] ?: return@forEach
            val (sectionX, sectionY, sectionZ) = coordinate
            for (localY in 0..15) for (localZ in 0..15) for (localX in 0..15) {
                val x = (sectionX shl 4) + localX
                val y = (sectionY shl 4) + localY
                val z = (sectionZ shl 4) + localZ
                if (x in bounds.minX..bounds.maxX && y in bounds.minY..bounds.maxY &&
                    z in bounds.minZ..bounds.maxZ
                ) action(BlockPos.asLong(x, y, z), section[x, y, z])
            }
        } ?: run {
        for (y in bounds.minY..bounds.maxY) for (z in bounds.minZ..bounds.maxZ) for (x in bounds.minX..bounds.maxX) {
            val physics = blockInside(x, y, z) ?: error("Incomplete snapshot at ($x,$y,$z)")
            action(BlockPos.asLong(x, y, z), physics)
        }
        }
    }

    override val simulableStanceY: IntRange = bounds.simulableStanceY

    override fun isKnown(x: Int, y: Int, z: Int): Boolean {
        if (x !in bounds.minX..bounds.maxX || y !in bounds.minY..bounds.maxY || z !in bounds.minZ..bounds.maxZ) {
            return false
        }
        // Force the lazy classification: a section nobody has asked for yet is neither
        // known nor unavailable until the client either copies it or marks it absent.
        blockInside(x, y, z, exact = false) ?: return false
        return !isUnavailable(x, y, z)
    }

    internal fun isUnavailable(x: Int, y: Int, z: Int): Boolean =
        sectionIsUnavailable(ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4))

    private fun sectionIsUnavailable(key: Long): Boolean =
        sectionUnavailable?.invoke(key) ?: (unavailableSectionKeys?.contains(key) == true)

    override fun slipperiness(pos: BlockPos): Double = checkedBlockAt(pos, null).slipperiness

    override fun velocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos, null).velocityMultiplier

    override fun jumpVelocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos, null).jumpVelocityMultiplier

    /** Planner reads fail closed at snapshot bounds and unsupported physics. */
    override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel {
        if (x !in bounds.minX..bounds.maxX || y !in bounds.minY..bounds.maxY || z !in bounds.minZ..bounds.maxZ) {
            return CoarseVoxel.UNKNOWN
        }
        val block = blockInside(x, y, z, exact = false) ?: return CoarseVoxel.UNKNOWN
        // A block whose physics we refuse to model is still a block we *read*. Reporting it
        // as UNKNOWN also claims it bulges into the cell above, which silently deletes every
        // arc that would clear it -- so a lava pool became unjumpable rather than merely
        // unstandable.
        return if (block.unsupportedPhysics == null) block.coarseVoxel else CoarseVoxel.HAZARD
    }

    /**
     * Fail-closed real shapes for arc masks. A cell the simulator would refuse to move
     * through (lava, cobweb, uncaptured terrain) is a full cube here: an arc that dips
     * into it could never certify, so proposing it would only buy a guaranteed reroute.
     */
    override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape {
        if (x !in bounds.minX..bounds.maxX || y !in bounds.minY..bounds.maxY || z !in bounds.minZ..bounds.maxZ) {
            return VoxelShapes.fullCube()
        }
        val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        if (sectionIsUnavailable(key)) return VoxelShapes.empty()
        val block = blockInside(x, y, z, exact = false) ?: return VoxelShapes.fullCube()
        return if (block.unsupportedPhysics == null) block.collisionShape else VoxelShapes.fullCube()
    }

    /** The classification [collisionShape] would yield, without constructing any shape. */
    override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass {
        if (x !in bounds.minX..bounds.maxX || y !in bounds.minY..bounds.maxY || z !in bounds.minZ..bounds.maxZ) {
            return CollisionClass.FULL
        }
        val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        if (sectionIsUnavailable(key)) return CollisionClass.EMPTY
        val block = blockInside(x, y, z, exact = false) ?: return CollisionClass.FULL
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
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val pos = mutable.set(x, y, z)
                    observer?.onRead(pos)
                    val block = blockAt(pos)
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

    /**
     * Whether [box] overlaps any block collision shape in the snapshot.
     *
     * The planner-side half of vanilla's sneak ledge clipping. Without it a worker
     * simulation could not model sneaking at all -- the probe it used needed a live entity,
     * so a planning thread silently answered "no ledge anywhere" and a tape that sneaked
     * near a drop would have walked off terrain the real client stops dead on.
     */
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
     * Vanilla picks the colliding block nearest the entity, breaking ties by
     * BlockPos order. Both halves matter: the nearest block is often *not* the
     * column under the entity's centre, and the tie-break is what makes the
     * choice deterministic when two blocks are equidistant.
     *
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

        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val pos = mutable.set(x, y, z)
                    observer?.onRead(pos)
                    val block = blockAt(pos)
                    if (block.collisionShape.isEmpty) continue

                    val collides = block.collisionShape
                        .offset(x.toDouble(), y.toDouble(), z.toDouble())
                        .boundingBoxes
                        .any { it.intersects(box) }
                    if (!collides) continue

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

    private fun blockAt(pos: BlockPos): SnapshotBlockPhysics {
        if (pos !in bounds) throw SimulationSnapshotOutOfBoundsException(pos.toImmutable())
        return blockInside(pos.x, pos.y, pos.z, exact = true) ?: run {
            val sectionX = pos.x shr 4
            val sectionY = pos.y shr 4
            val sectionZ = pos.z shr 4
            val key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
            if (sectionIsUnavailable(key)) {
                // Unknown terrain is a typed, recoverable condition: the rollout that
                // hit it is waiting on knowledge, not failing physics.
                onExactMiss?.invoke(key)
                throw SnapshotSectionUnavailableException(sectionX, sectionY, sectionZ)
            }
            throw IllegalStateException("Incomplete production snapshot at $pos")
        }
    }

    private fun blockInside(x: Int, y: Int, z: Int, exact: Boolean = false): SnapshotBlockPhysics? {
        val sectionX = x shr 4
        val sectionY = y shr 4
        val sectionZ = z shr 4
        val key = ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
        val installed = sections[key]
        val section = if (installed != null && (!exact || unavailableSectionKeys?.contains(key) != true)) {
            installed
        } else {
            missingSection?.invoke(sectionX, sectionY, sectionZ, exact)
        }
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

    /** Creates a worker-local dependency collector over this immutable snapshot. */
    fun trackingView(): TrackedSnapshotSimulationEnvironment = TrackedSnapshotSimulationEnvironment(this)

    private fun Box.intersectsUnitBlock(x: Int, y: Int, z: Int): Boolean =
        maxX > x && minX < x + 1.0 &&
            maxY > y && minY < y + 1.0 &&
            maxZ > z && minZ < z + 1.0

    companion object {
        /** Starts a resumable client-thread capture of every block in [bounds]. */
        internal fun beginCapture(
            world: World,
            player: ClientPlayerEntity,
            bounds: SimulationSnapshotBounds,
        ): SnapshotCaptureJob = SnapshotCaptureJob(world, player, bounds)

        /** Client-thread capture retained for fixtures and one-shot tooling. */
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

        /** Test/synthetic constructor; unspecified in-bounds cells are air. */
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

        /**
         * The coarse traits of a cell, read off its real collision shape.
         *
         * [CoarseVoxel.standingSurface] is the top of the shape *within the body's centred
         * column*, which is the same 0.6-wide column [CoarseVoxel.centerPassable] tests. A
         * body is 0.6 wide in a 1.0 cell, so what is under its middle is what holds it up:
         * a stairs block's raised half is, its tread is not the answer.
         *
         * Nothing standing proud of the cell counts. A fence post reaches 1.5, and a body
         * on top of one is standing in the cell above, not this one -- reporting a surface
         * here would put its feet half a block inside the floor.
         */
        internal fun coarseVoxelOf(shape: VoxelShape, bouncy: Boolean = false): CoarseVoxel {
            if (shape.isEmpty) return CoarseVoxel.AIR
            val underBody = VoxelShapes.combineAndSimplify(shape, CENTERED_SUPPORT_COLUMN, BooleanBiFunction.AND)
            val top = if (underBody.isEmpty) 0.0 else underBody.getMax(Direction.Axis.Y)
            // A shape that stands proud of its cell provides no surface *here*: the body it
            // holds up rests part way into the cell above, which is where that surface goes.
            val standsProud = top > 1.0 + COLLISION_EPSILON
            return CoarseVoxel(
                fullyPassable = false,
                centerPassable = !VoxelShapes.matchesAnywhere(shape, CENTERED_PLAYER_COLUMN, BooleanBiFunction.AND),
                standingSurface = top.takeIf { !standsProud && it > 0.0 }?.coerceAtMost(1.0),
                intrusionHeight = (top - 1.0).coerceAtLeast(0.0),
                bouncy = bouncy,
            )
        }

        internal fun BlockState.capturePhysics(
            world: World,
            pos: BlockPos,
            shapeContext: ShapeContext,
        ): SnapshotBlockPhysics {
            val shape = getCollisionShape(world, pos, shapeContext)
            val unsupported = unsupportedPhysics(this)
            val medium = mediumOf(this)
            val slime = isOf(Blocks.SLIME_BLOCK)
            val coarseVoxel = if (medium == Medium.CLIMBABLE) {
                // Known terrain a walking body cannot use, but the climb movement can.
                // Distinct from UNKNOWN, which is terrain nobody knows anything about.
                CoarseVoxel.of(Medium.CLIMBABLE)
            } else if (unsupported != null) {
                CoarseVoxel.UNKNOWN
            } else {
                coarseVoxelOf(shape, bouncy = slime)
            }
            return SnapshotBlockPhysics(
                collisionShape = shape,
                slipperiness = block.slipperiness.toDouble(),
                velocityMultiplier = block.velocityMultiplier.toDouble(),
                jumpVelocityMultiplier = block.jumpVelocityMultiplier.toDouble(),
                unsupportedPhysics = unsupported,
                coarseVoxel = coarseVoxel,
                fenceLike = isFenceLike(),
                bounceFactor = if (slime) 1.0 else 0.0,
                dampensSteppingSpeed = slime,
            )
        }

        /**
         * Which medium a block is, independent of whether anything can move through it.
         *
         * Separate from [unsupportedPhysics] on purpose: "what is this" and "can the
         * simulator handle it" used to be the same question, which is why every fluid,
         * ladder and cobweb collapsed into a single unknown that no movement could ever
         * claim. A medium named here is a medium a movement can be written for.
         */
        internal fun mediumOf(state: BlockState): Medium = when {
            !state.fluidState.isEmpty ->
                if (state.fluidState.isIn(FluidTags.LAVA)) Medium.LAVA else Medium.WATER
            state.isIn(BlockTags.CLIMBABLE) -> Medium.CLIMBABLE
            state.isOf(Blocks.COBWEB) -> Medium.COBWEB
            state.isOf(Blocks.POWDER_SNOW) -> Medium.POWDER_SNOW
            else -> Medium.SOLID
        }

        private fun unsupportedPhysics(state: BlockState): UnsupportedPhysics? = when {
            !state.fluidState.isEmpty -> UnsupportedPhysicsKind.FLUID
            state.isOf(Blocks.COBWEB) -> UnsupportedPhysicsKind.COBWEB
            state.isOf(Blocks.POWDER_SNOW) -> UnsupportedPhysicsKind.POWDER_SNOW
            state.isOf(Blocks.HONEY_BLOCK) -> UnsupportedPhysicsKind.HONEY_SIDE_EFFECTS
            else -> null
        }?.let { kind ->
            UnsupportedPhysics(
                kind = kind,
                blockId = Registries.BLOCK.getId(state.block).toString(),
            )
        }

        private const val COLLISION_EPSILON = 1.0E-7
        private val CENTERED_PLAYER_COLUMN = VoxelShapes.cuboid(
            0.2 + COLLISION_EPSILON,
            COLLISION_EPSILON,
            0.2 + COLLISION_EPSILON,
            0.8 - COLLISION_EPSILON,
            1.0 - COLLISION_EPSILON,
            0.8 - COLLISION_EPSILON,
        )

        /**
         * The same column, but reaching the cell's floor and ceiling exactly.
         *
         * [CENTERED_PLAYER_COLUMN] is inset on every axis so that a shape merely *touching*
         * the column does not read as blocking it, which is what the passability question
         * wants. The surface question is the opposite: it asks how high the shape reaches,
         * and an inset ceiling answers a full cube with 1.0 - 1e-7.
         *
         * That difference is not cosmetic. A surface a ten-millionth below the block top
         * puts the body's feet a ten-millionth *inside* the block it is standing on, and
         * the arc probe sweeps the body's box from there -- so it began every jump already
         * intersecting its own take-off block and refused the lot. Partial blocks were
         * unaffected, because their tops are nowhere near the inset ceiling, which is how
         * this showed up as "jumps work from a skull but not from stone".
         */
        private val CENTERED_SUPPORT_COLUMN = VoxelShapes.cuboid(
            0.2 + COLLISION_EPSILON,
            0.0,
            0.2 + COLLISION_EPSILON,
            0.8 - COLLISION_EPSILON,
            SUPPORT_COLUMN_CEILING,
            0.8 - COLLISION_EPSILON,
        )

        /**
         * How far above the cell the support probe looks, in blocks.
         *
         * Tall enough to see the whole of anything that stands proud of its own cell -- a
         * fence and a wall both reach 1.5. Clipping at the cell top instead made a fence
         * measure exactly 1.0 and read as a cell filled to the brim, so it claimed a surface
         * at its own top that no body can ever rest on.
         */
        const val SUPPORT_COLUMN_CEILING = 2.0
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

        /** Immutable copy suitable for publication after the rollout finishes. */
        fun dependencies(): Set<VoxelPos> = Collections.unmodifiableSet(HashSet(reads))

        /**
         * Returns and clears reads made since the previous call. Certification calls
         * this after every simulated tick, preserving the exact lifetime of replay
         * dependencies instead of treating already-walked terrain as forever live.
         */
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

        // Every physics read must come through here, or certification simulates a
        // different world than the search did. These two were missing, so the tracked
        // replay fell through to the interface defaults -- no bounce, no stepping drag --
        // and every tape whose search rollout touched slime diverged the moment it was
        // certified: the search's body reflected a landing the replay's body died on.
        override fun bounceFactor(pos: BlockPos): Double =
            snapshot.checkedBlockAt(pos, observer).bounceFactor

        override fun dampensSteppingSpeed(pos: BlockPos): Boolean =
            snapshot.checkedBlockAt(pos, observer).dampensSteppingSpeed
    }
}

/**
 * Client-thread producer for a sparse, immutable, worker-readable snapshot.
 *
 * A requested section is built privately and installed atomically. Consequently a
 * worker observes either no section (and waits) or one complete immutable version;
 * it can never observe a partially copied chunk section.
 */
