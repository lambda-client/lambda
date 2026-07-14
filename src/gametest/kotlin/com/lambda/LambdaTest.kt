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

import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.config.automation.AutomationConfig
import com.lambda.pathing.PathingManager
import com.lambda.pathing.PathingRequest
import com.lambda.pathing.coarse.Stance
import com.lambda.threading.runSafe
import com.lambda.util.combat.DamageUtils.isFallDeadly
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.input.Input
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.client.network.ClientPlayerEntity
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import kotlin.time.Duration

@Suppress("UnstableApiUsage")
object LambdaTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        val singleplayerContext = context.worldBuilder()
            .adjustSettings {
                it.gameMode = WorldCreator.Mode.CREATIVE
            }
            .create()

        val world = singleplayerContext.clientWorld
        val server = singleplayerContext.server

        world.waitForChunksDownload()

        server.runCommand("/tp Steve ~ ~30 ~")
        context.unit("assert deadly fall") { isFallDeadly() }

        server.runCommand("/tp Steve ~ -50 ~")
        context.unit("assert safe fall") { !isFallDeadly() }
        server.runCommand("/tp Steve ~ -60 ~")

        testOrdinaryMovementReplay(context, server)

        // All the tests passed
        singleplayerContext.close()
    }

    private fun testOrdinaryMovementReplay(
        context: ClientGameTestContext,
        server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext,
    ) {
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

        server.runCommand("/tp Steve 0 100 0 0 0")
        repeat(5) { context.waitTick() }
        assertMovementReplay(context, "steered-turn", buildList {
            repeat(4) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(0.0, 0.0))) }
            repeat(4) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(45.0, 0.0))) }
            repeat(4) { add(MovementSimulationInput(forward = 1.0, rotation = Rotation(90.0, 0.0))) }
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

        testPathingManagerWalk(context, server)
    }

    /**
     * The planner's own tapes, walked live by [PathingManager] -- the same path a
     * `.path` command takes. A simulator-vs-simulator unit test cannot see vanilla
     * divergence, so every coarse move kind M3 claims must land here.
     */
    private fun testPathingManagerWalk(
        context: ClientGameTestContext,
        server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext,
    ) {
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")
        server.runCommand("/fill -8 100 -8 8 105 8 minecraft:air")

        assertPathingWalk(context, server, "pathing-straight", Stance(0, 100, 5))
        assertPathingWalk(context, server, "pathing-diagonal", Stance(5, 100, 5))

        // One-block rise across the corridor: the seed search must find a launch
        // tick, and the manager must steer the turn through the rotation manager.
        server.runCommand("/fill -2 100 2 2 100 8 minecraft:stone")
        assertPathingWalk(context, server, "pathing-step-up", Stance(0, 101, 6))
        server.runCommand("/fill -2 100 2 2 100 8 minecraft:air")

        // Walk-off: the ground drops three blocks past z = 3. Survivable, so the
        // trajectory layer must certify it rather than refuse the whole route.
        server.runCommand("/fill -8 96 -8 8 96 8 minecraft:stone")
        server.runCommand("/fill -8 99 3 8 99 8 minecraft:air")
        assertPathingWalk(context, server, "pathing-walk-off", Stance(0, 97, 6))
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")
        server.runCommand("/fill -8 96 -8 8 96 8 minecraft:air")

        // A two-wide hole. The nominal walk falls in; only a launch discovered by
        // backtracking over that failure gets across. Nothing here is scheduled --
        // the coarse layer proposes a candidate, simulation certifies the jump.
        server.runCommand("/fill -8 99 3 8 99 4 minecraft:air")
        assertPathingWalk(context, server, "pathing-gap-jump", Stance(0, 100, 7))
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")

        // Longer than one tape can certify (~44 blocks). It must be walked as several
        // windows, each ending at a certified stop, and still arrive.
        server.runCommand("/fill -2 99 -8 2 99 70 minecraft:stone")
        server.runCommand("/fill -2 100 -8 2 105 70 minecraft:air")
        // Drift accumulates with tape length: ~3e-8 per frame of double-precision
        // rounding, so a ~250-frame haul lands near 1e-5 where a 12-frame tape stays
        // at 1e-6. The binding contract is the executor's own per-axis tolerance
        // (1e-5), and the cursor accepted every frame of every leg -- this gate simply
        // states that contract rather than a tighter one that only short tapes meet.
        assertPathingWalk(
            context, server, "pathing-long-haul", Stance(0, 100, 64),
            minLegs = 2, maxDeviation = EXECUTION_TOLERANCE,
        )
        server.runCommand("/fill -2 99 9 2 99 70 minecraft:air")
    }

    private fun assertPathingWalk(
        context: ClientGameTestContext,
        server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext,
        scenario: String,
        goal: Stance,
        minLegs: Int = 1,
        maxDeviation: Double = REPLAY_DEVIATION_EPSILON,
    ) {
        server.runCommand("/tp Steve 0.5 100 0.5 0 0")
        repeat(5) { context.waitTick() }

        context.runOnClient<IllegalStateException> {
            val player = Lambda.mc.player ?: error("Missing client player")
            check(player.isOnGround) { "$scenario: player did not settle" }
            PathingManager.clear()
            PathingRequest(AutomationConfig.DEFAULT, goal).submit()
        }

        // The manager owns planning and replay; just let the client tick until it
        // settles. `return@repeat` would be a *continue*, so this must be a real loop
        // with a break -- otherwise the walk finishes and the test keeps ticking.
        var ticks = 0
        while (ticks++ < MAX_PATHING_TICKS) {
            context.waitTick()
            val status = PathingManager.status
            if (status is PathingManager.Status.Complete || status is PathingManager.Status.Failed) break
        }

        context.runOnClient<IllegalStateException> {
            val status = PathingManager.status
            check(status is PathingManager.Status.Complete) { "$scenario: ended $status" }
            check(status.legs >= minLegs) {
                "$scenario: expected at least $minLegs windows, walked ${status.legs}"
            }

            val path = checkNotNull(PathingManager.published) { "$scenario: nothing published" }
            check(path.dependencies().isNotEmpty()) { "$scenario: no voxel dependencies published" }

            check(PathingManager.maxDeviation <= maxDeviation) {
                "$scenario: max deviation ${PathingManager.maxDeviation} exceeded $maxDeviation"
            }
            PathingManager.clear()
        }
    }

    private fun PathingManager.PublishedPath.dependencies() = plan.dependencies

    private fun assertMovementReplay(
        context: ClientGameTestContext,
        scenario: String,
        tape: List<MovementSimulationInput>,
    ) {
        lateinit var previousInput: Input
        lateinit var expected: List<MovementSimulationState>

        context.runOnClient<IllegalStateException> {
            val player = Lambda.mc.player ?: error("Missing client player")
            check(player.isOnGround) { "Movement replay player did not settle on the test platform" }

            previousInput = player.input
            val initial = MovementSimulationState.from(player)
            val profile = PlayerPhysicsProfile.capture(player)
            val center = player.blockPos
            val snapshot = SnapshotSimulationEnvironment.capture(
                world = player.entityWorld,
                player = player,
                bounds = SimulationSnapshotBounds(
                    minX = center.x - 6,
                    minY = center.y - 5,
                    minZ = center.z - 6,
                    maxX = center.x + 6,
                    maxY = center.y + 6,
                    maxZ = center.z + 10,
                ),
            )

            val liveSimulator = MovementSimulator(player, initial)
            val liveExpected = tape.map { liveSimulator.tickMovement(it).simulator.state }
            expected = CompletableFuture.supplyAsync {
                val snapshotSimulator = MovementSimulator(profile, snapshot, initial)
                tape.map { snapshotSimulator.tickMovement(it).simulator.state }
            }.join()

            liveExpected.zip(expected).forEachIndexed { frame, (live, captured) ->
                assertNear(live.position.x, captured.position.x, "$scenario snapshot frame $frame position.x")
                assertNear(live.position.y, captured.position.y, "$scenario snapshot frame $frame position.y")
                assertNear(live.position.z, captured.position.z, "$scenario snapshot frame $frame position.z")
                assertNear(live.velocity.x, captured.velocity.x, "$scenario snapshot frame $frame velocity.x")
                assertNear(live.velocity.y, captured.velocity.y, "$scenario snapshot frame $frame velocity.y")
                assertNear(live.velocity.z, captured.velocity.z, "$scenario snapshot frame $frame velocity.z")
                check(live.onGround == captured.onGround) {
                    "$scenario snapshot frame $frame onGround differs"
                }
            }
        }

        tape.forEachIndexed { frame, input ->
            context.runOnClient<IllegalStateException> {
                val player = Lambda.mc.player ?: error("Missing client player")
                input.rotation?.let { rotation ->
                    player.yaw = rotation.yawF
                    player.pitch = rotation.pitchF
                }
                player.input = buildMovementInput(
                    forward = input.forward,
                    strafe = input.strafe,
                    jump = input.jump,
                    sneak = input.sneak,
                    sprint = input.sprint,
                )
            }

            context.waitTick()

            context.runOnClient<IllegalStateException> {
                val player = Lambda.mc.player ?: error("Missing client player")
                val predicted = expected[frame]
                assertNear(predicted.position.x, player.x, "$scenario frame $frame position.x")
                assertNear(predicted.position.y, player.y, "$scenario frame $frame position.y")
                assertNear(predicted.position.z, player.z, "$scenario frame $frame position.z")
                assertNear(predicted.velocity.x, player.velocity.x, "$scenario frame $frame velocity.x")
                assertNear(predicted.velocity.y, player.velocity.y, "$scenario frame $frame velocity.y")
                assertNear(predicted.velocity.z, player.velocity.z, "$scenario frame $frame velocity.z")
                check(predicted.onGround == player.isOnGround) {
                    "$scenario frame $frame onGround: expected ${predicted.onGround}, actual ${player.isOnGround}"
                }
                // The block the player stands on, and the block vanilla reads
                // friction from. Both are derived, not observed, so a simulator
                // can drift on them while position and velocity still agree --
                // which is exactly how this went unnoticed.
                check(predicted.supportingBlockPos == player.supportingBlockPos.getOrNull()) {
                    "$scenario frame $frame supportingBlockPos: expected ${predicted.supportingBlockPos}, " +
                        "actual ${player.supportingBlockPos.getOrNull()}"
                }
                check(predicted.velocityAffectingPos == player.velocityAffectingPos) {
                    "$scenario frame $frame velocityAffectingPos: expected ${predicted.velocityAffectingPos}, " +
                        "actual ${player.velocityAffectingPos}"
                }
            }
        }

        context.runOnClient<IllegalStateException> {
            Lambda.mc.player?.input = previousInput
        }
    }

    private fun assertNear(expected: Double, actual: Double, label: String) {
        check(abs(expected - actual) <= MOVEMENT_EPSILON) {
            "$label: expected $expected, actual $actual"
        }
    }

    inline fun ClientGameTestContext.unit(label: String, crossinline block: SafeContext.() -> Boolean) {
        waitTick()

        runOnClient<IllegalStateException> {
            val asserted = runSafe(block)
                ?: throw IllegalStateException("Could not run in a safe context")

            check(asserted) { "Assertion failed: $label" }
        }
    }

    private const val MOVEMENT_EPSILON = 1.0E-6

    /** Euclidean counterpart of [MOVEMENT_EPSILON]: sqrt(3) * per-axis, rounded up. */
    private const val REPLAY_DEVIATION_EPSILON = 2.0E-6

    /** What TrajectoryExecutionCursor actually enforces: 1e-5 per axis, as a distance. */
    private const val EXECUTION_TOLERANCE = 1.7E-5

    /** Plan latency plus tape length; a walk that needs longer has already failed. */
    private const val MAX_PATHING_TICKS = 1200
}
