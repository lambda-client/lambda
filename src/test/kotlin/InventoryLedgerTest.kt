/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.lambda.interaction.manager.managers.inventory.InventoryLedger
import com.lambda.interaction.manager.managers.inventory.InventoryLedger.SingleDecision.Apply
import com.lambda.interaction.manager.managers.inventory.InventoryLedger.SingleDecision.Drop
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Challenges [InventoryLedger] with the exact failure modes of the old value-matching cache:
 * delayed echoes after rapid actions, stale full snapshots clobbering newer slots, repeated
 * identical values, orphaned entries eating real corrections, and server rejections.
 *
 * Stacks are plain data (no Minecraft classes) so these run as fast JVM unit tests.
 */
class InventoryLedgerTest {

    private data class TestStack(val item: String, val count: Int)

    private fun stone(count: Int) = TestStack("stone", count)
    private fun dirt(count: Int) = TestStack("dirt", count)

    private class ManualClock(var now: Instant = Instant.fromEpochMilliseconds(0)) {
        fun advance(ms: Long) {
            now = now.plus(ms.milliseconds)
        }
    }

    private fun ledger(clock: ManualClock, maxAgeMs: Long = 60_000L) =
        InventoryLedger<TestStack>({ a, b -> a == b }, maxAgeMs = maxAgeMs, clock = { clock.now })

    @Test
    fun `twelve rapid placements with delayed echoes keep the predicted count`() {
        val clock = ManualClock()
        val ledger = ledger(clock)
        val slot = 36

        // Client predicts 64 -> 52 while the server answers are still in flight.
        var count = 64
        repeat(12) {
            ledger.record(0, slot, stone(count), stone(count - 1))
            count--
        }
        assertEquals(52, count)
        assertEquals(12, ledger.pendingCount(0, slot))

        // The 12 echoes arrive late, after all predictions. Every one must be dropped.
        repeat(12) { i ->
            assertEquals(Drop, ledger.decideSingle(0, slot, stone(63 - i)), "echo ${63 - i} must be dropped")
        }
        assertEquals(0, ledger.totalPending())

        // Something genuinely new (blocks removed by something else) must still apply.
        assertEquals(Apply, ledger.decideSingle(0, slot, stone(48)))
    }

    @Test
    fun `stale full snapshot does not clobber slots changed after it was sent`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        // Slot 0 was decremented, then slot 1 changed after the server snapshot was taken.
        ledger.record(0, 0, stone(64), stone(63))
        ledger.record(0, 1, dirt(5), dirt(4))

        val client = listOf(stone(63), dirt(4), stone(10))
        // Server snapshot predates both changes; slot 2 has an unrelated external change.
        val incoming = listOf(stone(64), dirt(5), stone(11))

        assertEquals(listOf(false, false, true), ledger.decideFull(0, incoming, client))

        // Nothing was consumed for the stale slots, so the real echo still drops afterwards.
        assertEquals(1, ledger.pendingCount(0, 0))
        assertEquals(Drop, ledger.decideSingle(0, 0, stone(63)))
    }

    @Test
    fun `repeated identical values consume oldest first without orphans`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        // Place (64->63), pick up (63->64), place again (64->63).
        ledger.record(0, 36, stone(64), stone(63))
        ledger.record(0, 36, stone(63), stone(64))
        ledger.record(0, 36, stone(64), stone(63))

        assertEquals(Drop, ledger.decideSingle(0, 36, stone(63)))
        assertEquals(Drop, ledger.decideSingle(0, 36, stone(64)))
        assertEquals(Drop, ledger.decideSingle(0, 36, stone(63)))
        assertEquals(0, ledger.totalPending())

        // A later genuine correction must not be eaten by a leftover entry.
        assertEquals(Apply, ledger.decideSingle(0, 36, stone(60)))
    }

    @Test
    fun `click records once and a later correction still applies`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        // A click moves the stack; the forced full update echoes it back.
        // (Slot 0, because full-packet list positions map to slot ids.)
        ledger.record(0, 0, TestStack("sword", 1), TestStack("shield", 1))

        val apply = ledger.decideFull(0, listOf(TestStack("shield", 1)), listOf(TestStack("shield", 1)))
        assertEquals(listOf(true), apply)
        assertEquals(0, ledger.totalPending(), "single record must be fully consumed, no orphan may remain")

        // A duplicate delivery of the same value applies harmlessly (same value either way).
        assertEquals(Apply, ledger.decideSingle(0, 0, TestStack("shield", 1)))

        // A real change afterwards is new information.
        ledger.record(0, 0, TestStack("shield", 1), dirt(2))
        assertEquals(Apply, ledger.decideSingle(0, 0, stone(30)))
        assertEquals(0, ledger.pendingCount(0, 0))
    }

    @Test
    fun `server rejection of a prediction reverts to the old value`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        // Client predicted a placement the server refused: the old count comes back.
        ledger.record(0, 36, stone(64), stone(63))
        assertEquals(Apply, ledger.decideSingle(0, 36, stone(64)))
        assertEquals(0, ledger.pendingCount(0, 36))
    }

    @Test
    fun `external value in a full snapshot applies and clears stale predictions`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        ledger.record(0, 0, stone(64), stone(63))
        val apply = ledger.decideFull(0, listOf(stone(70)), listOf(stone(63)))
        assertEquals(listOf(true), apply)
        assertEquals(0, ledger.pendingCount(0, 0))
    }

    @Test
    fun `expired predictions no longer filter echoes`() {
        val clock = ManualClock()
        val ledger = ledger(clock, maxAgeMs = 100L)

        ledger.record(0, 36, stone(64), stone(63))
        clock.advance(500L)
        // The recording has decayed, so the late echo is treated as new and applied.
        assertEquals(Apply, ledger.decideSingle(0, 36, stone(63)))
        assertEquals(0, ledger.totalPending())
    }

    @Test
    fun `screens do not share predictions`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        ledger.record(5, 3, stone(10), stone(9))
        assertEquals(Apply, ledger.decideSingle(0, 3, stone(9)), "other screens must not consume this")
        assertEquals(Drop, ledger.decideSingle(5, 3, stone(9)))
        ledger.record(5, 3, stone(9), stone(8))
        ledger.clear(5)
        assertEquals(Apply, ledger.decideSingle(5, 3, stone(8)))
    }

    @Test
    fun `full snapshot with mismatched sizes applies everything`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        ledger.record(0, 0, stone(64), stone(63))
        val apply = ledger.decideFull(0, listOf(stone(1), stone(2)), listOf(stone(63)))
        assertEquals(listOf(true, true), apply)
    }

    @Test
    fun `recording an unchanged slot stores nothing`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        ledger.record(0, 0, stone(64), stone(64))
        assertEquals(0, ledger.totalPending())
        assertEquals(Apply, ledger.decideSingle(0, 0, stone(64)))
    }

    @Test
    fun `random predictions with in-order echoes are all dropped`() {
        val clock = ManualClock()
        val ledger = ledger(clock)
        val random = Random(1234)
        val slots = listOf(36, 37, 38)
        val expected = mutableMapOf<Int, ArrayDeque<TestStack>>()

        repeat(200) {
            val slot = slots.random(random)
            val current = expected[slot]?.lastOrNull() ?: stone(64)
            val next = current.copy(count = (current.count + random.nextInt(-3, 2)).coerceIn(0, 64))
            if (next != current) {
                ledger.record(0, slot, current, next)
                expected.getOrPut(slot) { ArrayDeque() }.addLast(next)
            }
            clock.advance(1L)
        }

        // Server echoes arrive in order per slot; each must be recognised as our own action.
        expected.forEach { (slot, echoes) ->
            echoes.forEach { echo ->
                assertEquals(Drop, ledger.decideSingle(0, slot, echo), "echo $echo on slot $slot")
            }
        }
        assertEquals(0, ledger.totalPending())

        // And the ledger still accepts genuinely new values afterwards.
        assertTrue(ledger.decideFull(0, listOf(dirt(1)), listOf(dirt(1))) == listOf(true))
    }

    // ---- cursor: every click is sent with a mismatched revision, so the server answers each one
    // with a full packet carrying the cursor. With rapid grab/place pairs that cursor is routinely
    // stale on arrival and must be merged like a slot, or a resurrected cursor breaks the tasks
    // that read it every tick. ------------------------------------------------------------------

    private val cursor = InventoryLedger.CURSOR_SLOT_ID
    private val empty = TestStack("air", 0)
    private fun shulker(contents: String) = TestStack("shulker[$contents]", 1)

    @Test
    fun `stale cursor from an earlier click is dropped`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        // Click 1 grabs the shulker, click 2 puts it into the destination slot.
        ledger.record(0, cursor, empty, shulker("gear"))
        ledger.record(0, 10, stone(5), shulker("gear"))
        ledger.record(0, cursor, shulker("gear"), empty)

        // The first full (cursor still holding the shulker) arrives after the second click.
        assertEquals(Drop, ledger.decideCursor(0, shulker("gear")))
        // The destination slot in the same stale full is merged the same way.
        assertEquals(
            listOf(true, true, true, true, true, true, true, true, true, true, false),
            ledger.decideFull(0, List(10) { stone(5) } + stone(5), List(10) { stone(5) } + shulker("gear"))
        )
    }

    @Test
    fun `two-click grab and place consumes everything once both echoes arrive`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        ledger.record(0, cursor, empty, shulker("gear"))
        ledger.record(0, 3, stone(5), shulker("gear"))
        ledger.record(0, cursor, shulker("gear"), empty)

        // First full arrives late: cursor grab is stale, destination already holds the shulker.
        assertEquals(Drop, ledger.decideCursor(0, shulker("gear")))
        assertEquals(listOf(true, true, true, false), ledger.decideFull(0, listOf(empty, empty, empty, stone(5)), listOf(empty, empty, empty, shulker("gear"))))

        // Second full (everything current) drains the rest; nothing is left to false-match later.
        assertEquals(Drop, ledger.decideCursor(0, empty))
        assertEquals(listOf(true, true, true, true), ledger.decideFull(0, listOf(empty, empty, empty, shulker("gear")), listOf(empty, empty, empty, shulker("gear"))))
        assertEquals(0, ledger.totalPending())

        // A later genuine cursor correction still applies.
        ledger.record(0, cursor, empty, dirt(2))
        assertEquals(Apply, ledger.decideCursor(0, stone(30)))
        assertEquals(0, ledger.pendingCount(0, cursor))
    }

    @Test
    fun `novel server cursor applies and clears stale predictions`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        ledger.record(0, cursor, empty, shulker("gear"))
        // Something genuinely new that matches neither end of the prediction.
        assertEquals(Apply, ledger.decideCursor(0, dirt(2)))
        assertEquals(0, ledger.pendingCount(0, cursor))
    }

    @Test
    fun `redundant full keeps the newer prediction without consuming it`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        // Grab, then place: two predictions. A no-op click in between makes the server send
        // a full carrying the intermediate cursor, which must neither apply nor consume.
        ledger.record(0, cursor, empty, shulker("gear"))
        ledger.record(0, cursor, shulker("gear"), empty)
        assertEquals(Drop, ledger.decideCursor(0, shulker("gear")))
        assertEquals(1, ledger.pendingCount(0, cursor))
        assertEquals(Drop, ledger.decideCursor(0, shulker("gear")))
        assertEquals(1, ledger.pendingCount(0, cursor))
        assertEquals(Drop, ledger.decideCursor(0, empty))
        assertEquals(0, ledger.pendingCount(0, cursor))
    }

    @Test
    fun `cursor predictions are isolated per screen`() {
        val clock = ManualClock()
        val ledger = ledger(clock)

        ledger.record(5, cursor, empty, shulker("gear"))
        assertEquals(Apply, ledger.decideCursor(0, shulker("gear")))
        assertEquals(Drop, ledger.decideSingle(5, cursor, shulker("gear")))
    }
}
