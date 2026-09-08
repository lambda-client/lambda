package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.families.SlimeBounceMovement
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.AnchorRollout
import com.lambda.pathing.search.AttemptAccumulator
import com.lambda.pathing.search.Outcome
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.ValueAnchor
import com.lambda.pathing.search.ValueFieldSearchConfig
import java.nio.file.Path
import kotlin.test.Test
import kotlin.time.Duration
import net.minecraft.util.math.Vec3d

/** Roll the coarse edges leaving one cell of a live dump from a standing body, frame by frame. */
class DumpBounceProbeTest {
    @Test
    fun `roll the edges leaving a cell`() {
        val dir = System.getenv("PATHING_DUMP_DIR") ?: return
        val file = System.getenv("PATHING_DUMP_FILE") ?: return
        val (sx, sy, sz) = (System.getenv("PATHING_DUMP_START") ?: return).split(',').map { it.trim().toInt() }
        val (gx, gy, gz) = (System.getenv("PATHING_DUMP_WAYPOINTS") ?: return).split(';').first().split(',').map { it.trim().toInt() }
        val start = Stance(sx, sy, sz)
        val goal = Stance(gx, gy, gz)
        val loaded = PlanDump.read(Path.of(dir, file))
        val environment = loaded.environment()
        val state = CoarsePlanningState(environment, loaded.moveOptions, start, goal)
        state.repairFrom(start, emptySet(), emptySet())
        check(state.planner.repair(Duration.INFINITE).converged)
        state.planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val field = state.planner.valueField()
        val constraints = loaded.searchConfig
        val surface = environment.standingSurface(start.x, start.y - 1, start.z) ?: 0.0
        // PATHING_DUMP_BODY "x,y,z,vx,vy,vz,yaw" replaces the resting body with an arrival state.
        val arrival = System.getenv("PATHING_DUMP_BODY")?.split(',')?.map { it.trim().toDouble() }
        val body = MovementSimulationState.synthetic(
            profile = loaded.profile,
            position = arrival?.let { Vec3d(it[0], it[1], it[2]) } ?: Vec3d(start.x + 0.5, start.y - 1 + surface, start.z + 0.5),
            rotation = Rotation(arrival?.get(6) ?: 0.0, 0.0),
            velocity = arrival?.let { Vec3d(it[3], it[4], it[5]) } ?: Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val root = ValueAnchor(body, start, 0, 0, 0, 0, null, emptyList(), 0)
        val rollouts = AnchorRollout(
            state.planner.moves.catalog, field, constraints, ValueFieldSearchConfig(), environment, loaded.profile,
            goalPoint = { com.lambda.pathing.core.HorizontalPoint(goal.x + 0.5, goal.y.toDouble(), goal.z + 0.5) },
            attempts = AttemptAccumulator(), progressOf = { 0 }, probe = SearchProbe.NONE,
        )
        println("[bounce-probe] body at ${body.position}, surface=$surface")
        for (edge in field.steps(start, 8, 1000.0, null)) {
            println("[bounce-probe] edge ${edge.movement} -> ${edge.to} lower=${edge.lowerBoundTicks} bounce=${edge.bounce} launch=${edge.launch}")
            val owner = state.planner.moves.catalog[edge.movement] ?: continue
            val decisions = owner.decisions(DecisionContext(root, edge, constraints, field.view, steering = field))
            for (decision in decisions) {
                val prepared = rollouts.prepare(root, decision) ?: continue
                rollouts.execute(prepared)
                val frames = prepared.raw!!.frames
                val outcome = rollouts.complete(prepared, null)
                val kind = when (outcome) {
                    is Outcome.Anchored -> "ANCHORED at ${outcome.anchor.stance} elapsed=${outcome.anchor.elapsed}"
                    is Outcome.Rejected -> "REJECTED ${outcome.diagnostic}"
                    is Outcome.Blocked -> "BLOCKED"
                    is Outcome.Arrived -> "ARRIVED"
                }
                println("[bounce-probe]   $decision -> $kind (${frames.size} frames)")
                frames.filterIndexed { i, _ -> i % 3 == 0 || i == frames.lastIndex }.forEach { f ->
                    val p = f.state.position
                    println("[bounce-probe]     f%-3d (%.2f, %.2f, %.2f) v=(%.3f, %.3f, %.3f) ground=%s jump=%s sprint=%s".format(
                        f.index, p.x, p.y, p.z, f.state.velocity.x, f.state.velocity.y, f.state.velocity.z, f.state.onGround, f.input.jump, f.input.sprint))
                }
            }
        }
    }
}
