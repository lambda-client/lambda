package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.search.PlanSegment
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.actions.TrajectoryDecision
import org.junit.jupiter.api.Tag
import kotlin.math.hypot
import kotlin.test.Test

/**
 * Where a certified tape spends frames that a better tape would not. Frame classes:
 * airborne, ground dwell between flights (the first grounded tick after a landing is the
 * unavoidable one), standing, grounded below sprint speed, grounded at speed. Plus the
 * two kinematic floors: the path's own length flown at the gait rate (0.35 b/t) and
 * sprinted (0.28 b/t). The gap between frames and those floors is the slack any vocabulary
 * would have to find.
 */
@Tag("bedrock-corpus")
class TapeSlackAuditTest {
    @Test
    fun `where the frames go`() {
        val constraints = MotionConstraints()
        println("[slack] %-18s %6s %5s %6s %6s %6s %6s %6s  %6s %6s %s".format(
            "plan", "frames", "jumps", "air", "dwell", "stand", "slow", "fast", "gait", "sprint", "dwell histogram"))
        val plans = ProbeScenarios.all().mapNotNull { s ->
            (ProbeScenarios.planned(s).result as? PathPlanResult.Planned)?.path?.takeIf { !it.partial }?.plan?.let { s.name to it }
        } + (5..12).mapNotNull { seed ->
            val s = ProbeScenarios.parkour(seed)
            (ProbeScenarios.plan(s, microsPerExpansion = 83L, parallelism = 4).result as? PathPlanResult.Planned)
                ?.path?.takeIf { !it.partial }?.plan?.let { s.name to it }
        }
        for ((name, plan) in plans) audit(name, plan, constraints)
    }

    private fun audit(name: String, plan: TrajectoryPlan, constraints: MotionConstraints) {
        val frames = plan.frames
        var air = 0; var dwell = 0; var stand = 0; var slow = 0; var fast = 0; var jumps = 0
        var bumpsAir = 0; var bumpsGround = 0; var wasColliding = plan.initialState.horizontalCollision
        var length = 0.0
        var previous = plan.initialState
        var groundedRun = 0
        var sawAir = false
        val runs = ArrayList<Int>()
        val blame = ArrayList<String>()
        for (frame in frames) {
            val state = frame.state
            length += hypot(state.position.x - previous.position.x, state.position.z - previous.position.z)
            val speed = state.velocity.horizontalLength()
            if (state.horizontalCollision && !wasColliding) { if (state.onGround) bumpsGround++ else bumpsAir++ }
            wasColliding = state.horizontalCollision
            if (!state.onGround) {
                air++
                if (previous.onGround) {
                    jumps++
                    if (sawAir && groundedRun > 0) {
                        runs += groundedRun
                        if (groundedRun >= 3) blame += "%d:%s".format(groundedRun, describe(plan, frame.index))
                    }
                }
                sawAir = true
                groundedRun = 0
            } else {
                groundedRun++
                when {
                    speed <= constraints.stoppedSpeed -> stand++
                    speed < SPRINT_SPEED -> slow++
                    else -> fast++
                }
            }
            previous = state
        }
        // Dwell: grounded ticks between two flights beyond the one unavoidable landing tick.
        dwell = runs.sumOf { it - 1 }
        val histogram = runs.groupingBy { it }.eachCount().toSortedMap()
        println("[slack] %-18s %6d %5d %6d %6d %6d %6d %6d  %6.0f %6.0f %s".format(
            name, frames.size, jumps, air, dwell, stand, slow, fast, length / GAIT_RATE, length / SPRINT_RATE, histogram))
        println("[slack]   collisions: air=$bumpsAir ground=$bumpsGround")
        if (blame.isNotEmpty()) println("[slack]   long dwells (ticks:decision launching out of them): " + blame.joinToString("  "))
    }

    /** The decision whose segment contains [frame], compactly. */
    private fun describe(plan: TrajectoryPlan, frame: Int): String {
        val segment = plan.segments.firstOrNull { frame >= it.startFrame && frame < it.endFrame } ?: return "?"
        val decision = (segment as? PlanSegment.Move)?.decision ?: return "terminal"
        return when (decision) {
            is TrajectoryDecision.RunUpLaunch -> "RunUp(retreat=%.1f,hop=%s,sprint=%s)".format(decision.retreatAlong, decision.hopAlong?.let { "%.1f".format(it) }, decision.sprint)
            is TrajectoryDecision.Launch -> "Launch(delay=%d,sprint=%s,solved=%s)".format(decision.delayFrames, decision.sprint, decision.solution != null)
            is TrajectoryDecision.Heading -> "Heading(delay=%s)".format(decision.delayFrames)
            is TrajectoryDecision.Walk -> "Walk"
            else -> decision::class.simpleName ?: "?"
        }
    }

    private companion object {
        const val SPRINT_SPEED = 0.25
        const val GAIT_RATE = 0.35
        const val SPRINT_RATE = 0.28
    }
}
