package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.ActionSet
import com.lambda.pathing.search.AnchorRollout
import com.lambda.pathing.search.FinishPlanner
import com.lambda.pathing.search.Outcome
import com.lambda.pathing.search.PlanImprover
import com.lambda.pathing.search.PlanSegment
import com.lambda.pathing.search.Solution
import com.lambda.pathing.search.ValueAnchor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The budget is a session ceiling shared by crossing, replay, re-solve and terminal attempts. */
class PlanImproverBudgetTest {
    private val action = TrajectoryDecision.Walk(false, null, 1, false)
    private val rollouts = mockk<AnchorRollout>()
    private val vocabulary = mockk<ActionSet>()
    private val finisher = mockk<FinishPlanner>()
    private val improver = PlanImprover(rollouts, vocabulary, finisher)
    private val approach = TerminalApproach(false, 1, 0.0, null)
    private val root = improverAnchor(0, null, decision = action)
    private val middle = improverAnchor(10, root, decision = action)
    private val tip = improverAnchor(20, middle, decision = action)

    init {
        every { vocabulary.actions(any(), any()) } returns List(8) { PricedDecision(action, DecisionPrice.FREE) }
    }

    @Test
    fun `rejected crossings cannot overrun a slice and later calls use the cumulative ceiling`() {
        every { rollouts.transition(any(), any(), any()) } returns Outcome.Rejected(mockk())
        val solution = Solution.of(tip, emptyList(), approach, 0)
        assertNull(improver.improve(solution, 1))
        assertEquals(1, improver.rolloutsSpent)
        assertNull(improver.improve(solution, 1))
        assertNull(improver.improve(solution, 0))
        assertEquals(1, improver.rolloutsSpent)
        assertNull(improver.improve(solution, 3))
        assertEquals(3, improver.rolloutsSpent)
        verify(exactly = 3) { rollouts.transition(any(), any(), any()) }
        verify(exactly = 0) { finisher.finishFrom(any(), any(), any()) }
    }

    @Test
    fun `deeper crossings share the callers limit`() {
        every { rollouts.transition(any(), any(), any()) } answers {
            val parent = firstArg<ValueAnchor>()
            Outcome.Anchored(improverAnchor(parent.elapsed + 1, parent, x = -100, decision = action))
        }
        assertNull(improver.improve(Solution.of(tip, emptyList(), approach, 0), 2))
        assertEquals(2, improver.rolloutsSpent)
        verify(exactly = 2) { rollouts.transition(any(), any(), any()) }
    }

    @Test
    fun `finished solution attempts use the same ceiling as transitions`() {
        val crossing = improverAnchor(1, root, x = 20, decision = action)
        every { rollouts.transition(any(), any(), any()) } returns Outcome.Anchored(crossing)
        var terminalAttempts = 0
        every { finisher.finishFrom(any(), any(), any()) } answers {
            val permit = thirdArg<() -> Boolean>()
            repeat(8) { if (permit()) terminalAttempts++ }
            null
        }
        assertNull(improver.improve(Solution.of(tip, emptyList(), approach, 0), 3))
        assertEquals(2, terminalAttempts)
        assertEquals(3, improver.rolloutsSpent)
        verify(exactly = 1) { rollouts.transition(any(), any(), any()) }
    }
}

/** A grounded, slowly walking body at x = [frame] on a flat line; the improver's test spine. */
internal fun improverState(frame: Int, x: Double = frame.toDouble()) = MovementSimulationState.synthetic(
    profile = ProbeScenarios.PROFILE,
    position = Vec3d(x, 100.0, 0.5),
    rotation = Rotation(0.0, 0.0),
    velocity = Vec3d(0.1, 0.0, 0.0),
    onGround = true,
)

internal fun improverSegment(frames: Int, start: Int = 0): PlanSegment = PlanSegment.Terminal(
    approach = TerminalApproach(false, 1, 0.0, null),
    entry = improverState(start),
    exit = improverState(start + frames),
    inputs = List(frames) { MovementSimulationInput() },
    startFrame = start,
)

/** An anchor [elapsed] frames in, standing on cell [x], whose state sits at x = [x] so equal cells rejoin exactly. */
internal fun improverAnchor(
    elapsed: Int,
    parent: ValueAnchor?,
    x: Int = elapsed,
    collisions: Int = 0,
    decision: TrajectoryDecision?,
): ValueAnchor {
    val frames = elapsed - (parent?.elapsed ?: 0)
    return ValueAnchor(
        improverState(elapsed, x.toDouble()), Stance(x, 100, 0), elapsed, collisions, 0, 0, parent,
        List(frames) { MovementSimulationInput() }, elapsed,
    ).also { if (parent != null) it.decision = decision }
}
