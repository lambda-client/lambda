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

import com.lambda.PathingTestHarness.BEDROCK_FIELD_HALF_WIDTH
import com.lambda.PathingTestHarness.BEDROCK_FIELD_LENGTH
import com.lambda.PathingTestHarness.BEDROCK_FIELD_SEED
import com.lambda.PathingTestHarness.EXECUTION_TOLERANCE
import com.lambda.PathingTestHarness.assertPathingWalk
import com.lambda.PathingTestHarness.restoreArena
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.util.math.Vec3d

/**
 * End-to-end walk scenarios: the planner's own tapes, walked live by
 * [com.lambda.pathing.PathingManager] over terrain a walk (or fall) can cross
 * without a certified gap launch.
 */
@Suppress("UnstableApiUsage")
internal object WalkPathingTests {
    fun run(
        context: ClientGameTestContext,
        server: TestServerContext,
    ) {
        restoreArena(server)

        // A singleton coarse route used to produce no trajectory windows and then
        // null-cast the absent failure. It must certify a local stopped tape.
        assertPathingWalk(context, server, "pathing-already-there", Stance(0, 100, 0))
        assertPathingWalk(context, server, "pathing-straight", Stance(0, 100, 5))
        assertPathingWalk(context, server, "pathing-diagonal", Stance(5, 100, 5))

        // Submit while the body is still drifting, as a rapid retry does. Capturing that
        // moving state would freeze a frame zero the player sheds before the async plan
        // returns, and the cursor would reject it. The manager must settle to rest first.
        assertPathingWalk(
            context, server, "pathing-drifting-start", Stance(0, 100, 6),
            driftBeforeSubmit = Vec3d(0.18, 0.0, 0.12),
        )

        // One-block rise across the corridor: the seed search must find a launch
        // tick, and the manager must steer the turn through the rotation manager.
        server.runCommand("/fill -2 100 2 2 100 8 minecraft:stone")
        assertPathingWalk(context, server, "pathing-step-up", Stance(0, 101, 6))
        restoreArena(server)

        // Walk-off: the ground drops three blocks past z = 3. Survivable, so the
        // trajectory layer must certify it rather than refuse the whole route.
        server.runCommand("/fill -8 96 -8 8 96 8 minecraft:stone")
        server.runCommand("/fill -8 99 3 8 99 8 minecraft:air")
        assertPathingWalk(context, server, "pathing-walk-off", Stance(0, 97, 6))
        restoreArena(server)

        // Four consecutive three-block drops, one block across each: the staircase that
        // only descends if the body brakes on every lip. Walking off a tread carries
        // enough speed to clear the next one and land on the one after -- or on nothing --
        // so this is the shape that forces the drop control to sneak-pin each edge and
        // step off slowly, one tread at a time.
        //
        // The live counterpart of DropPrimitiveTest's staircase, and worth having as a
        // separate scenario because everything that has gone wrong on this geometry went
        // wrong only against the real client. The unit tests were green through a
        // simulator that read a sneak ledge clip as a wall collision, and green again
        // through one that never shrank the body's box when it crouched; both aborted a
        // real descent within four frames.
        server.runCommand("/fill -8 99 3 8 99 12 minecraft:air")
        server.runCommand("/fill -2 96 3 2 96 3 minecraft:stone")
        server.runCommand("/fill -2 93 4 2 93 4 minecraft:stone")
        server.runCommand("/fill -2 90 5 2 90 5 minecraft:stone")
        server.runCommand("/fill -2 87 6 2 87 12 minecraft:stone")
        // Multi-leg with pipelined hand-offs: replay drift accumulates across legs, so
        // the budget is the executor's own contract, not a single leg's.
        assertPathingWalk(
            context, server, "pathing-descending-drops", Stance(0, 88, 10),
            maxDeviation = EXECUTION_TOLERANCE,
        )
        restoreArena(server)

        // Terrain that is not made of whole cubes: a slab shelf, a run of stairs, and a
        // slab on top of a block. Every one of these used to be a *wall* to the coarse
        // graph -- its only question was whether a cell's top face was a full solid square,
        // which a slab, a stair and a snow layer all answer no to, leaving a cell that is
        // neither standable nor passable. The simulator would have walked all of it without
        // noticing, so the two layers disagreed about what terrain even existed.
        //
        // Live rather than only in JVM tests because the heights are what is at stake: the
        // body walks this at y.5, and every height the planner compares against it -- the
        // goal, the route floor, the stance a frame is attributed to -- had to learn that a
        // stance sits above whatever holds it up rather than at a whole block.
        server.runCommand("/fill -2 100 3 2 100 4 minecraft:stone_slab[type=bottom]")
        server.runCommand("/fill -2 100 5 2 100 6 minecraft:stone_stairs[facing=north,half=bottom]")
        server.runCommand("/fill -2 100 7 2 100 8 minecraft:stone")
        server.runCommand("/fill -2 101 7 2 101 8 minecraft:stone_slab[type=bottom]")
        assertPathingWalk(context, server, "pathing-half-block-ramp", Stance(0, 102, 8))
        restoreArena(server)

        // Slime, which the planner refused to touch at all until its physics were modelled:
        // a landing was assumed to stop the body, so a block that reflects the fall instead
        // was filed as physics the simulator could not reproduce.
        //
        // Walked rather than bounced here on purpose. Crossing slime exercises the stepping
        // drag, which is the half of slime that a tape hits every tick it is in contact --
        // the bounce is one tick, the drag is all the others, and an unmodelled drag diverges
        // the replay immediately. A carpet over the second half checks that the block
        // underfoot is still read through it.
        server.runCommand("/fill -2 99 3 2 99 8 minecraft:slime_block")
        server.runCommand("/fill -2 100 6 2 100 8 minecraft:white_carpet")
        assertPathingWalk(context, server, "pathing-slime-walk", Stance(0, 100, 7))
        restoreArena(server)

        // A slime pit, crossed by falling into it on purpose. Six deep with a landing deck
        // three below the lip and eight blocks out -- past every jump, and a plain fall gives
        // up all six blocks and finishes at the bottom. The only way over is the rebound.
        //
        // Off by default in the planner, so the scenario turns it on: the arcs are thirty
        // ticks long and the terrain that rewards them is rare, which is a bad trade
        // everywhere except exactly here.
        server.runCommand("/fill -6 99 -2 0 99 2 minecraft:stone")
        server.runCommand("/fill 1 99 -3 7 105 3 minecraft:air")
        server.runCommand("/fill 1 93 -3 7 93 3 minecraft:slime_block")
        server.runCommand("/fill 8 96 -2 11 96 2 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-slime-bounce", Stance(9, 97, 0),
            start = "0.5 100 0.5 -90 0",
        )
        restoreArena(server)

        // Longer than the local controller horizon. The coarse search must retain the
        // true final goal while sparse snapshot sections are acquired on demand, and
        // the anytime controller must extend one continuous execution to that goal.
        server.runCommand("/fill -2 99 -8 2 99 70 minecraft:stone")
        server.runCommand("/fill -2 100 -8 2 105 70 minecraft:air")
        // Drift accumulates with tape length: ~3e-8 per frame of double-precision
        // rounding, so a ~250-frame haul lands near 1e-5 where a 12-frame tape stays
        // at 1e-6. The binding contract is the executor's own per-axis tolerance
        // (1e-5), and the cursor accepted every frame of every leg -- this gate simply
        // states that contract rather than a tighter one that only short tapes meet.
        assertPathingWalk(
            context, server, "pathing-long-haul", Stance(0, 100, 64),
            minLegs = 1, maxLegs = 1, minContinuousSegments = 2, requireMovingSplices = true,
            maxDeviation = EXECUTION_TOLERANCE,
            cameraYawDuringPlanning = 90.0f,
        )
        server.runCommand("/fill -2 99 9 2 99 70 minecraft:air")
        restoreArena(server)

        // The final stance starts beyond the client's view distance. D* keeps that real
        // goal and reaches it through one optimistic edge from the streamed frontier,
        // so the published route ends at the frontier and the walk continues from there
        // as the chunks behind it arrive -- one leg per hop of the streamed world,
        // never a false no-route.
        server.runOnServer<IllegalStateException> { minecraftServer ->
            val world = minecraftServer.overworld
            for (z in -8..134) for (x in -2..2) {
                world.setBlockState(
                    net.minecraft.util.math.BlockPos(x, 99, z),
                    net.minecraft.block.Blocks.STONE.defaultState,
                    net.minecraft.block.Block.NOTIFY_ALL,
                )
                for (y in 100..105) world.setBlockState(
                    net.minecraft.util.math.BlockPos(x, y, z),
                    net.minecraft.block.Blocks.AIR.defaultState,
                    net.minecraft.block.Block.NOTIFY_ALL,
                )
            }
        }
        repeat(3) { context.waitTick() }
        assertPathingWalk(
            context, server, "pathing-unloaded-final-goal", Stance(0, 100, 128),
            minLegs = 1, maxLegs = 3, minContinuousSegments = 2,
            requireMovingSplices = true, maxDeviation = EXECUTION_TOLERANCE,
        )
        server.runOnServer<IllegalStateException> { minecraftServer ->
            val world = minecraftServer.overworld
            for (z in 9..134) for (x in -2..2) world.setBlockState(
                net.minecraft.util.math.BlockPos(x, 99, z),
                net.minecraft.block.Blocks.AIR.defaultState,
                net.minecraft.block.Block.NOTIFY_ALL,
            )
        }
        restoreArena(server)

        // The exposed-bedrock field from the retired bench corpus: a fixed-seed
        // reconstruction of vanilla's 80/60/40/20% bedrock layer profile -- irregular
        // pillars, pockets, overhangs and narrow landings. This is the terrain class
        // the whole redesign is measured against ("rough pure natural bedrock
        // surface"); no synthetic fixture reproduces its jump/route quality pressure.
        // Flat launch and arrival islands keep the measurement about the chaotic
        // middle rather than endpoint placement luck.
        server.runOnServer<IllegalStateException> { minecraftServer ->
            val world = minecraftServer.overworld
            for (cell in BedrockFieldLayout.solidCells(
                length = BEDROCK_FIELD_LENGTH,
                halfWidth = BEDROCK_FIELD_HALF_WIDTH,
                seed = BEDROCK_FIELD_SEED,
            )) {
                world.setBlockState(
                    net.minecraft.util.math.BlockPos(cell.x, cell.y, cell.z),
                    net.minecraft.block.Blocks.BEDROCK.defaultState,
                    net.minecraft.block.Block.NOTIFY_ALL,
                )
            }
        }
        repeat(10) { context.waitTick() }
        assertPathingWalk(
            context, server, "pathing-bedrock-field", Stance(BEDROCK_FIELD_LENGTH - 2, 63, 0),
            maxLegs = 1,
            maxDeviation = EXECUTION_TOLERANCE,
            start = "1.5 63 0.5 -90 0",
            maxPathingTicks = 2400,
        )
        server.runCommand(
            "/fill 0 62 -$BEDROCK_FIELD_HALF_WIDTH ${BEDROCK_FIELD_LENGTH - 1} 67 $BEDROCK_FIELD_HALF_WIDTH minecraft:air",
        )
        restoreArena(server)

        // The same terrain class at three times the length: 120 blocks puts the goal far
        // beyond the client's view distance, so the coarse field must route to the
        // streamed frontier over *jagged* ground, stream the middle while walking, and
        // keep the journey's graph across every leg. The 40-block field above fits
        // inside one streamed neighbourhood and could never catch the long-search
        // regressions ("no coarse path on big bedrock surface") that this length does.
        server.runOnServer<IllegalStateException> { minecraftServer ->
            val world = minecraftServer.overworld
            for (cell in BedrockFieldLayout.solidCells(
                length = LONG_BEDROCK_FIELD_LENGTH,
                halfWidth = BEDROCK_FIELD_HALF_WIDTH,
                seed = BEDROCK_FIELD_SEED,
            )) {
                world.setBlockState(
                    net.minecraft.util.math.BlockPos(cell.x, cell.y, cell.z),
                    net.minecraft.block.Blocks.BEDROCK.defaultState,
                    net.minecraft.block.Block.NOTIFY_ALL,
                )
            }
        }
        repeat(10) { context.waitTick() }
        assertPathingWalk(
            context, server, "pathing-bedrock-field-long",
            Stance(LONG_BEDROCK_FIELD_LENGTH - 2, 63, 0),
            maxDeviation = EXECUTION_TOLERANCE,
            start = "1.5 63 0.5 -90 0",
            maxPathingTicks = 7200,
        )
        server.runCommand(
            "/fill 0 62 -$BEDROCK_FIELD_HALF_WIDTH ${LONG_BEDROCK_FIELD_LENGTH - 1} 67 $BEDROCK_FIELD_HALF_WIDTH minecraft:air",
        )
        restoreArena(server)

        assertTeleportReplan(context, server)
    }

    /**
     * The retained-journey teleport failure of 2026-08-24, end to end: a journey whose
     * horizon ring granted the goal's chunks is re-planned after the player teleports
     * far outside streamed range. The goal's terrain being "known" must not disable the
     * optimistic frontier over the unstreamed middle -- live, this converged with the
     * start at infinity and refused a route along a perfectly walkable runway.
     *
     * Hand-rolled rather than via [assertPathingWalk] because the harness clears
     * [PathingManager] before each walk, and the retained journey IS the scenario.
     */
    private fun assertTeleportReplan(
        context: ClientGameTestContext,
        server: TestServerContext,
    ) {
        val scenario = "pathing-teleport-replan"
        if (!PathingTestHarness.scenarioSelected(scenario)) return
        try {
            server.runOnServer<IllegalStateException> { minecraftServer ->
                val world = minecraftServer.overworld
                for (z in -8..200) for (x in -2..2) {
                    world.setBlockState(
                        net.minecraft.util.math.BlockPos(x, 99, z),
                        net.minecraft.block.Blocks.STONE.defaultState,
                        net.minecraft.block.Block.NOTIFY_ALL,
                    )
                    for (y in 100..105) world.setBlockState(
                        net.minecraft.util.math.BlockPos(x, y, z),
                        net.minecraft.block.Blocks.AIR.defaultState,
                        net.minecraft.block.Block.NOTIFY_ALL,
                    )
                }
            }
            repeat(3) { context.waitTick() }
            server.runCommand("/tp Steve 0.5 100 0.5 0 0")
            repeat(10) { context.waitTick() }

            val goal = com.lambda.pathing.coarse.Stance(0, 100, 5)
            context.runOnClient<IllegalStateException> {
                com.lambda.pathing.PathingManager.clear()
                com.lambda.pathing.PathingRequest(
                    com.lambda.config.automation.AutomationConfig.DEFAULT, goal,
                ).submit()
            }
            awaitWalkTerminal(context, scenario, "prime walk", PathingTestHarness.MAX_PATHING_TICKS)

            // Out of view distance; the origin chunks unload on the client, but the
            // journey toward the goal -- with the goal's chunks granted -- is retained.
            server.runCommand("/tp Steve 0.5 100 192.5 180 0")
            repeat(40) { context.waitTick() }
            context.runOnClient<IllegalStateException> {
                com.lambda.pathing.PathingRequest(
                    com.lambda.config.automation.AutomationConfig.DEFAULT, goal,
                ).submit()
            }
            // The manager still reports the prime walk's Complete until the new request
            // is picked up on a later tick -- wait for the state to actually turn over,
            // or the terminal wait below returns instantly against the stale status.
            var activation = 0
            while (activation++ < 100 &&
                com.lambda.pathing.PathingManager.status is com.lambda.pathing.PathingManager.Status.Complete
            ) {
                context.waitTick()
            }
            check(com.lambda.pathing.PathingManager.status !is com.lambda.pathing.PathingManager.Status.Complete) {
                "$scenario: replan request was never picked up"
            }
            awaitWalkTerminal(context, scenario, "replan walk", maxTicks = 6000)
        } catch (failure: IllegalStateException) {
            PathingTestHarness.pathingFailures += failure.message ?: "$scenario: ${failure::class.simpleName}"
            println("[pathing-fail] ${failure.message}")
        } finally {
            context.runOnClient<IllegalStateException> { com.lambda.pathing.PathingManager.clear() }
            server.runOnServer<IllegalStateException> { minecraftServer ->
                val world = minecraftServer.overworld
                for (z in 9..200) for (x in -2..2) world.setBlockState(
                    net.minecraft.util.math.BlockPos(x, 99, z),
                    net.minecraft.block.Blocks.AIR.defaultState,
                    net.minecraft.block.Block.NOTIFY_ALL,
                )
            }
            restoreArena(server)
        }
    }

    private fun awaitWalkTerminal(
        context: ClientGameTestContext,
        scenario: String,
        phase: String,
        maxTicks: Int,
    ) {
        var ticks = 0
        while (ticks++ < maxTicks &&
            com.lambda.pathing.PathingManager.status !is com.lambda.pathing.PathingManager.Status.Complete &&
            com.lambda.pathing.PathingManager.status !is com.lambda.pathing.PathingManager.Status.Failed
        ) {
            context.waitTick()
        }
        check(com.lambda.pathing.PathingManager.status is com.lambda.pathing.PathingManager.Status.Complete) {
            "$scenario: $phase did not complete (${com.lambda.pathing.PathingManager.status})"
        }
    }

    private const val LONG_BEDROCK_FIELD_LENGTH = 120
}
