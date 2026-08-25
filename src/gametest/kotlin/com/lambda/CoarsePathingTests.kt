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

package com.lambda

import com.lambda.PathingTestHarness.MAX_PATHING_TICKS
import com.lambda.PathingTestHarness.assertPathingWalk
import com.lambda.PathingTestHarness.pathingFailures
import com.lambda.PathingTestHarness.restoreArena
import com.lambda.PathingTestHarness.scenarioSelected
import com.lambda.config.automation.AutomationConfig
import com.lambda.pathing.PathingManager
import com.lambda.pathing.PathingRequest
import com.lambda.pathing.core.Stance
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext

/**
 * Coarse-routing scenarios: single gap-jump topology shapes -- the templates the
 * coarse graph must propose before certification ever sees them -- plus the
 * route-invalidation contract when the world mutates under a certified tape.
 */
@Suppress("UnstableApiUsage")
internal object CoarsePathingTests {
    fun run(
        context: ClientGameTestContext,
        server: TestServerContext,
    ) {
        restoreArena(server)

        // A two-wide hole. The nominal walk falls in; only a launch discovered by
        // backtracking over that failure gets across. Nothing here is scheduled --
        // the coarse layer proposes a candidate, simulation certifies the jump.
        server.runCommand("/fill -8 99 3 8 99 4 minecraft:air")
        assertPathingWalk(
            context, server, "pathing-gap-jump-flat-2", Stance(0, 100, 7),
            expectedJumpDy = 0, requireJumpInput = true,
        )
        restoreArena(server)

        // One missing support cell with the landing one block higher. This is a
        // rising gap jump, not an adjacent step-up: it needs a span-2 candidate.
        server.runCommand("/fill -8 99 3 8 99 3 minecraft:air")
        server.runCommand("/fill -8 100 4 8 100 8 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-gap-jump-rise-1", Stance(0, 101, 6),
            expectedJumpDy = 1, requireJumpInput = true,
        )
        restoreArena(server)

        // Regression for the live report that a two-stance connection landing one
        // block down was absent from topology. A sprint may coast over this small
        // gap without pressing jump; the key contract is that the descending edge
        // exists and its complete tape is certified.
        server.runCommand("/fill -8 99 3 8 99 8 minecraft:air")
        server.runCommand("/fill -8 98 4 8 98 8 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-gap-drop-span-2", Stance(0, 99, 6),
            expectedJumpDy = -1,
        )
        restoreArena(server)

        // The same lower landing across a genuinely two-wide hole. This one must
        // launch, and guards the descending variant of the span-3 template.
        server.runCommand("/fill -8 99 3 8 99 8 minecraft:air")
        server.runCommand("/fill -8 98 5 8 98 8 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-gap-drop-span-3", Stance(0, 99, 7),
            expectedJumpDy = -1, requireJumpInput = true,
        )
        restoreArena(server)

        // A three-wide hole exercises the longest flat jump currently covered by
        // the live corpus. The mask proposes span 4; only vanilla replay proves it.
        server.runCommand("/fill -8 99 3 8 99 5 minecraft:air")
        assertPathingWalk(
            context, server, "pathing-gap-jump-flat-3", Stance(0, 100, 8),
            expectedJumpDy = 0, requireJumpInput = true,
        )
        restoreArena(server)

        assertPathingInvalidatesOnWorldMutation(context, server)
        restoreArena(server)
    }

    /** A changed dependency must stop the old tape, then continue in a fresh generation. */
    private fun assertPathingInvalidatesOnWorldMutation(
        context: ClientGameTestContext,
        server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext,
    ) {
        val scenario = "pathing-world-invalidation"
        if (!scenarioSelected(scenario)) return
        try {
            server.runCommand("/tp Steve 0.5 100 0.5 0 0")
            repeat(5) { context.waitTick() }
            context.runOnClient<IllegalStateException> {
                PathingManager.clear()
                PathingRequest(AutomationConfig.DEFAULT, Stance(0, 100, 7)).submit()
            }

            var ticks = 0
            while (ticks++ < MAX_PATHING_TICKS && PathingManager.status !is PathingManager.Status.Executing) {
                context.waitTick()
            }
            check(PathingManager.status is PathingManager.Status.Executing) {
                "$scenario: never started execution (${PathingManager.status})"
            }

            // This support block is in the certified route's dependency section.
            // The client update must invalidate the snapshot before replay continues.
            server.runCommand("/setblock 0 99 4 minecraft:air")
            ticks = 0
            while (ticks++ < 40 && PathingManager.recoveries == 0) {
                context.waitTick()
            }
            check(PathingManager.recoveries == 1) {
                "$scenario: certified generation was not invalidated (${PathingManager.status})"
            }
            // Restore a walkable route. The second client update may restart an in-flight
            // multi-tick capture, but it must not resurrect the invalidated worker.
            server.runCommand("/setblock 0 99 4 minecraft:stone")
            ticks = 0
            while (ticks++ < MAX_PATHING_TICKS &&
                PathingManager.status !is PathingManager.Status.Complete &&
                PathingManager.status !is PathingManager.Status.Failed
            ) {
                context.waitTick()
            }
            check(PathingManager.status is PathingManager.Status.Complete) {
                "$scenario: did not recover after invalidation (${PathingManager.status})"
            }
        } catch (failure: IllegalStateException) {
            pathingFailures += failure.message ?: "$scenario: ${failure::class.simpleName}"
            println("[pathing-fail] ${failure.message}")
        } finally {
            server.runCommand("/setblock 0 99 4 minecraft:stone")
            context.runOnClient<IllegalStateException> { PathingManager.clear() }
        }
    }
}
