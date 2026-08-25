package com.lambda.pathing.coarse

import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.movement.MovementId
import com.lambda.pathing.movement.horizontalDistance
import com.lambda.pathing.world.VoxelPos
import net.minecraft.util.math.Vec3d
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

object CoarseMoveRates {
    const val SPRINT_BLOCKS_PER_TICK = 0.2806

    const val SPRINT_JUMP_BLOCKS_PER_TICK = 0.3640

    const val STEP_UP_BLOCKS_PER_TICK = 0.1000

    const val FREE_STEP_RISE = 0.6

    const val MIN_JUMP_DISTANCE = 2.0

    const val JUMP_AIR_TICKS = 12.0

    const val JUMP_RISE_TICKS = 2.0

    const val CLIMB_TICKS_PER_BLOCK = 1.0 / 0.117

    private const val MAX_FALL_TABLE_DEPTH = 12

    private val FALL_TICKS = DoubleArray(MAX_FALL_TABLE_DEPTH + 1).also { table ->
        var velocity = 0.0
        var fallen = 0.0
        var tick = 0
        var depth = 1
        while (depth <= MAX_FALL_TABLE_DEPTH) {
            tick++
            velocity = (velocity + 0.08) * 0.98
            fallen += velocity
            while (depth <= MAX_FALL_TABLE_DEPTH && fallen >= depth) {
                table[depth] = tick.toDouble()
                depth++
            }
        }
    }

    fun fallTicks(depth: Int): Double = FALL_TICKS[depth.coerceIn(1, MAX_FALL_TABLE_DEPTH)]
}

data class Stance(val x: Int, val y: Int, val z: Int) {
    fun offset(dx: Int, dy: Int, dz: Int) = Stance(x + dx, y + dy, z + dz)

    override fun toString() = "($x, $y, $z)"

    companion object {

        fun of(position: Vec3d, onGround: Boolean): Stance = Stance(
            floor(position.x).toInt(),
            if (onGround) floor(position.y - SURFACE_EPSILON).toInt() + 1
            else floor(position.y + SURFACE_EPSILON).toInt(),
            floor(position.z).toInt(),
        )

        private const val SURFACE_EPSILON = 1e-6
    }
}

@JvmInline
value class MotionTemplateId(val value: Int)

data class CoarseEdgeId(val template: MotionTemplateId, val from: Stance)

class LazyReadSet(supplier: () -> Set<VoxelPos>) : AbstractSet<VoxelPos>() {
    private val backing: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION, supplier)

    override val size: Int get() = backing.size

    override fun iterator(): Iterator<VoxelPos> = backing.iterator()

    override fun contains(element: VoxelPos): Boolean = element in backing
}

data class CoarseEdge(
    val id: CoarseEdgeId,
    val from: Stance,
    val to: Stance,
    val movement: MovementId,
    val lowerBoundTicks: Double,
    val readSet: Set<VoxelPos>,

    val launch: LaunchSolution? = null,

    val bounce: BounceSolution? = null,
)

class CoarseMoveCosts(
    val cardinalWalk: Double,
    val diagonalWalk: Double,
    val stepUp: Double,
    val walkOff: (depth: Int) -> Double,

    val jumpCandidate: (distance: Double, verticalOffset: Int) -> Double,
    val drop: (span: Int, depth: Int) -> Double = { _, depth -> walkOff(depth) },
    val climb: (blocks: Int) -> Double = { blocks -> blocks * CoarseMoveRates.CLIMB_TICKS_PER_BLOCK },

    val bounce: (span: Int, drop: Int, rise: Int) -> Double = { _, drop, rise ->
        CoarseMoveRates.fallTicks(drop) + CoarseMoveRates.fallTicks(drop + rise) + 2.0
    },
) {
    init {
        validate("cardinalWalk", cardinalWalk)
        validate("diagonalWalk", diagonalWalk)
        validate("stepUp", stepUp)
    }

    fun jumpCandidateCost(distance: Double, verticalOffset: Int): Double {
        require(distance >= CoarseMoveRates.MIN_JUMP_DISTANCE) {
            "a jump candidate must reach past the adjacent stance: $distance"
        }
        return jumpCandidate(distance, verticalOffset).also {
            validate("jumpCandidate($distance, $verticalOffset)", it)
        }
    }

    fun walkOffCost(depth: Int): Double {
        require(depth > 0) { "walk-off depth must be positive" }
        return walkOff(depth).also { validate("walkOff($depth)", it) }
    }

    fun bounceCost(span: Int, drop: Int, rise: Int): Double {
        require(drop > 0) { "a bounce must fall onto something: $drop" }
        return bounce(span, drop, rise).also { validate("bounce($span, $drop, $rise)", it) }
    }

    fun climbCost(blocks: Int): Double {
        require(blocks > 0) { "a climb must cover at least one block" }
        return climb(blocks).also { validate("climb($blocks)", it) }
    }

    fun dropCost(span: Int, depth: Int): Double {
        require(span >= 1) { "a drop must cross at least the adjacent stance" }
        require(depth > 0) { "drop depth must be positive" }
        return drop(span, depth).also { validate("drop($span, $depth)", it) }
    }

    private fun validate(name: String, value: Double) {
        require(value.isFinite() && value > 0.0) { "$name must be finite and positive: $value" }
    }

    companion object {

        private const val UNREACHABLE_BALLISTIC_TICKS = 1000.0

        private const val DROP_SETTLE_TICKS = 2.0

        fun measured(
            transitionOverheadTicks: Double = 0.0,
            profile: BallisticProfile = BallisticProfile.VANILLA,
        ): CoarseMoveCosts {
            require(transitionOverheadTicks >= 0.0 && transitionOverheadTicks.isFinite()) {
                "transitionOverheadTicks must be non-negative and finite: $transitionOverheadTicks"
            }
            val walk = 1.0 / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK
            val step = 1.0 / CoarseMoveRates.STEP_UP_BLOCKS_PER_TICK

            fun ballistic(jumping: Boolean, rise: Int): Double? = LaunchMode.entries
                .filter { it.jumps == jumping && it.supports(rise.toDouble()) }
                .mapNotNull { mode -> profile.fly(mode, profile.cruiseSpeed(mode.sprint), rise.toDouble())?.airTicks }
                .minOrNull()?.let { (it + 1).toDouble() }

            fun jump(horizontalDistance: Double, verticalOffset: Int): Double =
                max(
                    ballistic(jumping = true, rise = verticalOffset) ?: UNREACHABLE_BALLISTIC_TICKS,
                    horizontalDistance / CoarseMoveRates.SPRINT_JUMP_BLOCKS_PER_TICK,
                )

            return CoarseMoveCosts(
                cardinalWalk = walk + transitionOverheadTicks,
                diagonalWalk = sqrt(2.0) * walk + transitionOverheadTicks,
                stepUp = step + transitionOverheadTicks,
                walkOff = { depth -> max(walk, CoarseMoveRates.fallTicks(depth)) + 1.0 + transitionOverheadTicks },
                jumpCandidate = { distance, vo -> jump(distance, vo) + transitionOverheadTicks },
                drop = { span, depth ->

                    max(
                        ballistic(jumping = false, rise = -depth) ?: UNREACHABLE_BALLISTIC_TICKS,
                        span / CoarseMoveRates.SPRINT_BLOCKS_PER_TICK,
                    ) + DROP_SETTLE_TICKS + transitionOverheadTicks
                },
            )
        }
    }
}

data class SimpleMoveOptions(
    val allowDiagonal: Boolean = true,
    val allowStepUp: Boolean = true,
    val maxWalkOffDepth: Int = 3,

    val maxDropSpan: Int = 2,

    val allowClimbing: Boolean = false,
    val allowJumpCandidates: Boolean = true,
    val maxJumpSpan: Int = 4,
    val maxJumpDrop: Int = 1,

    val allowOffAxisJumps: Boolean = true,

    val allowSlimeBounces: Boolean = false,
    val maxBounceDrop: Int = 8,
) {
    init {
        require(maxWalkOffDepth >= 0) { "maxWalkOffDepth must be non-negative" }
        require(maxDropSpan >= 1) { "maxDropSpan must reach at least the adjacent stance" }
        require(maxJumpSpan >= 2) { "maxJumpSpan must reach past the adjacent stance" }
        require(maxBounceDrop >= 3) { "a bounce needs a fall deep enough to rebound from" }
        require(maxJumpDrop >= 0) { "maxJumpDrop must be non-negative" }
    }
}
