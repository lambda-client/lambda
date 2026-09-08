package pathing

import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.TerminalApproach
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.ActionSet
import com.lambda.pathing.search.AnchorRollout
import com.lambda.pathing.search.FinishPlanner
import com.lambda.pathing.search.Outcome
import com.lambda.pathing.search.PlanImprover
import com.lambda.pathing.search.Solution
import com.lambda.pathing.search.ValueAnchor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The budget is a session ceiling shared by crossing, replay and terminal attempts. */
class PlanImproverBudgetTest {
    private val action = TrajectoryDecision.Walk(false, null, 1, false)
    private val rollouts = mockk<AnchorRollout>()
    private val vocabulary = mockk<ActionSet>()
    private val finisher = mockk<FinishPlanner>()
    private val improver = PlanImprover(rollouts, vocabulary, finisher)
    private val approach = TerminalApproach(false, 1, 0.0, null)
    private val root = anchor(0, null)
    private val middle = anchor(10, root)
    private val tip = anchor(20, middle)

    init {
        every { vocabulary.actions(any(), any()) } returns List(8) { PricedDecision(action, DecisionPrice.FREE) }
    }

    @Test
    fun `rejected crossings cannot overrun a slice and later calls use cumulative ceiling`() {
        every { rollouts.transition(any(), any(), any()) } returns Outcome.Rejected(mockk())
        assertNull(improver.improveTip(tip, 1))
        assertEquals(1, improver.rolloutsSpent)
        assertNull(improver.improveTip(tip, 1))
        assertNull(improver.improveTip(tip, 0))
        assertNull(improver.improveTip(tip, -1))
        assertEquals(1, improver.rolloutsSpent)
        assertNull(improver.improveTip(tip, 3))
        assertEquals(3, improver.rolloutsSpent)
        verify(exactly = 3) { rollouts.transition(any(), any(), any()) }
        verify(exactly = 0) { finisher.finishFrom(any(), any()) }
    }

    @Test
    fun `recursive crossings share the callers limit`() {
        every { rollouts.transition(any(), any(), any()) } answers {
            val parent = firstArg<ValueAnchor>()
            Outcome.Anchored(anchor(parent.elapsed + 1, parent, x = -100))
        }
        assertNull(improver.improveTip(tip, 2))
        assertEquals(2, improver.rolloutsSpent)
        verify(exactly = 2) { rollouts.transition(any(), any(), any()) }
    }

    @Test
    fun `tail replay cannot start after crossing consumed the last unit`() {
        val crossing = anchor(1, root, x = 10)
        every { rollouts.transition(any(), any(), any()) } returns Outcome.Anchored(crossing)
        assertNull(improver.improveTip(tip, 1))
        assertEquals(1, improver.rolloutsSpent)
        verify(exactly = 1) { rollouts.transition(any(), any(), any()) }
    }

    @Test
    fun `finished solution attempts use the same ceiling as transitions`() {
        val crossing = anchor(1, root, x = 20)
        every { rollouts.transition(any(), any(), any()) } returns Outcome.Anchored(crossing)
        var terminalAttempts = 0
        every { finisher.finishFrom(any(), any()) } answers {
            val permit = secondArg<() -> Boolean>()
            repeat(8) { if (permit()) terminalAttempts++ }
            null
        }
        val solution = Solution.of(tip, emptyList(), approach, 0)
        assertNull(improver.improve(solution, 3))
        assertEquals(2, terminalAttempts)
        assertEquals(3, improver.rolloutsSpent)
        verify(exactly = 1) { rollouts.transition(any(), any(), any()) }
    }

    private fun anchor(elapsed: Int, parent: ValueAnchor?, x: Int = elapsed): ValueAnchor {
        val segment = graphSegment(elapsed - (parent?.elapsed ?: 0), x)
        return ValueAnchor(segment.entry, Stance(x, 100, 0), elapsed, 0, 0, 0, parent,
            segment.inputs, elapsed).also { if (parent != null) it.decision = action }
    }
}
