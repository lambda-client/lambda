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

import com.lambda.pathing.api.PathingService
import com.lambda.pathing.session.PathingSession.State
import com.lambda.PathingTestHarness.BEDROCK_FIELD_HALF_WIDTH
import com.lambda.PathingTestHarness.BEDROCK_FIELD_LENGTH
import com.lambda.PathingTestHarness.BEDROCK_FIELD_SEED
import com.lambda.PathingTestHarness.EXECUTION_TOLERANCE
import com.lambda.PathingTestHarness.assertPathingWalk
import com.lambda.PathingTestHarness.pathingFailures
import com.lambda.PathingTestHarness.restoreArena
import com.lambda.PathingTestHarness.scenarioSelected
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.PathingConfig
import com.lambda.config.blocks.RotationConfig
import com.lambda.interaction.managers.rotating.MouseLook
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.TrajectoryPlan
import pathing.BedrockFieldLayout
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * End-to-end walk scenarios: the planner's own tapes, walked live by
 * [com.lambda.pathing.session.PathingManager] over terrain a walk (or fall) can cross
 * without a certified gap launch.
 */
@Suppress("UnstableApiUsage")
internal object WalkPathingTests {
    private val HUMAN_HAND = object : RotationConfig by AutomationConfig.DEFAULT.rotationConfig {
        override val turnSpeed = 20.0
        override val turnAcceleration = 6.0
        override val turnNoise = 3.0
    }

    private fun checkHumanTurns(context: ClientGameTestContext, plan: TrajectoryPlan?) {
        if (!scenarioSelected("pathing-human-turns")) return
        val failure = context.computeOnClient<String, IllegalStateException> { client ->
            if (plan == null) return@computeOnClient "nothing published"
            val look = MouseLook(MouseLook.factor(client.options.mouseSensitivity.value))
            val rotations = listOf(plan.initialState.rotation) + plan.frames.map { it.state.rotation }
            val steps = rotations.zipWithNext { a, b -> Rotation(Rotation.wrap(b.yaw - a.yaw), b.pitch - a.pitch) }
            val slack = look.degreesPerCount * 1.5
            val fastest = steps.maxOf { Math.hypot(it.yaw, it.pitch) }
            val jerk = steps.zipWithNext { a, b -> Math.hypot(b.yaw - a.yaw, b.pitch - a.pitch) }.maxOrNull() ?: 0.0
            val offLattice = rotations.zipWithNext().count { (a, b) -> look.turn(a, look.countsX(Rotation.wrap(b.yaw - a.yaw)), look.countsY(b.pitch - a.pitch)) != b }
            println("[pathing-diag] pathing-human-turns: fastest %.2f deg/tick, largest speed change %.2f, off-lattice steps %d".format(fastest, jerk, offLattice))
            when {
                offLattice > 0 -> "$offLattice steps were not whole mouse counts"
                fastest > HUMAN_HAND.turnSpeed + 5 * HUMAN_HAND.turnNoise + slack -> "turned %.2f deg in one tick".format(fastest)
                jerk > HUMAN_HAND.turnAcceleration + slack -> "changed turn speed by %.2f deg in one tick".format(jerk)
                else -> ""
            }
        }
        if (failure.isNotEmpty()) {
            pathingFailures += "pathing-human-turns: $failure"
            println("[pathing-fail] pathing-human-turns: $failure")
        }
    }

    private class ToggleWatch {
        var uses = 0
        var closes = 0
        var recoveries = 0
    }

    private fun watchToggles(context: ClientGameTestContext, watch: ToggleWatch): (Int) -> Unit = {
        context.runOnClient<IllegalStateException> {
            PathingService.telemetry.published?.plan?.let { plan ->
                val uses = plan.tape.asList().mapNotNull { it.use }
                watch.uses = maxOf(watch.uses, uses.size)
                watch.closes = maxOf(watch.closes, uses.count { !it.open })
            }
            watch.recoveries = PathingService.telemetry.recoveries
        }
    }

    private fun usingDoors(close: Boolean = false): (PathingConfig) -> PathingConfig = { base ->
        object : PathingConfig by base {
            override val openDoors = true
            override val closeDoors = close
            override val hazards = base.hazards.copy(openDoors = true)
        }
    }

    private fun checkToggles(context: ClientGameTestContext, scenario: String, watch: ToggleWatch, uses: IntRange, closes: IntRange, expected: Map<BlockPos, Boolean>) {
        if (!scenarioSelected(scenario)) return
        val states = context.computeOnClient<Map<BlockPos, Boolean>, IllegalStateException> { client ->
            expected.keys.associateWith { client.world?.getBlockState(it)?.getOrEmpty(Properties.OPEN)?.orElse(false) == true }
        }
        println("[pathing-diag] $scenario: uses ${watch.uses}, closes ${watch.closes}, recoveries ${watch.recoveries}, open $states")
        val failure = when {
            watch.uses !in uses -> "the tape used blocks ${watch.uses} times, expected $uses"
            watch.closes !in closes -> "the tape closed blocks ${watch.closes} times, expected $closes"
            watch.recoveries > 0 -> "recovered ${watch.recoveries} times"
            states != expected -> "ended with $states, expected $expected"
            else -> return
        }
        pathingFailures += "$scenario: $failure"
        println("[pathing-fail] $scenario: $failure")
    }

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
        var humanPlan: TrajectoryPlan? = null
        assertPathingWalk(context, server, "pathing-human-turns", Stance(-5, 100, 2), rotation = HUMAN_HAND, onTick = {
            context.runOnClient<IllegalStateException> { PathingService.telemetry.published?.plan?.let { humanPlan = it } }
        })
        checkHumanTurns(context, humanPlan)

        // A closed oak door in a wall across the straight line, and a closed fence gate in a fence
        // behind it. With doors allowed the coarse route goes through both, and the tape clicks
        // them open on the run. It starts looking at the floor, so it has to raise its aim to
        // the door and lower it to the gate, and the server must see exactly that look while the
        // camera looks elsewhere.
        server.runCommand("/fill -8 100 3 8 102 3 minecraft:stone")
        server.runCommand("/setblock 0 100 3 minecraft:oak_door[facing=north,half=lower]")
        server.runCommand("/setblock 0 101 3 minecraft:oak_door[facing=north,half=upper]")
        server.runCommand("/fill -8 100 5 8 100 5 minecraft:oak_fence")
        server.runCommand("/setblock 0 100 5 minecraft:oak_fence_gate[facing=north]")
        val doorWatch = ToggleWatch()
        assertPathingWalk(
            context, server, "pathing-door", Stance(0, 100, 7),
            start = "0.5 100 0.5 0 35",
            cameraYawDuringPlanning = 90.0f,
            pathing = usingDoors(),
            onTick = watchToggles(context, doorWatch),
        )
        checkToggles(context, "pathing-door", doorWatch, 2..2, 0..0, mapOf(BlockPos(0, 100, 3) to true, BlockPos(0, 100, 5) to true))
        restoreArena(server)

        // The same door with Close Doors on: opened on the way in, shut again once the body is past.
        server.runCommand("/fill -8 100 3 8 102 3 minecraft:stone")
        server.runCommand("/setblock 0 100 3 minecraft:oak_door[facing=north,half=lower]")
        server.runCommand("/setblock 0 101 3 minecraft:oak_door[facing=north,half=upper]")
        val closeWatch = ToggleWatch()
        assertPathingWalk(
            context, server, "pathing-door-close", Stance(0, 100, 8),
            pathing = usingDoors(close = true),
            onTick = watchToggles(context, closeWatch),
        )
        checkToggles(context, "pathing-door-close", closeWatch, 2..2, 1..1, mapOf(BlockPos(0, 100, 3) to false))
        restoreArena(server)

        // Five open trapdoors hang in the floor of a one-wide corridor over a deep pit. Closed they are
        // floor, so the tape closes them while running up and crosses on them.
        server.runCommand("/fill -1 100 -2 -1 101 10 minecraft:stone")
        server.runCommand("/fill 1 100 -2 1 101 10 minecraft:stone")
        server.runCommand("/fill 0 99 2 0 99 6 minecraft:oak_trapdoor[half=top,open=true,facing=north]")
        val trapdoorWatch = ToggleWatch()
        assertPathingWalk(
            context, server, "pathing-trapdoor", Stance(0, 100, 8),
            pathing = usingDoors(),
            onTick = watchToggles(context, trapdoorWatch),
        )
        checkToggles(context, "pathing-trapdoor", trapdoorWatch, 1..5, 1..5, emptyMap())
        restoreArena(server)

        // A door and a gate back to back, then a closed trapdoor opened as a step onto a ledge. From
        // the ledge both are out of reach, so Close Doors shuts them before the climb even though the
        // client already reports them open by then.
        server.runCommand("/fill 0 99 -2 0 99 16 minecraft:stone")
        server.runCommand("/fill -1 100 -2 -1 103 16 minecraft:stone")
        server.runCommand("/fill 1 100 -2 1 103 16 minecraft:stone")
        server.runCommand("/setblock 0 100 4 minecraft:oak_door[facing=north,half=lower]")
        server.runCommand("/setblock 0 101 4 minecraft:oak_door[facing=north,half=upper]")
        server.runCommand("/setblock 0 100 5 minecraft:oak_fence_gate[facing=north]")
        server.runCommand("/setblock 0 100 8 minecraft:oak_trapdoor[half=bottom,open=false,facing=north]")
        server.runCommand("/fill 0 100 9 0 101 9 minecraft:stone")
        server.runCommand("/fill 0 101 10 0 101 14 minecraft:stone")
        val courseWatch = ToggleWatch()
        assertPathingWalk(
            context, server, "pathing-door-course", Stance(0, 102, 13),
            pathing = usingDoors(close = true),
            onTick = watchToggles(context, courseWatch),
        )
        checkToggles(
            context, "pathing-door-course", courseWatch, 6..12, 3..6,
            mapOf(BlockPos(0, 100, 4) to false, BlockPos(0, 100, 5) to false, BlockPos(0, 100, 8) to false),
        )
        restoreArena(server)

        // A cow standing on the straight line. Walking through it gets the body pushed off
        // its tape, so the cells a mob stands in are closed and the walk goes around it.
        server.runCommand("/summon minecraft:cow 0.5 100 4.5 {NoAI:1b,Invulnerable:1b}")
        assertPathingWalk(context, server, "pathing-mob-in-the-way", Stance(0, 100, 8))
        server.runCommand("/kill @e[type=minecraft:cow]")

        // A cow standing in the only one-wide gap through a wall: no trajectory avoids it, so
        // the session plans through it instead of failing the walk, absorbing the pushes until
        // the body squeezes past or the cow is gone.
        server.runCommand("/fill -8 100 2 -1 101 6 minecraft:stone")
        server.runCommand("/fill 1 100 2 8 101 6 minecraft:stone")
        server.runCommand("/summon minecraft:cow 0.5 100 4.5 {NoAI:1b,Invulnerable:1b}")
        assertPathingWalk(
            context, server, "pathing-mob-blocks-gap", Stance(0, 100, 8),
            onTick = { tick -> if (tick == MOB_LEAVES_TICK) server.runCommand("/kill @e[type=minecraft:cow]") },
        )
        server.runCommand("/kill @e[type=minecraft:cow]")
        restoreArena(server)

        assertFlyingStart(context, server)

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
        // Moving splices are gated on the deterministic splice scenarios; on this
        // longest course the continuous tape may carry a pace-driven safety hold
        // in-line under suite CPU contention (leg boundaries used to hide the same
        // stops from this gate).
        assertPathingWalk(
            context, server, "pathing-long-haul", Stance(0, 100, 64),
            minLegs = 1, maxLegs = 1, minContinuousSegments = 2, requireMovingSplices = false,
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
        // Holds while chunks stream are intentional stops: the continuous tape
        // carries them in-line where legs used to hide them between tapes, so the
        // moving-splice gate does not apply here.
        assertPathingWalk(
            context, server, "pathing-unloaded-final-goal", Stance(0, 100, 128),
            minLegs = 1, maxLegs = 3, minContinuousSegments = 2,
            requireMovingSplices = false, maxDeviation = EXECUTION_TOLERANCE,
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

            val goal = com.lambda.pathing.core.Stance(0, 100, 5)
            context.runOnClient<IllegalStateException> {
                PathingService.clear()
                com.lambda.pathing.session.PathingRequest(
                    com.lambda.config.automation.AutomationConfig.DEFAULT, goal,
                ).submit()
            }
            awaitWalkTerminal(context, scenario, "prime walk", PathingTestHarness.MAX_PATHING_TICKS)

            // Out of view distance; the origin chunks unload on the client, but the
            // journey toward the goal -- with the goal's chunks granted -- is retained.
            server.runCommand("/tp Steve 0.5 100 192.5 180 0")
            repeat(40) { context.waitTick() }
            context.runOnClient<IllegalStateException> {
                com.lambda.pathing.session.PathingRequest(
                    com.lambda.config.automation.AutomationConfig.DEFAULT, goal,
                ).submit()
            }
            // The manager still reports the prime walk's Complete until the new request
            // is picked up on a later tick -- wait for the state to actually turn over,
            // or the terminal wait below returns instantly against the stale status.
            var activation = 0
            while (activation++ < 100 &&
                PathingService.status is State.Complete
            ) {
                context.waitTick()
            }
            check(PathingService.status !is State.Complete) {
                "$scenario: replan request was never picked up"
            }
            awaitWalkTerminal(context, scenario, "replan walk", maxTicks = 6000)
        } catch (failure: IllegalStateException) {
            PathingTestHarness.pathingFailures += failure.message ?: "$scenario: ${failure::class.simpleName}"
            println("[pathing-fail] ${failure.message}")
        } finally {
            context.runOnClient<IllegalStateException> { PathingService.clear() }
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

    /** A walk requested while hovering in creative flight lands first instead of planning from mid-air. */
    private fun assertFlyingStart(
        context: ClientGameTestContext,
        server: TestServerContext,
    ) {
        val scenario = "pathing-flying-start"
        if (!PathingTestHarness.scenarioSelected(scenario)) return
        try {
            server.runCommand("/tp Steve 0.5 104 0.5 0 0")
            repeat(5) { context.waitTick() }
            context.runOnClient<IllegalStateException> {
                val player = checkNotNull(com.lambda.Lambda.mc.player) { "$scenario: missing client player" }
                player.abilities.flying = true
                player.sendAbilitiesUpdate()
            }
            repeat(10) { context.waitTick() }
            context.runOnClient<IllegalStateException> {
                val player = checkNotNull(com.lambda.Lambda.mc.player) { "$scenario: missing client player" }
                check(player.abilities.flying && !player.isOnGround) { "$scenario: the player is not hovering" }
                PathingService.clear()
                com.lambda.pathing.session.PathingRequest(com.lambda.config.automation.AutomationConfig.DEFAULT, Stance(0, 100, 5)).submit()
            }
            awaitWalkTerminal(context, scenario, "the walk from flight", PathingTestHarness.MAX_PATHING_TICKS)
        } catch (failure: IllegalStateException) {
            PathingTestHarness.pathingFailures += failure.message ?: "$scenario: ${failure::class.simpleName}"
            println("[pathing-fail] ${failure.message}")
        } finally {
            context.runOnClient<IllegalStateException> { PathingService.clear() }
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
            PathingService.status !is State.Complete &&
            PathingService.status !is State.Failed
        ) {
            context.waitTick()
        }
        check(PathingService.status is State.Complete) {
            "$scenario: $phase did not complete (${PathingService.status})"
        }
    }

    private const val LONG_BEDROCK_FIELD_LENGTH = 120

    private const val MOB_LEAVES_TICK = 80
}
