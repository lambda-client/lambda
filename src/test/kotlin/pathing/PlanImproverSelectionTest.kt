package pathing

import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.search.ActionSet
import com.lambda.pathing.search.AnchorRollout
import com.lambda.pathing.search.FinishPlanner
import com.lambda.pathing.search.Outcome
import com.lambda.pathing.search.PlanImprover
import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.search.Solution
import com.lambda.pathing.search.ValueAnchor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Which crossings become splices: a candidate must rejoin a spine cell faster, its tail
 * must re-certify (re-solving a decision the displaced body cannot replay), and the
 * finished result must beat the incumbent on score and frames.
 */
class PlanImproverSelectionTest {
    private val walk = TrajectoryDecision.Walk(false, null, 1, false)
    private val sprint = TrajectoryDecision.Walk(true, null, 1, false)
    private val rollouts = mockk<AnchorRollout>()
    private val vocabulary = mockk<ActionSet>()
    private val finisher = mockk<FinishPlanner>()
    private val root = improverAnchor(0, null, decision = walk)
    private val middle = improverAnchor(10, root, decision = walk)
    private val tip = improverAnchor(20, middle, decision = walk)
    private val approach = TerminalApproach(false, 1, 0.0, null)
    private val improver = PlanImprover(rollouts, vocabulary, finisher, canReach = { it === root })

    init {
        every { vocabulary.actions(root, any()) } returns
            listOf(walk, sprint).map { PricedDecision(it, DecisionPrice.FREE) }
    }

    @Test
    fun `complete winner is reused without further rollouts`() {
        val crossing = improverAnchor(1, root, 20, decision = walk)
        val winner = solution(crossing)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(crossing)
        every { finisher.finishFrom(crossing, any(), any()) } answers {
            if (thirdArg<() -> Boolean>()()) winner else null
        }
        assertSame(winner, improver.improve(solution(tip), 2))
        assertEquals(2, improver.rolloutsSpent)
        verify(exactly = 1) { rollouts.transition(any(), any(), any()) }
        verify(exactly = 1) { finisher.finishFrom(any(), any(), any()) }
    }

    @Test
    fun `collision only wins cannot lengthen a completed tape`() {
        val crossing = improverAnchor(1, root, 20, decision = walk)
        val longer = solution(crossing, tail = 24)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(crossing)
        every { finisher.finishFrom(crossing, any(), any()) } answers {
            if (thirdArg<() -> Boolean>()()) longer else null
        }
        assertNull(improver.improve(solution(tip, collisions = 10), 2))
    }

    @Test
    fun `a shorter tape that bumps mid-air is refused`() {
        val crossing = improverAnchor(1, root, 20, decision = walk).also { it.airborneCollisionEvents = 1 }
        val unsafeTrade = solution(crossing, collisions = 10)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(crossing)
        every { finisher.finishFrom(crossing, any(), any()) } answers {
            if (thirdArg<() -> Boolean>()()) unsafeTrade else null
        }
        assertNull(improver.improve(solution(tip), 2))
        assertEquals(1, improver.finishCollisions)
    }

    @Test
    fun `a shorter tape that only grazes the ground is accepted despite its score`() {
        val crossing = improverAnchor(1, root, 20, decision = walk)
        val groundedTrade = solution(crossing, collisions = 10)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(crossing)
        every { finisher.finishFrom(crossing, any(), any()) } answers {
            if (thirdArg<() -> Boolean>()()) groundedTrade else null
        }
        assertSame(groundedTrade, improver.improve(solution(tip), 2))
        assertEquals(1, improver.groundedBumpAccepts)
    }

    @Test
    fun `a failed finish does not hide the next crossing from the same departure`() {
        val first = improverAnchor(1, root, 20, decision = walk)
        val second = improverAnchor(2, root, 20, decision = walk)
        val winner = solution(second)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(first)
        every { rollouts.transition(root, sprint, null) } returns Outcome.Anchored(second)
        every { finisher.finishFrom(any(), any(), any()) } answers {
            if (!thirdArg<() -> Boolean>()()) null else winner.takeIf { firstArg<ValueAnchor>() === second }
        }
        assertSame(winner, improver.improve(solution(tip), 4))
        assertEquals(4, improver.rolloutsSpent)
    }

    @Test
    fun `a slower arrival on a spine cell is not a rejoin`() {
        val late = improverAnchor(25, root, 20, decision = walk)
        every { rollouts.transition(root, any(), null) } returns Outcome.Anchored(late)
        assertNull(improver.improve(solution(tip), 4))
        verify(exactly = 0) { finisher.finishFrom(any(), any(), any()) }
    }

    @Test
    fun `a stale decision is re-solved from the displaced body with the same edge`() {
        // Rejoin at the middle cell (10) faster; the spine's next decision is a launch onto cell 20.
        val launch = TrajectoryDecision.Launch(true, Stance(20, 100, 0), delayFrames = 3)
        val fresh = TrajectoryDecision.Launch(true, Stance(20, 100, 0), delayFrames = 1)
        val otherEdge = TrajectoryDecision.Launch(true, Stance(30, 100, 0), delayFrames = 1)
        val tip = improverAnchor(20, middle, decision = launch)
        val crossing = improverAnchor(6, root, 10, decision = walk)
        val landed = improverAnchor(14, crossing, 20, decision = fresh)
        val winner = solution(landed)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(crossing)
        every { rollouts.transition(crossing, launch, null) } returns Outcome.Rejected(mockk())
        every { vocabulary.actions(crossing, any()) } returns
            listOf(otherEdge, fresh).map { PricedDecision(it, DecisionPrice.FREE) }
        every { rollouts.transition(crossing, fresh, null) } returns Outcome.Anchored(landed)
        every { finisher.finishFrom(landed, any(), any()) } answers { if (thirdArg<() -> Boolean>()()) winner else null }

        // Exactly one round's worth: crossing + stale replay + fresh solution + finish.
        assertSame(winner, improver.improve(solution(tip), 4))
        assertEquals(1, improver.resolvedDecisions)
        verify(exactly = 0) { rollouts.transition(crossing, otherEdge, null) }
        assertEquals(4, improver.rolloutsSpent)
    }

    private fun solution(anchor: ValueAnchor, tail: Int = 1, collisions: Int = 0) = Solution.of(
        anchor, List(tail) { SimulatedTrajectoryFrame(it, MovementSimulationInput(), anchor.state) },
        approach, collisions,
    )
}
