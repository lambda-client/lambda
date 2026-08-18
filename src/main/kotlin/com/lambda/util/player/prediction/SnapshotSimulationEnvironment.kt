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
import com.lambda.pathing.world.VoxelPos
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
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.World
import java.util.Collections

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

private fun interface SnapshotReadObserver {
    fun onRead(pos: BlockPos)
}

/**
 * Bounded immutable environment for worker-thread rollouts.
 *
 * Collision shapes and block constants are resolved on the client thread at
 * capture time. Rollouts only read this immutable map and immutable voxel
 * shapes; they never touch chunks, the world, or the live player. Reads beyond
 * [bounds] fail closed instead of treating uncaptured terrain as air.
 */
class SnapshotSimulationEnvironment private constructor(
    val bounds: SimulationSnapshotBounds,
    val capturedWorldTime: Long,
    blocks: Map<Long, SnapshotBlockPhysics>,
    private val defaultBlock: SnapshotBlockPhysics?,
) : SimulationEnvironment, CoarseVoxelView {
    /** Captured cells, packed by [BlockPos.asLong]. Debug/serialisation only. */
    fun snapshotBlocks(): Map<Long, SnapshotBlockPhysics> = blocks

    private val blocks = Collections.unmodifiableMap(HashMap(blocks))

    override val simulableStanceY: IntRange = bounds.simulableStanceY

    override fun slipperiness(pos: BlockPos): Double = checkedBlockAt(pos, null).slipperiness

    override fun velocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos, null).velocityMultiplier

    override fun jumpVelocityMultiplier(pos: BlockPos): Double = checkedBlockAt(pos, null).jumpVelocityMultiplier

    /** Planner reads fail closed at snapshot bounds and unsupported physics. */
    override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel {
        if (x !in bounds.minX..bounds.maxX || y !in bounds.minY..bounds.maxY || z !in bounds.minZ..bounds.maxZ) {
            return CoarseVoxel.UNKNOWN
        }
        val block = blocks[BlockPos.asLong(x, y, z)] ?: defaultBlock ?: return CoarseVoxel.UNKNOWN
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
        val block = blocks[BlockPos.asLong(x, y, z)] ?: defaultBlock ?: return VoxelShapes.fullCube()
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
                        (distance == bestDistance && (best == null || best!! < candidate))
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
        return blocks[BlockPos.asLong(pos.x, pos.y, pos.z)]
            ?: defaultBlock
            ?: throw IllegalStateException("Incomplete production snapshot at $pos")
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
        /** Client-thread capture of every block in [bounds]. */
        fun capture(
            world: World,
            player: ClientPlayerEntity,
            bounds: SimulationSnapshotBounds,
        ): SnapshotSimulationEnvironment {
            check(MinecraftClient.getInstance().isOnThread) {
                "Simulation snapshots must be captured on the client thread"
            }
            val blocks = HashMap<Long, SnapshotBlockPhysics>()
            val mutable = BlockPos.Mutable()
            val shapeContext = ShapeContext.of(player)
            for (y in bounds.minY..bounds.maxY) {
                for (z in bounds.minZ..bounds.maxZ) {
                    for (x in bounds.minX..bounds.maxX) {
                        val pos = mutable.set(x, y, z)
                        val state = world.getBlockState(pos)
                        blocks[BlockPos.asLong(x, y, z)] = state.capturePhysics(world, pos, shapeContext)
                    }
                }
            }
            return SnapshotSimulationEnvironment(
                bounds = bounds,
                capturedWorldTime = world.time,
                blocks = blocks,
                defaultBlock = null,
            )
        }

        /** Test/synthetic constructor; unspecified in-bounds cells are air. */
        fun synthetic(
            bounds: SimulationSnapshotBounds,
            blocks: Map<BlockPos, SnapshotBlockPhysics>,
        ): SnapshotSimulationEnvironment = SnapshotSimulationEnvironment(
            bounds = bounds,
            capturedWorldTime = 0L,
            blocks = blocks.mapKeys { (pos, _) -> BlockPos.asLong(pos.x, pos.y, pos.z) },
            defaultBlock = SnapshotBlockPhysics.AIR,
        )

        private fun BlockState.capturePhysics(
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
        private val observer = SnapshotReadObserver { pos -> reads += VoxelPos(pos.x, pos.y, pos.z) }

        /** Immutable copy suitable for publication after the rollout finishes. */
        fun dependencies(): Set<VoxelPos> = Collections.unmodifiableSet(HashSet(reads))

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
