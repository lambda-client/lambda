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

import com.lambda.interaction.manager.managers.inventory.InventorySyncFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * End-to-end tests for [InventorySyncFilter]: snapshot + ledger wiring, not just the ledger rules.
 *
 * The headline scenario is the nested-shulker failure: pick up a shulker box, move it into the
 * ender chest on another screen, then pick up an *identical* shulker box into the *same* slot.
 * The second pickup is new information from the server and must be applied. Previously the
 * applied first pickup was re-recorded as a phantom prediction on the next diff (the snapshot was
 * never refreshed), and that phantom matched the identical second shulker, so the update was
 * dropped and the shulker never appeared client-side.
 *
 * Stacks are plain data (no Minecraft classes) so these run as fast JVM unit tests.
 */
class InventorySyncFilterTest {
    private data class TestStack(val item: String, val count: Int)

    private val empty = TestStack("air", 0)
    private fun shulker(contents: String) = TestStack("shulker[$contents]", 1)

    private class ManualClock(var now: Instant = Instant.fromEpochMilliseconds(0))

    private fun filter(clock: ManualClock, maxAgeMs: Long = 60_000L) =
        InventorySyncFilter<TestStack>({ a, b -> a == b }, { it.copy() }, maxAgeMs = maxAgeMs, clock = { clock.now })

    /** Player-screen slots: everything empty except [slot]. */
    private fun playerSlots(slot: Int, stack: TestStack, size: Int = 46): Map<Int, TestStack> =
        (0 until size).associateWith { empty } + (slot to stack)

    @Test
    fun `identical shulker picked into the same slot applies`() {
        val clock = ManualClock()
        val filter = filter(clock)
        val slot = 36
        val first = shulker("ender_chest")
        val second = shulker("ender_chest")
        assertEquals(first, second, "test setup: the two shulkers must be identical")

        // Baseline: the player screen as the client sees it.
        filter.observeLocal(0, playerSlots(slot, empty), empty)

        // The first shulker is picked up from the ground. Nothing predicted it, so it applies.
        assertTrue(filter.onSingle(0, slot, first, empty))

        // A tick passes with no local change. The applied pickup must NOT be re-recorded as
        // something we did: there must be no phantom prediction left behind.
        filter.observeLocal(0, playerSlots(slot, first), empty)
        assertEquals(0, filter.totalPending(), "applied server values must refresh the snapshot")

        // The first shulker moves into the ender chest on its screen (container-local ids,
        // so these recordings live under that screen only).
        filter.snapshot(5, mapOf(2 to empty, 40 to first), empty)
        filter.observeLocal(5, mapOf(2 to first, 40 to empty), empty)
        assertEquals(0, filter.pendingCount(0, slot))

        // The second, identical shulker is picked up into the same slot. This is new
        // information and must be applied, not mistaken for an echo of the first pickup.
        assertTrue(
            filter.onSingle(0, slot, second, empty),
            "identical shulker into the same slot must be applied"
        )
        assertEquals(0, filter.pendingCount(0, slot))
    }

    @Test
    fun `identical shulker picked into a different slot applies`() {
        val clock = ManualClock()
        val filter = filter(clock)

        filter.observeLocal(0, playerSlots(36, empty), empty)
        assertTrue(filter.onSingle(0, 36, shulker("ender_chest"), empty))
        filter.observeLocal(0, playerSlots(36, shulker("ender_chest")), empty)

        assertTrue(filter.onSingle(0, 37, shulker("ender_chest"), empty))
    }

    @Test
    fun `local predictions still filter their echoes`() {
        val clock = ManualClock()
        val filter = filter(clock)
        val slot = 36

        filter.observeLocal(0, playerSlots(slot, TestStack("stone", 64)), empty)

        // The client predicts placing a block; the late echo must be dropped.
        filter.observeLocal(0, playerSlots(slot, TestStack("stone", 63)), empty)
        assertEquals(1, filter.pendingCount(0, slot))
        assertEquals(false, filter.onSingle(0, slot, TestStack("stone", 63), TestStack("stone", 63)))
        assertEquals(0, filter.pendingCount(0, slot))

        // And a correction straight afterwards still applies.
        assertTrue(filter.onSingle(0, slot, TestStack("stone", 64), TestStack("stone", 63)))
    }

    @Test
    fun `snapshot overwrites and per-screen state is isolated`() {
        val clock = ManualClock()
        val filter = filter(clock)

        filter.observeLocal(0, playerSlots(36, TestStack("stone", 64)), empty)
        filter.observeLocal(0, playerSlots(36, TestStack("stone", 63)), empty)
        assertEquals(1, filter.pendingCount(0, 36))

        // Re-snapshotting (screen reopened) does not clear predictions, but a different screen
        // never sees them.
        filter.snapshot(5, mapOf(0 to TestStack("stone", 1)), empty)
        assertEquals(1, filter.pendingCount(0, 36))
        assertTrue(filter.onSingle(5, 0, TestStack("stone", 1), TestStack("stone", 1)))

        filter.clear(0)
        assertEquals(0, filter.totalPending())
    }
}
