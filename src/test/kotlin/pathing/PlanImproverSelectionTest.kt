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
import com.lambda.pathing.search.SimulatedTrajectoryFrame
import com.lambda.pathing.search.Solution
import com.lambda.pathing.search.ValueAnchor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PlanImproverSelectionTest {
    private val walk = TrajectoryDecision.Walk(false, null, 1, false)
    private val sprint = TrajectoryDecision.Walk(true, null, 1, false)
    private val rollouts = mockk<AnchorRollout>()
    private val vocabulary = mockk<ActionSet>()
    private val finisher = mockk<FinishPlanner>()
    private val root = anchor(0, null)
    private val middle = anchor(10, root)
    private val tip = anchor(20, middle)
    private val approach = TerminalApproach(false, 1, 0.0, null)
    private val improver = PlanImprover(rollouts, vocabulary, finisher, canReach = { it === root })

    init {
        every { vocabulary.actions(root, any()) } returns
            listOf(walk, sprint).map { PricedDecision(it, DecisionPrice.FREE) }
    }

    @Test
    fun `complete winner is reused without segment reconstruction or further rollouts`() {
        val crossing = anchor(1, root, 20)
        val winner = solution(crossing)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(crossing)
        every { finisher.finishFrom(crossing, any()) } answers {
            if (secondArg<() -> Boolean>()()) winner else null
        }
        assertSame(winner, improver.improve(solution(tip), 2))
        assertEquals(2, improver.rolloutsSpent)
        verify(exactly = 1) { rollouts.transition(any(), any(), any()) }
        verify(exactly = 1) { finisher.finishFrom(any(), any()) }
    }

    @Test
    fun `collision only wins cannot lengthen a completed tape`() {
        val crossing = anchor(1, root, 20)
        val longer = solution(crossing, tail = 24)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(crossing)
        every { finisher.finishFrom(crossing, any()) } answers {
            if (secondArg<() -> Boolean>()()) longer else null
        }
        assertNull(improver.improve(solution(tip, collisions = 10), 2))
    }

    @Test
    fun `a shorter tape with a worse collision score is refused`() {
        val crossing = anchor(1, root, 20)
        val unsafeTrade = solution(crossing, collisions = 10)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(crossing)
        every { finisher.finishFrom(crossing, any()) } answers {
            if (secondArg<() -> Boolean>()()) unsafeTrade else null
        }
        assertNull(improver.improve(solution(tip), 2))
    }

    @Test
    fun `a failed finish does not hide the next crossing from the same departure`() {
        val first = anchor(1, root, 20)
        val second = anchor(2, root, 20)
        val winner = solution(second)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(first)
        every { rollouts.transition(root, sprint, null) } returns Outcome.Anchored(second)
        every { finisher.finishFrom(any(), any()) } answers {
            if (!secondArg<() -> Boolean>()()) null else winner.takeIf { firstArg<ValueAnchor>() === second }
        }
        assertSame(winner, improver.improve(solution(tip), 4))
        assertEquals(4, improver.rolloutsSpent)
    }

    @Test
    fun `a costly first rejoin does not hide a better partial shortcut`() {
        val first = anchor(1, root, 20, collisions = 10)
        val second = anchor(2, root, 20)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(first)
        every { rollouts.transition(root, sprint, null) } returns Outcome.Anchored(second)
        assertSame(second, improver.improveTip(tip, 2))
        assertEquals(2, improver.rolloutsSpent)
        verify(exactly = 0) { finisher.finishFrom(any(), any()) }
    }

    @Test
    fun `accepting a partial shortcut does not simulate unused crossing alternatives`() {
        val winner = anchor(1, root, 20)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(winner)
        assertSame(winner, improver.improveTip(tip, 100))
        assertEquals(1, improver.rolloutsSpent)
        verify(exactly = 1) { rollouts.transition(any(), any(), any()) }
    }

    @Test
    fun `a failed replay does not hide the next crossing`() {
        val first = anchor(1, root, 10)
        val second = anchor(2, root, 20)
        every { rollouts.transition(root, walk, null) } returns Outcome.Anchored(first)
        every { rollouts.transition(first, walk, null) } returns Outcome.Rejected(mockk())
        every { rollouts.transition(root, sprint, null) } returns Outcome.Anchored(second)
        assertSame(second, improver.improveTip(tip, 3))
        assertEquals(3, improver.rolloutsSpent)
    }

    private fun solution(anchor: ValueAnchor, tail: Int = 1, collisions: Int = 0) = Solution.of(
        anchor, List(tail) { SimulatedTrajectoryFrame(it, MovementSimulationInput(), anchor.state) },
        approach, collisions,
    )

    private fun anchor(elapsed: Int, parent: ValueAnchor?, x: Int = elapsed, collisions: Int = 0): ValueAnchor {
        val segment = graphSegment(elapsed - (parent?.elapsed ?: 0), x)
        return ValueAnchor(segment.entry, Stance(x, 100, 0), elapsed, collisions, 0, 0, parent,
            segment.inputs, elapsed).also { if (parent != null) it.decision = walk }
    }
}
