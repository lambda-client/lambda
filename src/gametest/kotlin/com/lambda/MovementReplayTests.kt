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

import com.lambda.PathingTestHarness.assertMovementReplay
import com.lambda.PathingTestHarness.restoreArena
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.MovementSimulationInput
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext

/**
 * Simulator-vs-vanilla replay scenarios: hand-written input tapes are simulated
 * against both the live world and a captured snapshot, then replayed against the
 * real client tick, asserting per-frame agreement of position, velocity and the
 * derived state vanilla reads friction from.
 */
@Suppress("UnstableApiUsage")
internal object MovementReplayTests {
    fun run(
        context: ClientGameTestContext,
        server: TestServerContext,
    ) {
        restoreArena(server)
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }

        assertMovementReplay(context, "walk-jump", buildList {
            repeat(4) { add(MovementSimulationInput(forward = 1.0)) }
            add(MovementSimulationInput(forward = 1.0, jump = true))
            repeat(6) { add(MovementSimulationInput(forward = 1.0)) }
        })

        server.runCommand("/setblock 0 100 2 minecraft:stone_slab[type=bottom]")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(
            context = context,
            scenario = "slab-auto-step",
            tape = List(12) { MovementSimulationInput(forward = 1.0) },
        )

        server.runCommand("/setblock 0 100 2 minecraft:air")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(context, "sprint-coast", buildList {
            repeat(7) { add(MovementSimulationInput(forward = 1.0, sprint = true)) }
            repeat(7) { add(MovementSimulationInput()) }
        })

        // Sprinting from stone onto slime: the stepping drag and the landing reflection are
        // the two halves of slime a grounded tape touches, and a per-tick disagreement in
        // either diverges replay on the tick it first happens.
        server.runCommand("/fill -2 99 3 2 99 8 minecraft:slime_block")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(context, "slime-stride", buildList {
            repeat(14) { add(MovementSimulationInput(forward = 1.0, sprint = true, rotation = Rotation(0.0, 0.0))) }
        })

        // The same stride with the carpet half: the block underfoot must be read through
        // the carpet for both the drag and the landing reflection.
        server.runCommand("/fill -2 100 6 2 100 8 minecraft:white_carpet")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(context, "slime-carpet-stride", buildList {
            repeat(22) { add(MovementSimulationInput(forward = 1.0, sprint = true, rotation = Rotation(0.0, 0.0))) }
        })
        server.runCommand("/fill -2 100 6 2 100 8 minecraft:air")
        server.runCommand("/fill -2 99 3 2 99 8 minecraft:stone")

        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(context, "steered-turn", buildList {
            repeat(4) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(0.0, 0.0))) }
            repeat(4) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(45.0, 0.0))) }
            repeat(4) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(90.0, 0.0))) }
        })

        // Internal trajectory expansion may choose a different sprint gait at a
        // moving controller boundary. That transition is only safe to flatten if
        // vanilla and the simulator give the sprint input identical tick semantics.
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(context, "moving-sprint-turn-transition", buildList {
            repeat(5) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(0.0, 0.0))) }
            add(MovementSimulationInput(forward = 1.0, sprint = true, rotation = Rotation(30.0, 0.0)))
            repeat(5) { add(MovementSimulationInput(forward = 1.0, sprint = true, rotation = Rotation(60.0, 0.0))) }
            add(MovementSimulationInput(forward = 1.0, sprint = false, rotation = Rotation(90.0, 0.0)))
            repeat(3) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(90.0, 0.0))) }
        })

        server.runCommand("/setblock 0 100 2 minecraft:stone")
        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(
            context = context,
            scenario = "full-block-wall",
            tape = List(12) { MovementSimulationInput(forward = 1.0) },
        )

        server.runCommand("/setblock 0 100 2 minecraft:air")

        // ClientPlayerEntity classifies a near-parallel edge scrape as a soft
        // collision. It preserves sprint on the following tick even though
        // horizontalCollision is true; this is the exact state transition that
        // diverged during a rising-edge jump in live pathing.
        server.runCommand("/fill -1 100 -2 -1 101 5 minecraft:stone")
        server.runCommand("/tp Steve 0.3001 100 0.5 0.7 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(
            context,
            "sprint-glancing-edge",
            List(6) {
                MovementSimulationInput(
                    forward = 1.0,
                    sprint = true,
                    rotation = Rotation(0.7, 0.0),
                )
            },
        )
        server.runCommand("/fill -1 100 -2 -1 101 5 minecraft:air")

        // Vanilla's sprint-jump boost goes through MathHelper's sine table. Yaw
        // 0/45/90 land exactly on table indices, so only an off-axis heading can
        // expose a simulator that used Math.sin instead.
        server.runCommand("/tp Steve 0 100 0 37 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(context, "sprint-jump-offaxis", buildList {
            val facing = Rotation(37.0, 0.0)
            repeat(4) { add(MovementSimulationInput(forward = 1.0, sprint = true, rotation = facing)) }
            add(MovementSimulationInput(forward = 1.0, sprint = true, jump = true, rotation = facing))
            repeat(10) { add(MovementSimulationInput(forward = 1.0, sprint = true, rotation = facing)) }
        })
    }
}
