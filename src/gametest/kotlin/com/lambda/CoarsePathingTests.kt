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
import com.lambda.pathing.api.PathingService
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.PathingRequest
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

        assertPathingStartsAfloat(context, server)
        restoreArena(server)

        assertPathingRecoversAfloat(context, server)
        restoreArena(server)

        assertBreathesInSidePocket(context, server)
        restoreArena(server)
    }

    /**
     * A flooded passage longer than a breath whose only air midway lies eight cells up a side tunnel:
     * the route must break the dive with a breathing stop in that pocket. Survival only for this
     * scenario, since a creative body cannot drown and plans no stops.
     */
    private fun assertBreathesInSidePocket(
        context: ClientGameTestContext,
        server: TestServerContext,
    ) {
        val scenario = "pathing-breathing-stop"
        if (!scenarioSelected(scenario)) return
        val length = 54
        val side = 8
        val z0 = 12
        val pocketZ = z0 + length / 2
        try {
            server.runCommand("/fill -4 88 ${z0 - 6} ${side + 4} 96 ${z0 + length + 6} minecraft:stone")
            server.runCommand("/fill -2 93 ${z0 - 6} 2 96 ${z0 - 1} minecraft:air")
            server.runCommand("/fill -2 93 ${z0 + length} 2 96 ${z0 + length + 6} minecraft:air")
            for (pool in listOf(z0, z0 + length - 2)) {
                server.runCommand("/fill -1 90 $pool 1 92 ${pool + 1} minecraft:water")
                server.runCommand("/fill -1 93 $pool 1 96 ${pool + 1} minecraft:air")
            }
            server.runCommand("/fill 0 90 ${z0 + 2} 0 91 ${z0 + length - 3} minecraft:water")
            server.runCommand("/fill 1 90 $pocketZ $side 91 $pocketZ minecraft:water")
            server.runCommand("/fill $side 92 $pocketZ $side 92 $pocketZ minecraft:air")
            server.runCommand("/gamemode survival Steve")
            server.runCommand("/tp Steve 0.5 93 ${z0 - 3.5} 0 0")
            repeat(10) { context.waitTick() }
            context.runOnClient<IllegalStateException> {
                PathingService.clear()
                PathingRequest(AutomationConfig.DEFAULT, Stance(0, 93, z0 + length + 3)).submit()
            }
            var ticks = 0
            var deepest = 0.0
            while (ticks++ < MAX_PATHING_TICKS * 2 &&
                PathingService.status !is State.Complete &&
                PathingService.status !is State.Failed
            ) {
                context.waitTick()
                context.runOnClient<IllegalStateException> {
                    val player = com.lambda.Lambda.mc.player
                    if (player != null) deepest = maxOf(deepest, player.x)
                }
            }
            check(PathingService.status is State.Complete) {
                "$scenario: did not cross the passage (${PathingService.status})"
            }
            check(deepest > side - 0.5) { "$scenario: never breathed in the side pocket (reached x=$deepest)" }
        } catch (failure: IllegalStateException) {
            pathingFailures += failure.message ?: "$scenario: ${failure::class.simpleName}"
            println("[pathing-fail] ${failure.message}")
        } finally {
            context.runOnClient<IllegalStateException> { PathingService.clear() }
            server.runCommand("/gamemode creative Steve")
            server.runCommand("/fill -4 88 ${z0 - 6} ${side + 4} 96 ${z0 + length + 6} minecraft:air")
        }
    }

    /** A tape invalidated mid-swim must restart from the water instead of waiting to stand on ground. */
    private fun assertPathingRecoversAfloat(
        context: ClientGameTestContext,
        server: TestServerContext,
    ) {
        val scenario = "pathing-recover-afloat"
        if (!scenarioSelected(scenario)) return
        try {
            server.runCommand("/fill -8 95 3 8 99 20 minecraft:stone")
            server.runCommand("/fill -8 100 3 -8 101 20 minecraft:stone")
            server.runCommand("/fill 8 100 3 8 101 20 minecraft:stone")
            server.runCommand("/fill -7 96 4 7 99 19 minecraft:water")
            server.runCommand("/fill -8 99 21 8 99 24 minecraft:stone")
            server.runCommand("/tp Steve 0.5 100 0.5 0 0")
            repeat(10) { context.waitTick() }
            context.runOnClient<IllegalStateException> {
                PathingService.clear()
                PathingRequest(AutomationConfig.DEFAULT, Stance(0, 100, 23)).submit()
            }
            var ticks = 0
            var swimmingAt: net.minecraft.util.math.BlockPos? = null
            while (ticks++ < MAX_PATHING_TICKS && swimmingAt == null) {
                context.waitTick()
                context.runOnClient<IllegalStateException> {
                    val player = com.lambda.Lambda.mc.player
                    if (player != null && player.isTouchingWater && player.z > 8.0 && PathingService.status is State.Executing) swimmingAt = player.blockPos
                }
            }
            val at = checkNotNull(swimmingAt) { "$scenario: never swam mid-pool (${PathingService.status})" }
            server.runCommand("/fill ${at.x - 1} 96 ${at.z + 2} ${at.x + 1} 101 ${at.z + 2} minecraft:stone")
            ticks = 0
            while (ticks++ < 40 && PathingService.telemetry.recoveries == 0) {
                context.waitTick()
            }
            check(PathingService.telemetry.recoveries >= 1) {
                "$scenario: the pillar in the swim did not invalidate the tape (${PathingService.status})"
            }
            ticks = 0
            while (ticks++ < MAX_PATHING_TICKS &&
                PathingService.status !is State.Complete &&
                PathingService.status !is State.Failed
            ) {
                context.waitTick()
            }
            check(PathingService.status is State.Complete) {
                "$scenario: did not recover in the water (${PathingService.status})"
            }
        } catch (failure: IllegalStateException) {
            pathingFailures += failure.message ?: "$scenario: ${failure::class.simpleName}"
            println("[pathing-fail] ${failure.message}")
        } finally {
            context.runOnClient<IllegalStateException> { PathingService.clear() }
        }
    }

    /** A walk requested while floating mid-pool must start treading on a certified tape, not wait to stand. */
    private fun assertPathingStartsAfloat(
        context: ClientGameTestContext,
        server: TestServerContext,
    ) {
        val scenario = "pathing-start-afloat"
        if (!scenarioSelected(scenario)) return
        try {
            server.runCommand("/fill -8 95 3 8 99 20 minecraft:stone")
            server.runCommand("/fill -8 100 3 -8 101 20 minecraft:stone")
            server.runCommand("/fill 8 100 3 8 101 20 minecraft:stone")
            server.runCommand("/fill -7 96 4 7 99 19 minecraft:water")
            server.runCommand("/fill -8 99 21 8 99 24 minecraft:stone")
            server.runCommand("/tp Steve 0.5 99.4 10.5 0 0")
            repeat(10) { context.waitTick() }
            context.runOnClient<IllegalStateException> {
                val player = checkNotNull(com.lambda.Lambda.mc.player) { "$scenario: missing client player" }
                check(player.isTouchingWater && !player.isOnGround) { "$scenario: the player is not afloat" }
                PathingService.clear()
                PathingRequest(AutomationConfig.DEFAULT, Stance(0, 100, 23)).submit()
            }
            var ticks = 0
            while (ticks++ < MAX_PATHING_TICKS &&
                PathingService.status !is State.Complete &&
                PathingService.status !is State.Failed
            ) {
                context.waitTick()
            }
            check(PathingService.status is State.Complete) {
                "$scenario: did not reach the far bank from the water (${PathingService.status})"
            }
        } catch (failure: IllegalStateException) {
            pathingFailures += failure.message ?: "$scenario: ${failure::class.simpleName}"
            println("[pathing-fail] ${failure.message}")
        } finally {
            context.runOnClient<IllegalStateException> { PathingService.clear() }
        }
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
                PathingService.clear()
                PathingRequest(AutomationConfig.DEFAULT, Stance(0, 100, 7)).submit()
            }

            var ticks = 0
            while (ticks++ < MAX_PATHING_TICKS && PathingService.status !is State.Executing) {
                context.waitTick()
            }
            check(PathingService.status is State.Executing) {
                "$scenario: never started execution (${PathingService.status})"
            }

            // A wall across the certified route changes how its remaining frames replay.
            // The client update must invalidate the tape before replay continues.
            server.runCommand("/fill -8 100 4 8 101 4 minecraft:stone")
            ticks = 0
            while (ticks++ < 40 && PathingService.telemetry.recoveries == 0) {
                context.waitTick()
            }
            check(PathingService.telemetry.recoveries == 1) {
                "$scenario: certified generation was not invalidated (${PathingService.status})"
            }
            // Restore a walkable route. The second client update may restart an in-flight
            // multi-tick capture, but it must not resurrect the invalidated worker.
            server.runCommand("/fill -8 100 4 8 101 4 minecraft:air")
            ticks = 0
            while (ticks++ < MAX_PATHING_TICKS &&
                PathingService.status !is State.Complete &&
                PathingService.status !is State.Failed
            ) {
                context.waitTick()
            }
            check(PathingService.status is State.Complete) {
                "$scenario: did not recover after invalidation (${PathingService.status})"
            }
        } catch (failure: IllegalStateException) {
            pathingFailures += failure.message ?: "$scenario: ${failure::class.simpleName}"
            println("[pathing-fail] ${failure.message}")
        } finally {
            server.runCommand("/fill -8 100 4 8 101 4 minecraft:air")
            context.runOnClient<IllegalStateException> { PathingService.clear() }
        }
    }
}
