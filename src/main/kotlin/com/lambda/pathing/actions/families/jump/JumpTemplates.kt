package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import kotlin.math.abs

/** The jump template fan: which stance deltas are offered and the specs describing them. */
internal object JumpTemplates {

    /** Launch modes a jump may use; shared by the fan, the solver calls and the proposers. */
    val MODES = LaunchMode.entries.filter { it.jumps }

    fun templates(context: MovementContext, movement: MovementId): List<TemplateSpec> = buildList {
        val options = context.options
        if (!options.allowJumpCandidates) return@buildList

        val reach = options.maxJumpSpan
        for (rise in -options.maxJumpDrop..LaunchMode.MAX_JUMP_RISE) {
            for (dx in -reach..reach) {
                for (dz in -reach..reach) {
                    if (!offered(dx, dz, rise, options)) continue
                    add(spec(dx, dz, rise, context.costs.jumpCandidateCost(hypot(dx, dz), rise), movement))
                }
            }
        }
    }

    /**
     * A jump template must be reachable from a standing start on its own block (the
     * coarse graph cannot promise a run-up). The gate is the corner-to-corner AIR GAP,
     * not cell-centre distance, against rollout-measured ceilings; [SimpleMoveOptions.maxJumpSpan]
     * is a per-axis user cap on top. See docs/decisions/movement-tuning.md (standing reach).
     */
    private fun offered(dx: Int, dz: Int, rise: Int, options: SimpleMoveOptions): Boolean {
        val distance = hypot(dx, dz)
        if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE) return false
        if (dx != 0 && dz != 0 && !options.allowDiagonal) return false
        if (abs(dx) != abs(dz) && dx != 0 && dz != 0 && !options.allowOffAxisJumps) return false
        if (maxOf(abs(dx), abs(dz)) > options.maxJumpSpan) return false

        // Statically the WIDEST ceiling this stance delta could reach; the real
        // (surface-corrected) rise is judged in [spec]'s riseAdmission. The deep-drop
        // ceiling is opt-in and never offered for LEVEL deltas.
        // See docs/decisions/movement-tuning.md (deep-drop jumps).
        val ceiling = if (rise < 0 && options.allowDeepDropJumps) FULL_DROP_AIR_GAP_BLOCKS
            else STANDING_AIR_GAP_BLOCKS
        return airGap(dx, dz) <= ceiling + REACH_EPSILON
    }

    private fun airGap(dx: Int, dz: Int): Double = kotlin.math.hypot(
        (abs(dx) - 1).coerceAtLeast(0).toDouble(),
        (abs(dz) - 1).coerceAtLeast(0).toDouble(),
    )

    private fun hypot(dx: Int, dz: Int): Double = kotlin.math.hypot(dx.toDouble(), dz.toDouble())

    private fun spec(dx: Int, dz: Int, rise: Int, cost: Double, movement: MovementId): TemplateSpec {
        val gap = airGap(dx, dz)
        return TemplateSpec(
            dx = dx, dy = rise, dz = dz,
            movement = movement,
            cost = cost,
            conditions = StanceRules.stanceConditions(dx, rise, dz),
            arc = MotionTemplate.ArcSpec(
                dx, dz, rise, MODES,
                // Reach ladder judged against the surface-corrected rise: a real ascent
                // keeps the rising reach, a near-flat one gets the flat ceiling, longer
                // gaps must be bought with descent. See docs/decisions/movement-tuning.md.
                riseAdmission = { realRise ->
                    when {
                        gap <= RISING_STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
                        realRise > NEAR_FLAT_RISE -> false
                        gap <= STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
                        realRise > HALF_DROP_RISE -> false
                        gap <= HALF_DROP_AIR_GAP_BLOCKS + REACH_EPSILON -> true
                        else -> realRise <= FULL_DROP_RISE
                    }
                },
            ),
        )
    }

    private const val REACH_EPSILON = 1e-9

    /** Air gap a standing start clears on flat and near-flat jumps: hypot(3, 1). */
    private val STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 1.0)

    /** Rising jumps trade reach for the block of height: hypot(2, 2). */
    private val RISING_STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(2.0, 2.0)

    /**
     * Drop-extended reaches: half a block of real descent certifies a 4.0 air gap, a
     * full block the 4.12-4.24 diagonals; 5.0 never certifies.
     * See docs/decisions/movement-tuning.md (drop-extended reaches).
     */
    private const val HALF_DROP_AIR_GAP_BLOCKS = 4.0

    private val FULL_DROP_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 3.0)

    /** Real-rise boundaries for the drop-extended ceilings, placed between measured clusters. */
    private const val HALF_DROP_RISE = -0.25

    private const val FULL_DROP_RISE = -0.75

    /** Real ascents at or below this fly like flat jumps; above it, the rising reach applies. */
    private const val NEAR_FLAT_RISE = 0.75
}
