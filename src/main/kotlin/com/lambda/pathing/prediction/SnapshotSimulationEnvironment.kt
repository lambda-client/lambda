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

package com.lambda.pathing.prediction

import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.Medium
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.prediction.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.snapshot.SnapshotCaptureJob
import com.lambda.pathing.prediction.snapshot.SnapshotCaptureResult
import com.lambda.pathing.prediction.snapshot.SnapshotStorageStats
import com.lambda.util.BlockUtils.isFenceLike
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
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

class SnapshotSimulationEnvironment internal constructor(
    val bounds: SimulationSnapshotBounds,
    sections: Map<Long, ImmutableSnapshotSection>,
    private val defaultBlock: SnapshotBlockPhysics?,
    shareSections: Boolean = false,
    private val missingSection: ((Int, Int, Int, Boolean) -> ImmutableSnapshotSection)? = null,
    private val sparseSectionCoordinates: Map<Long, Triple<Int, Int, Int>>? = null,
    private val unavailableSectionKeys: Set<Long>? = null,
    private val sectionUnavailable: ((Long) -> Boolean)? = null,
    private val onExactMiss: ((Long) -> Unit)? = null,
) : SimulationEnvironment, CoarseVoxelView {
    private fun interface SnapshotReadObserver {
        fun onRead(pos: BlockPos)
    }

    private val sections: Map<Long, ImmutableSnapshotSection> = if (shareSections) sections else
        Long2ObjectOpenHashMap<ImmutableSnapshotSection>().apply { putAll(sections) }

    internal fun storageStats() = SnapshotStorageStats(
        sections = sections.size,
        paletteEntries = sections.values.sumOf(ImmutableSnapshotSection::paletteSize),
        indexBytes = sections.values.sumOf(ImmutableSnapshotSection::indexStorageBytes),
    )

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

    override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel {
        if (x !in bounds.minX..bounds.maxX || y !in bounds.minY..bounds.maxY || z !in bounds.minZ..bounds.maxZ) {
            return CoarseVoxel.UNKNOWN
        }
        val block = blockInside(x, y, z, exact = false) ?: return CoarseVoxel.UNKNOWN
        return if (block.unsupportedPhysics == null) block.coarseVoxel else CoarseVoxel.HAZARD
    }

    override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape {
        if (x !in bounds.minX..bounds.maxX || y !in bounds.minY..bounds.maxY || z !in bounds.minZ..bounds.maxZ) {
            return VoxelShapes.fullCube()
        }
        val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        if (sectionIsUnavailable(key)) return VoxelShapes.empty()
        val block = blockInside(x, y, z, exact = false) ?: return VoxelShapes.fullCube()
        return if (block.unsupportedPhysics == null) block.collisionShape else VoxelShapes.fullCube()
    }

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

    fun trackingView(): TrackedSnapshotSimulationEnvironment = TrackedSnapshotSimulationEnvironment(this)

    private fun Box.intersectsUnitBlock(x: Int, y: Int, z: Int): Boolean =
        maxX > x && minX < x + 1.0 &&
            maxY > y && minY < y + 1.0 &&
            maxZ > z && minZ < z + 1.0

    companion object {
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

        internal fun coarseVoxelOf(shape: VoxelShape, bouncy: Boolean = false): CoarseVoxel {
            if (shape.isEmpty) return CoarseVoxel.AIR
            val underBody = VoxelShapes.combineAndSimplify(shape, CENTERED_SUPPORT_COLUMN, BooleanBiFunction.AND)
            val top = if (underBody.isEmpty) 0.0 else underBody.getMax(Direction.Axis.Y)
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

        private val CENTERED_SUPPORT_COLUMN = VoxelShapes.cuboid(
            0.2 + COLLISION_EPSILON,
            0.0,
            0.2 + COLLISION_EPSILON,
            0.8 - COLLISION_EPSILON,
            SUPPORT_COLUMN_CEILING,
            0.8 - COLLISION_EPSILON,
        )

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