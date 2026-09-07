package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.execution.ImprovementArbiter
import com.lambda.pathing.coarse.CoarseKinematicEnvelope
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.search.MotionPlanResult
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.search.TrajectoryPlanId
import com.lambda.pathing.search.ValueFieldAnchorSearch
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import pathing.ProbeScenarios.PROFILE

class ImprovementArbiterTest {
    @Test
    fun `no running tape or no cursor begins fresh`() {
        val offered = published(east3, generation = 1, sequence = 1)
        assertIs<ImprovementArbiter.Verdict.BeginFresh>(
            ImprovementArbiter.judge(null, 0, awaitingObservation = false, offered = offered),
        )
        val running = published(east6, generation = 1, sequence = 1)
        assertIs<ImprovementArbiter.Verdict.BeginFresh>(
            ImprovementArbiter.judge(running, null, awaitingObservation = false, offered = offered),
        )
    }

    @Test
    fun `a different planning generation is kept out`() {
        val running = published(east6, generation = 1, sequence = 1)
        val offered = published(east3, generation = 2, sequence = 2)
        val verdict = ImprovementArbiter.judge(running, 0, false, offered)
        assertEquals(
            "publication belongs to a different planning generation",
            assertIs<ImprovementArbiter.Verdict.Keep>(verdict).reason,
        )
    }

    @Test
    fun `stale publication sequence is kept out`() {
        val running = published(east6, generation = 1, sequence = 3)
        val offered = published(east3, generation = 1, sequence = 3)
        val verdict = ImprovementArbiter.judge(running, 0, false, offered)
        assertEquals(
            "publication is older than the running tape",
            assertIs<ImprovementArbiter.Verdict.Keep>(verdict).reason,
        )
    }

    @Test
    fun `improvements defer while an input awaits observation`() {
        val running = published(east6, generation = 1, sequence = 1)
        val offered = published(east3, generation = 1, sequence = 2)
        assertIs<ImprovementArbiter.Verdict.DeferForObservation>(
            ImprovementArbiter.judge(running, 0, awaitingObservation = true, offered = offered),
        )
    }

    @Test
    fun `an improvement already walked past is kept out`() {
        val running = published(east6, generation = 1, sequence = 1)
        val offered = published(east3, generation = 1, sequence = 2)
        val behind = east3.plan.tape.frameCount + 1
        val verdict = ImprovementArbiter.judge(running, behind, false, offered)
        assertEquals(
            "improvement is shorter than the walk so far",
            assertIs<ImprovementArbiter.Verdict.Keep>(verdict).reason,
        )
    }

    @Test
    fun `a complete tape that is not shorter is kept out`() {
        val running = published(east3, generation = 1, sequence = 1)
        val offered = published(east6, generation = 1, sequence = 2)
        val verdict = ImprovementArbiter.judge(running, 0, false, offered)
        assertEquals(
            "improvement is not shorter than the running tape",
            assertIs<ImprovementArbiter.Verdict.Keep>(verdict).reason,
        )
    }

    @Test
    fun `a partial tape without a new anchor is kept out`() {
        val running = published(east6, generation = 1, sequence = 1, partial = true, safeAnchorFrame = 20)
        val offered = published(east6, generation = 1, sequence = 2, partial = true, safeAnchorFrame = 20)
        val verdict = ImprovementArbiter.judge(running, 0, false, offered)
        assertEquals(
            "partial publication commits no new anchor",
            assertIs<ImprovementArbiter.Verdict.Keep>(verdict).reason,
        )
    }

    @Test
    fun `a partial tape whose anchor the cursor passed is kept out`() {
        val running = published(east6, generation = 1, sequence = 1, partial = true, safeAnchorFrame = 5)
        val offered = published(east6, generation = 1, sequence = 2, partial = true, safeAnchorFrame = 20)
        val verdict = ImprovementArbiter.judge(running, 25, false, offered)
        assertEquals(
            "partial publication has no unexecuted progress anchor",
            assertIs<ImprovementArbiter.Verdict.Keep>(verdict).reason,
        )
    }

    @Test
    fun `an improvement that diverges behind the cursor is kept out`() {
        assertTrue(east6.plan.tape[0] != south3.plan.tape[0], "fixture tapes must diverge at frame 0")
        val running = published(east6, generation = 1, sequence = 1)
        val offered = published(south3, generation = 1, sequence = 2)
        val verdict = ImprovementArbiter.judge(running, 1, false, offered)
        assertEquals(
            "improvement diverges behind the cursor",
            assertIs<ImprovementArbiter.Verdict.Keep>(verdict).reason,
        )
    }

    @Test
    fun `a shorter complete tape is adopted at the cursor frame`() {
        val running = published(east6, generation = 1, sequence = 1)
        val offered = published(east3, generation = 1, sequence = 2)
        val verdict = ImprovementArbiter.judge(running, 0, false, offered)
        assertEquals(0, assertIs<ImprovementArbiter.Verdict.Adopt>(verdict).frame)
    }

    @Test
    fun `a partial tape committing a further anchor is adopted`() {
        val running = published(east6, generation = 1, sequence = 1, partial = true, safeAnchorFrame = 5)
        val offered = published(east6, generation = 1, sequence = 2, partial = true, safeAnchorFrame = 20)
        val verdict = ImprovementArbiter.judge(running, 10, false, offered)
        assertEquals(10, assertIs<ImprovementArbiter.Verdict.Adopt>(verdict).frame)
    }

    private data class Certified(val route: CoarseRoutePlan, val plan: TrajectoryPlan)

    private fun published(
        certified: Certified,
        generation: Long,
        sequence: Int,
        partial: Boolean = false,
        safeAnchorFrame: Int = 0,
    ) = PublishedPath(
        route = certified.route,
        plan = certified.plan,
        profile = PROFILE,
        parameters = TerminalApproach(
            sprint = true, lookAheadNodes = 1, brakeDistance = 0.25, stepUpJumpLeadDistance = null,
        ),
        safeAnchorStance = certified.route.goal,
        safeAnchorFrame = safeAnchorFrame,
        remainingGuideTicks = 0.0,
        attempts = 1,
        planMillis = 0,
        finalGoal = certified.route.goal,
        partial = partial,
        planningGeneration = generation,
        publicationSequence = sequence,
    )

    private companion object {

        val environment: SnapshotSimulationEnvironment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-3, -3, -3, 9, 4, 9),
            buildMap {
                for (x in -3..9) for (z in -3..9) put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            },
        )

        val moves = SimpleMoveLibrary.build(
            costs = CoarseKinematicEnvelope(0.6, 0.5, 4.0).moveCosts(),
            options = SimpleMoveOptions(
                allowDiagonal = false, allowStepUp = false,
                maxWalkOffDepth = 0, allowJumpCandidates = false,
            ),
        )

        val east3 = certify(Stance(3, 0, 0), planId = 1)
        val east6 = certify(Stance(6, 0, 0), planId = 2)
        val south3 = certify(Stance(0, 0, 3), planId = 3)

        init {
            check(east3.plan.tape.frameCount < east6.plan.tape.frameCount) {
                "fixture requires the shorter goal to certify a shorter tape"
            }
            check(south3.plan.tape.frameCount < east6.plan.tape.frameCount) {
                "fixture requires the divergent goal to certify a shorter tape"
            }
        }

        private fun certify(goal: Stance, planId: Long): Certified {
            val start = Stance(0, 0, 0)
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 0.0, 0.5),
                rotation = Rotation(-90.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            )
            val planner = CoarsePlanner(environment, moves, start, goal)
            check(planner.repair(Duration.INFINITE).converged)
            planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
            val route = requireNotNull(planner.routePlan(planId))
            val seed = ValueFieldAnchorSearch.search(
                route, moves.catalog, planner.valueField(), initial, PROFILE, environment,
                MotionConstraints(),
            )
            check(seed is MotionPlanResult.Success) { "fixture search must certify: $seed" }
            return Certified(route, TrajectoryPlan.fromWalkingSeed(TrajectoryPlanId(planId), seed, PROFILE))
        }
    }
}
