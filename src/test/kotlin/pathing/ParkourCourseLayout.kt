package com.lambda.pathing.debug

import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import kotlin.math.abs
import kotlin.random.Random
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes

/**
 * A seeded parkour course: isolated pads separated by gaps that must be jumped.
 *
 * The corpus had no fixture of this shape and it showed. [pathing.BedrockFieldLayout] is open
 * terrain where the body can walk almost anywhere, so a trajectory search that struggles
 * on gaps still scores well on it; the only parkour fixtures were two-jump toys that
 * finish in 36 frames, short enough that the terminal stop dominates them and long enough
 * to prove nothing. Every conclusion drawn about parkour from that corpus was drawn from
 * open ground and a pair of pillars.
 *
 * The generator is deliberately austere. Each pad is a single block with nothing around
 * it, so there is exactly one way across each gap and no walking alternative -- which
 * makes the coarse route unambiguous and the trajectory layer's excess over it the only
 * thing being measured. Gaps are drawn from what a sprint jump can actually clear so
 * every course is solvable by construction; a fixture the planner *should* fail is a
 * different test.
 */
object ParkourCourseLayout {

    const val SEED = 0x9A2C_0DE

    const val BASE_Y = 100

    /**
     * Reachable gaps, as (span, rise).
     *
     * Spans of two and three only, and the reason is the pad rather than the jump. A
     * sprint jump clears four blocks *given cruise speed*, and cruise needs a run-up: from
     * a one-block landing pad the body has a tick or two of contact and nothing like the
     * room to build it. Four-block gaps were in this list originally and brute force --
     * every solution against every launch delay from every reachable landing state -- could
     * not cross a single one of them. A fixture that no controller can solve measures
     * nothing about the controller.
     *
     * The sideways drift below turns some of these into 3.16 blocks, which brute force
     * does clear, so that is the real ceiling for a pad-to-pad chain.
     */
    private val GAPS = listOf(
        2 to 0, 3 to 0,
        2 to 1, 3 to 1,
        2 to -1, 3 to -1, 3 to -2,
    )

    /**
     * Horizontal extent of a pad, in blocks, centred in its cell.
     *
     * A full block is already enough to break the planner, so that is the default -- but
     * the narrower shapes are the interesting ones for accuracy work. The coarse layer
     * sees a standable cell whatever this is, exactly as it does in a real world: the
     * graph is optimistic about geometry and the trajectory layer is what has to certify
     * that the body actually lands on it.
     */
    enum class PadShape(val width: Double) {
        BLOCK(1.0),
        SLAB(0.5),
        POST(0.25),
        ;

        fun shape(): VoxelShape {
            if (this == BLOCK) return VoxelShapes.fullCube()
            val inset = (1.0 - width) / 2.0
            return VoxelShapes.cuboid(inset, 0.0, inset, 1.0 - inset, 1.0, 1.0 - inset)
        }
    }

    data class Course(
        val pads: Set<VoxelPos>,
        val start: Stance,
        val goal: Stance,
    ) {
        /** Blocks between the endpoints, for sanity-checking a generated course. */
        val span: Int get() = abs(goal.x - start.x) + abs(goal.z - start.z)
    }

    /**
     * A course of [jumps] gaps, wandering in x and z so the body has to turn as well as leap.
     *
     * The landing pad of each gap is the takeoff pad of the next, so the course is a
     * chain with no branches: the coarse planner has one route and the trajectory layer
     * has to certify every gap on it.
     */
    fun course(jumps: Int = 24, seed: Int = SEED): Course {
        val random = Random(seed)
        val pads = LinkedHashSet<VoxelPos>()
        var x = 0
        var y = BASE_Y - 1
        var z = 0
        pads += VoxelPos(x, y, z)

        // A standing platform at the start: the first gap should be a jump, not a
        // standing-start problem, which is measured elsewhere.
        for (approach in 1..4) pads += VoxelPos(x - approach, y, z)

        repeat(jumps) {
            val (span, rise) = GAPS[random.nextInt(GAPS.size)]
            // Turning between gaps is what makes a course parkour rather than a corridor,
            // but a right angle off a one-block pad is not a jump anyone lands, so the
            // heading turns at most once per pad and never doubles back.
            val sideways = random.nextInt(3) - 1
            val nextY = (y + rise).coerceIn(BASE_Y - 6, BASE_Y + 6)
            x += span
            z += sideways
            y = nextY
            pads += VoxelPos(x, y, z)
        }

        // A landing platform, so the walk ends on ground it can stop on rather than
        // measuring how well the finisher balances on a single block.
        for (run in 1..4) pads += VoxelPos(x + run, y, z)

        return Course(
            pads = pads,
            start = Stance(0, BASE_Y, 0),
            goal = Stance(x + 3, y + 1, z),
        )
    }
}
