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
import com.lambda.pathing.world.PathingChunk
import com.lambda.pathing.world.VoxelPos
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
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
) {
    companion object {
        const val DEFAULT_SLIPPERINESS = 0.6

        val AIR = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.AIR)
        val FULL_CUBE = SnapshotBlockPhysics(VoxelShapes.fullCube(), coarseVoxel = CoarseVoxel.FULL_BLOCK)
        /** Exact simulation never consumes this placeholder. */
        val UNAVAILABLE = SnapshotBlockPhysics(VoxelShapes.fullCube(), coarseVoxel = CoarseVoxel.UNKNOWN)
    }
}

enum class UnsupportedPhysicsKind {
    FLUID,
    CLIMBABLE,
    COBWEB,
    POWDER_SNOW,
    SLIME_BOUNCE,
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
        unavailableSectionKeys?.contains(ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)) == true

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
        if (unavailableSectionKeys?.contains(key) == true) return VoxelShapes.empty()
        val block = blockInside(x, y, z, exact = false) ?: return VoxelShapes.fullCube()
        return if (block.unsupportedPhysics == null) block.collisionShape else VoxelShapes.fullCube()
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
        return blockInside(pos.x, pos.y, pos.z, exact = true)
            ?: throw IllegalStateException("Incomplete production snapshot at $pos")
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

        /**
         * Starts an initially-empty snapshot whose immutable sections are requested by
         * actual coarse/trajectory reads. The worker waits without touching Minecraft;
         * [DemandDrivenSnapshotCapture.advance] performs every world read on the client
         * thread under the normal per-tick budget.
         */
        internal fun beginDemandDrivenCapture(
            world: World,
            player: ClientPlayerEntity,
            bounds: SimulationSnapshotBounds,
            cancelled: () -> Boolean,
            replaying: () -> Boolean = { false },
        ): DemandDrivenSnapshotCapture =
            DemandDrivenSnapshotCapture(world, player, bounds, cancelled, replaying)

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

        internal fun BlockState.capturePhysics(
            world: World,
            pos: BlockPos,
            shapeContext: ShapeContext,
        ): SnapshotBlockPhysics {
            val shape = getCollisionShape(world, pos, shapeContext)
            val unsupported = unsupportedPhysics(this)
            val coarseVoxel = if (unsupported != null) {
                CoarseVoxel.UNKNOWN
            } else {
                CoarseVoxel(
                    fullyPassable = shape.isEmpty,
                    centerPassable = shape.isEmpty ||
                        !VoxelShapes.matchesAnywhere(shape, CENTERED_PLAYER_COLUMN, BooleanBiFunction.AND),
                    standableFullTop = isSideSolidFullSquare(world, pos, Direction.UP),
                    intrudesAbove = !shape.isEmpty && shape.getMax(Direction.Axis.Y) > 1.0 + COLLISION_EPSILON,
                )
            }
            return SnapshotBlockPhysics(
                collisionShape = shape,
                slipperiness = block.slipperiness.toDouble(),
                velocityMultiplier = block.velocityMultiplier.toDouble(),
                jumpVelocityMultiplier = block.jumpVelocityMultiplier.toDouble(),
                unsupportedPhysics = unsupported,
                coarseVoxel = coarseVoxel,
                fenceLike = isFenceLike(),
            )
        }

        private fun unsupportedPhysics(state: BlockState): UnsupportedPhysics? = when {
            !state.fluidState.isEmpty -> UnsupportedPhysicsKind.FLUID
            state.isIn(BlockTags.CLIMBABLE) -> UnsupportedPhysicsKind.CLIMBABLE
            state.isOf(Blocks.COBWEB) -> UnsupportedPhysicsKind.COBWEB
            state.isOf(Blocks.POWDER_SNOW) -> UnsupportedPhysicsKind.POWDER_SNOW
            state.isOf(Blocks.SLIME_BLOCK) -> UnsupportedPhysicsKind.SLIME_BOUNCE
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
    }
}

/**
 * Client-thread producer for a sparse, immutable, worker-readable snapshot.
 *
 * A requested section is built privately and installed atomically. Consequently a
 * worker observes either no section (and waits) or one complete immutable version;
 * it can never observe a partially copied chunk section.
 */
internal class DemandDrivenSnapshotCapture(
    private val world: World,
    private val player: ClientPlayerEntity,
    val bounds: SimulationSnapshotBounds,
    private val cancelled: () -> Boolean,
    /** True while a certified tape is replaying, i.e. while the frontier still moves. */
    private val replaying: () -> Boolean = { false },
) {
    private data class Section(val x: Int, val y: Int, val z: Int) {
        val key: Long get() = ChunkSectionPos.asLong(x, y, z)
    }

    private val sections = ConcurrentHashMap<Long, ImmutableSnapshotSection>()
    private val sectionCoordinates = ConcurrentHashMap<Long, Triple<Int, Int, Int>>()
    private val requests = ConcurrentHashMap<Long, CompletableFuture<ImmutableSnapshotSection>>()
    private val exactRequired = ConcurrentHashMap.newKeySet<Long>()
    private val unavailableSections = ConcurrentHashMap.newKeySet<Long>()
    /** Placeholder sections whose chunk is outside the trusted loaded ring right now. */
    private val frontierSections = ConcurrentHashMap.newKeySet<Long>()
    /** Client-thread view of [replaying], readable by the parked planner thread. */
    private val replayHold = AtomicBoolean()
    /** Placeholder sections whose chunk arrived, awaiting planner-thread retirement. */
    private val retirableSections = ConcurrentHashMap.newKeySet<Long>()
    /** Placeholder sections currently being replaced by authoritative terrain. */
    private val optimisticReplacements = ConcurrentHashMap.newKeySet<Long>()
    /** Chunks whose coarse graph edges may still describe a removed placeholder. */
    private val authoritativeTransitions = ConcurrentHashMap.newKeySet<PathingChunk>()
    private val authoritativeChunks = ConcurrentHashMap.newKeySet<PathingChunk>()
    private val queued = ConcurrentHashMap.newKeySet<Long>()
    private val queue = ConcurrentLinkedQueue<Section>()
    private val shapeContext = ShapeContext.of(player)
    private val mutable = BlockPos.Mutable()

    private var active: Section? = null
    private var builder = ImmutableSnapshotSection.Builder()
    private var x = 0
    private var y = 0
    private var z = 0

    val snapshot = SnapshotSimulationEnvironment(
        bounds = bounds,
        sections = sections,
        defaultBlock = null,
        shareSections = true,
        missingSection = ::awaitSection,
        sparseSectionCoordinates = sectionCoordinates,
        unavailableSectionKeys = unavailableSections,
    )

    val hasPendingDemand: Boolean
        get() = active != null || queue.isNotEmpty()

    fun hasAuthoritativeChunk(chunkX: Int, chunkZ: Int): Boolean =
        PathingChunk(chunkX, chunkZ) in authoritativeChunks

    /**
     * Returns optimistic-to-exact knowledge transitions since the previous drain.
     *
     * Coarse D* may have cached edges derived from an unavailable placeholder. Exact
     * rollout later replaces that placeholder as the player approaches it, which is a
     * graph-cost change even though the live world itself did not mutate. The retained
     * journey consumes these chunks before its next repair.
     */
    fun drainAuthoritativeTransitions(): Set<PathingChunk> = buildSet {
        for (chunk in authoritativeTransitions) {
            if (authoritativeTransitions.remove(chunk)) add(chunk)
        }
    }

    /**
     * Notes optimistic placeholders whose chunk has entered the trusted loaded ring.
     *
     * A placeholder is knowledge, not terrain: once the real chunk is streamed the
     * coarse graph must stop routing over the assumed surface. The placeholder itself
     * is not dropped here -- a section that changes identity underneath a running
     * search would make its own cached edges unreproducible, which reads as a
     * converged search with no extractable route. The drop happens on the planner
     * thread in [retireOptimisticSections], between searches.
     */
    fun refreshFrontier() {
        check(MinecraftClient.getInstance().isOnThread) {
            "Snapshot frontier state may only be refreshed on the client thread"
        }
        replayHold.set(replaying())
        if (frontierSections.isEmpty()) return
        val iterator = frontierSections.iterator()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val sectionX = ChunkSectionPos.unpackX(key)
            val sectionZ = ChunkSectionPos.unpackZ(key)
            if (!isTrustedLoadedChunk(sectionX, sectionZ)) continue
            iterator.remove()
            if (key in unavailableSections) {
                retirableSections += key
                authoritativeTransitions += PathingChunk(sectionX, sectionZ)
            }
        }
    }

    /**
     * Drops the placeholders [refreshFrontier] marked, so the next read of those
     * sections captures the terrain the client now has. Runs on the planner thread
     * before a repair, never while a search is reading the graph it built, and only
     * for the chunks that same repair is about to resynchronize -- a section that
     * changed identity without its cached edges being regenerated leaves a route D*
     * still believes in but nothing can rebuild.
     */
    fun retireOptimisticSections(chunks: Set<PathingChunk>) {
        if (retirableSections.isEmpty() || chunks.isEmpty()) return
        for (key in retirableSections) {
            val chunk = PathingChunk(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key))
            if (chunk !in chunks) continue
            if (!retirableSections.remove(key)) continue
            if (!unavailableSections.remove(key)) continue
            sections.remove(key)
            sectionCoordinates.remove(key)
        }
    }

    /**
     * Drops the immutable version containing [pos]. A later planner read requests a
     * fresh atomic copy; an in-flight copy is restarted from cell zero.
     */
    fun invalidate(pos: BlockPos) {
        check(MinecraftClient.getInstance().isOnThread) {
            "Snapshot sections may only be invalidated on the client thread"
        }
        val section = Section(pos.x shr 4, pos.y shr 4, pos.z shr 4)
        sections.remove(section.key)
        sectionCoordinates.remove(section.key)
        unavailableSections.remove(section.key)
        frontierSections.remove(section.key)
        retirableSections.remove(section.key)
        optimisticReplacements.remove(section.key)
        if (active?.key == section.key) {
            active = null
            builder = ImmutableSnapshotSection.Builder()
        }
        requests[section.key]?.takeUnless { it.isDone }?.let {
            if (queued.add(section.key)) queue += section
        }
    }

    /** Drops every installed exact section in a refreshed chunk. */
    fun invalidateChunk(chunkX: Int, chunkZ: Int) {
        check(MinecraftClient.getInstance().isOnThread) {
            "Snapshot sections may only be invalidated on the client thread"
        }
        val installed = sectionCoordinates.entries
            .filter { (_, coordinate) -> coordinate.first == chunkX && coordinate.third == chunkZ }
            .map { (key, coordinate) -> key to Section(coordinate.first, coordinate.second, coordinate.third) }
        installed.forEach { (key, section) ->
            sections.remove(key)
            sectionCoordinates.remove(key)
            unavailableSections.remove(key)
            frontierSections.remove(key)
            retirableSections.remove(key)
            optimisticReplacements.remove(key)
            requests[key]?.takeUnless { it.isDone }?.let {
                if (queued.add(key)) queue += section
            }
        }
        active?.takeIf { it.x == chunkX && it.z == chunkZ }?.let { section ->
            active = null
            builder = ImmutableSnapshotSection.Builder()
            if (requests[section.key]?.isDone == false && queued.add(section.key)) queue += section
        }
    }

    fun advance(maxCells: Int, deadlineNanos: Long) {
        check(MinecraftClient.getInstance().isOnThread) {
            "Simulation snapshots must be captured on the client thread"
        }
        require(maxCells > 0) { "Snapshot capture cell quota must be positive" }

        replayHold.set(replaying())
        var written = 0
        while (written < maxCells && (written == 0 || System.nanoTime() < deadlineNanos)) {
            val section = active ?: nextSection() ?: break
            if (!isTrustedLoadedChunk(section.x, section.z)) {
                if (section.key in exactRequired && replaying()) {
                    // Exact rollout has reached the currently loaded frontier while a
                    // certified tape is still carrying the body forward. Hold its
                    // request: the walk itself is what brings this chunk into view, and
                    // the wait ends the moment replay does.
                    active = null
                    if (queued.add(section.key)) queue += section
                    return
                }
                // Install a marker rather than failing the whole coarse search. The
                // coarse-only wrapper turns it into optimistic guidance; an exact reader
                // is rejected below instead of certifying against the placeholder, which
                // is what keeps a standing player from parking the planner on a chunk
                // only its own motion could load.
                builder.fill(SnapshotBlockPhysics.UNAVAILABLE)
                unavailableSections += section.key
                frontierSections += section.key
                completeSection(section)
                continue
            }
            frontierSections.remove(section.key)

            val pos = mutable.set(x, y, z)
            val physics = with(SnapshotSimulationEnvironment) {
                world.getBlockState(pos).capturePhysics(world, pos, shapeContext)
            }
            builder.set(x, y, z, physics)
            written++

            if (advanceCell(section)) completeSection(section)
        }
    }

    fun cancel() {
        val failure = CancellationException("snapshot capture was cancelled")
        requests.values.forEach { it.completeExceptionally(failure) }
        requests.clear()
        exactRequired.clear()
        frontierSections.clear()
        retirableSections.clear()
        replayHold.set(false)
        optimisticReplacements.clear()
        authoritativeTransitions.clear()
        queue.clear()
        active = null
    }

    private fun awaitSection(
        sectionX: Int,
        sectionY: Int,
        sectionZ: Int,
        exact: Boolean,
    ): ImmutableSnapshotSection {
        val section = Section(sectionX, sectionY, sectionZ)
        sections[section.key]?.takeIf { !exact || section.key !in unavailableSections }?.let { return it }
        if (exact) {
            // The client tick clears this the moment the chunk enters the trusted ring;
            // until then a request would only be refused again a tick later. While a
            // tape is replaying the request is made anyway, so that the capture can
            // hold it for the frontier the walk is still moving.
            if (section.key in frontierSections && !replayHold.get()) {
                throw SnapshotSectionUnavailableException(sectionX, sectionY, sectionZ)
            }
            exactRequired += section.key
            if (unavailableSections.remove(section.key)) {
                optimisticReplacements += section.key
                sections.remove(section.key)
                sectionCoordinates.remove(section.key)
            }
        }
        val created = CompletableFuture<ImmutableSnapshotSection>()
        val existing = requests.putIfAbsent(section.key, created)
        val future = existing ?: created.also {
            queued += section.key
            queue += section
        }
        while (true) {
            if (cancelled()) throw CancellationException("snapshot capture was cancelled")
            try {
                return future.get(WAIT_POLL_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                // Poll cancellation; the client thread owns all actual world reads.
            } catch (failure: ExecutionException) {
                val cause = failure.cause ?: failure
                if (cause is RuntimeException) throw cause
                throw IllegalStateException(cause.message, cause)
            } catch (failure: InterruptedException) {
                Thread.currentThread().interrupt()
                throw CancellationException("snapshot wait was interrupted")
            }
        }
    }

    private fun nextSection(): Section? {
        while (true) {
            val candidate = queue.poll() ?: return null
            queued.remove(candidate.key)
            if (sections.containsKey(candidate.key) || requests[candidate.key]?.isDone != false) continue
            active = candidate
            builder = ImmutableSnapshotSection.Builder()
            x = candidate.x shl 4
            y = candidate.y shl 4
            z = candidate.z shl 4
            return candidate
        }
    }

    /**
     * [World.isChunkLoaded] is not enough on the client. Vanilla deliberately keeps
     * a three-chunk cache margin around the server's watched area, and those cache
     * entries can be newly allocated chunks which still contain only void air. Treat
     * only chunks inside the server watch filter as authoritative snapshot input.
     *
     * This mirrors `ChunkFilter.isWithinDistance(..., includeEdge = true)`. Using the
     * current player chunk as the center is conservative around a center-update race:
     * the final chunk-manager check still requires a packet-created chunk, and an exact
     * request is retried on following client ticks if it is not trusted yet.
     */
    private fun isTrustedLoadedChunk(chunkX: Int, chunkZ: Int): Boolean {
        if (!world.chunkManager.isChunkLoaded(chunkX, chunkZ)) return false
        val center = player.chunkPos
        val viewDistance = MinecraftClient.getInstance().options.clampedViewDistance
        val dx = max(0, abs(chunkX - center.x) - CHUNK_FILTER_EDGE_MARGIN).toLong()
        val dz = max(0, abs(chunkZ - center.z) - CHUNK_FILTER_EDGE_MARGIN).toLong()
        return dx * dx + dz * dz < viewDistance.toLong() * viewDistance
    }

    /** True after the section's last cell. */
    private fun advanceCell(section: Section): Boolean {
        val maxX = (section.x shl 4) + 15
        val maxY = (section.y shl 4) + 15
        val maxZ = (section.z shl 4) + 15
        if (x < maxX) { x++; return false }
        x = section.x shl 4
        if (z < maxZ) { z++; return false }
        z = section.z shl 4
        if (y < maxY) { y++; return false }
        return true
    }

    private fun completeSection(section: Section) {
        val frozen = builder.build(expectedWrites = SECTION_CELLS)
        val placeholder = section.key in unavailableSections
        if (!placeholder) {
            exactRequired.remove(section.key)
            val chunk = PathingChunk(section.x, section.z)
            authoritativeChunks += chunk
            if (optimisticReplacements.remove(section.key)) authoritativeTransitions += chunk
        }
        sectionCoordinates[section.key] = Triple(section.x, section.y, section.z)
        sections[section.key] = frozen
        val request = requests.remove(section.key)
        if (placeholder && exactRequired.remove(section.key)) {
            request?.completeExceptionally(
                SnapshotSectionUnavailableException(section.x, section.y, section.z)
            )
        } else {
            request?.complete(frozen)
        }
        active = null
    }

    private companion object {
        const val SECTION_CELLS = 16 * 16 * 16
        const val WAIT_POLL_MILLIS = 50L
        const val CHUNK_FILTER_EDGE_MARGIN = 2
    }
}
