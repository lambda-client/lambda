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
import com.lambda.context.Automated
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.PathingConfig
import com.lambda.pathing.PathingManager
import com.lambda.pathing.PathingRequest
import com.lambda.pathing.coarse.CoarseMoveKind
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
import net.minecraft.util.math.Vec3d
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
        PathingMetricSink.reset()
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")
        server.runCommand("/fill -8 100 -8 8 105 8 minecraft:air")

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
        server.runCommand("/fill -2 100 2 2 100 8 minecraft:air")

        // A rise immediately beyond a deliberately short local horizon exercises
        // the state that failed in-game: the next controller begins with retained
        // momentum close to the lip and must still discover a valid takeoff. The
        // raised section then drops back to the original goal height.
        server.runCommand("/fill -2 99 9 2 99 22 minecraft:stone")
        server.runCommand("/fill -2 100 10 2 100 14 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-moving-splice-step-up", Stance(0, 100, 20),
            maxLegs = 1, minContinuousSegments = 2, requireMovingSplices = true,
            requireJumpInput = true, plannerMaxFrames = 40,
            maxDeviation = EXECUTION_TOLERANCE,
        )
        server.runCommand("/fill -2 100 10 2 100 14 minecraft:air")
        server.runCommand("/fill -2 99 9 2 99 22 minecraft:air")

        // A sequence of rises ending immediately at a long descending gap is the
        // live shape that exposed greedy continuous expansion. The focused JVM test
        // forces the hazard-driven runway boundary; this live companion forces an
        // ordinary frame-horizon boundary and proves the combined tape still retains
        // momentum, launches across span four, and never inserts a runtime stop.
        server.runCommand("/fill -8 100 3 8 100 5 minecraft:stone")
        server.runCommand("/fill -8 101 6 8 101 8 minecraft:stone")
        server.runCommand("/fill -8 102 9 8 102 12 minecraft:stone")
        server.runCommand("/fill -8 101 16 8 101 24 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-ascent-to-descending-gap-splice", Stance(0, 102, 20),
            maxLegs = 1, minGapLaunches = 1, minContinuousSegments = 2,
            requireMovingSplices = true, requireJumpInput = true,
            plannerMaxFrames = 53, maxDeviation = EXECUTION_TOLERANCE,
        )
        server.runCommand("/fill -8 100 3 8 102 24 minecraft:air")

        // Two-block treads climbing to a top that sits three blocks above *both*
        // endpoints, then back down. Every other live rise ends on the high ground,
        // whose height is therefore already in the snapshot's own bounds -- so none of
        // them could ever exercise a route that peaks above what start and goal see.
        // The capture must budget a sprint jump's ceiling above a stance neither
        // endpoint reaches; when it did not, entering these stairs one step lower was
        // the whole difference between a refusal and a certified trajectory.
        server.runCommand("/fill -8 99 3 8 99 8 minecraft:air")
        server.runCommand("/fill -2 100 3 2 100 4 minecraft:stone")
        server.runCommand("/fill -2 101 5 2 101 6 minecraft:stone")
        server.runCommand("/fill -2 102 7 2 102 8 minecraft:stone")
        // A two-wide hole across the top: crossing it is the only thing that launches
        // *from* the peak, and a launch is the only motion that reads four blocks up.
        server.runCommand("/fill -2 102 11 2 102 14 minecraft:stone")
        server.runCommand("/fill -2 101 15 2 101 16 minecraft:stone")
        server.runCommand("/fill -2 99 17 2 99 24 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-staircase-above-both-endpoints", Stance(0, 100, 22),
            maxLegs = 1, minGapLaunches = 1, requireJumpInput = true,
        )
        server.runCommand("/fill -2 100 3 2 102 16 minecraft:air")
        server.runCommand("/fill -2 99 17 2 99 24 minecraft:air")
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")

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
        assertPathingWalk(
            context, server, "pathing-gap-jump-flat-2", Stance(0, 100, 7),
            expectedJumpDy = 0, requireJumpInput = true,
        )
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")

        // A diagonal notch of void: the direct line to a diagonally offset goal is a
        // diagonal jump. Without diagonal jump topology the coarse layer had to zigzag
        // cardinally -- the "slalom" the body then walked. Vanilla must certify the
        // off-axis launch, which is where the sine-table jump boost has to be exact.
        server.runCommand("/fill 1 99 1 1 99 2 minecraft:air")
        server.runCommand("/fill 2 99 1 2 99 1 minecraft:air")
        assertPathingWalk(
            context, server, "pathing-gap-jump-diagonal", Stance(3, 100, 3),
            requireJumpInput = true,
        )
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")

        // A three-wide hole exercises the longest flat jump currently covered by
        // the live corpus. The mask proposes span 4; only vanilla replay proves it.
        server.runCommand("/fill -8 99 3 8 99 5 minecraft:air")
        assertPathingWalk(
            context, server, "pathing-gap-jump-flat-3", Stance(0, 100, 8),
            expectedJumpDy = 0, requireJumpInput = true,
        )
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")

        // One missing support cell with the landing one block higher. This is a
        // rising gap jump, not an adjacent step-up: it needs a span-2 candidate.
        server.runCommand("/fill -8 99 3 8 99 3 minecraft:air")
        server.runCommand("/fill -8 100 4 8 100 8 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-gap-jump-rise-1", Stance(0, 101, 6),
            expectedJumpDy = 1, requireJumpInput = true,
        )
        server.runCommand("/fill -8 100 4 8 100 8 minecraft:air")
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")

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
        server.runCommand("/fill -8 98 4 8 98 8 minecraft:air")
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")

        // The same lower landing across a genuinely two-wide hole. This one must
        // launch, and guards the descending variant of the span-3 template.
        server.runCommand("/fill -8 99 3 8 99 8 minecraft:air")
        server.runCommand("/fill -8 98 5 8 98 8 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-gap-drop-span-3", Stance(0, 99, 7),
            expectedJumpDy = -1, requireJumpInput = true,
        )
        server.runCommand("/fill -8 98 5 8 98 8 minecraft:air")
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")

        // Three descending gaps reproduce the real suffix that exhausted its
        // launch beam after a frame-40 moving splice. Every landing is one block
        // lower; the complete run must keep momentum and still acquire the tight
        // final stop rather than publishing parts.
        server.runCommand("/fill -8 99 3 8 99 20 minecraft:air")
        server.runCommand("/fill -2 98 5 2 98 7 minecraft:stone")
        server.runCommand("/fill -2 97 10 2 97 12 minecraft:stone")
        server.runCommand("/fill -2 96 15 2 96 20 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-descending-gap-chain-splice", Stance(0, 97, 17),
            maxLegs = 1, minGapLaunches = 3, minContinuousSegments = 2,
            requireMovingSplices = true, requireJumpInput = true,
            plannerMaxFrames = 40, maxDeviation = EXECUTION_TOLERANCE,
        )
        server.runCommand("/fill -2 98 5 2 98 7 minecraft:air")
        server.runCommand("/fill -2 97 10 2 97 12 minecraft:air")
        server.runCommand("/fill -2 96 15 2 96 20 minecraft:air")
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")

        // Two independent gaps in one short route must produce one continuous
        // certified tape. The search used to discover only one gap launch, so the
        // planner shortened the window and the manager stopped on every platform,
        // throwing away momentum before planning the next jump.
        server.runCommand("/fill -2 99 9 2 99 14 minecraft:stone")
        server.runCommand("/fill -8 99 3 8 99 4 minecraft:air")
        server.runCommand("/fill -8 99 9 8 99 10 minecraft:air")
        assertPathingWalk(
            context, server, "pathing-gap-chain-one-tape", Stance(0, 100, 13),
            maxLegs = 1, minGapLaunches = 2, requireJumpInput = true,
        )
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")
        server.runCommand("/fill -2 99 9 2 99 14 minecraft:air")

        // Longer than one local controller horizon (~44 blocks). Worker search must
        // expand through predicted moving states and flatten the pieces into one
        // tape; the live player must never stop at those internal boundaries.
        server.runCommand("/fill -2 99 -8 2 99 70 minecraft:stone")
        server.runCommand("/fill -2 100 -8 2 105 70 minecraft:air")
        // Drift accumulates with tape length: ~3e-8 per frame of double-precision
        // rounding, so a ~250-frame haul lands near 1e-5 where a 12-frame tape stays
        // at 1e-6. The binding contract is the executor's own per-axis tolerance
        // (1e-5), and the cursor accepted every frame of every leg -- this gate simply
        // states that contract rather than a tighter one that only short tapes meet.
        assertPathingWalk(
            context, server, "pathing-long-haul", Stance(0, 100, 64),
            maxLegs = 1, minContinuousSegments = 2, requireMovingSplices = true,
            maxDeviation = EXECUTION_TOLERANCE,
            cameraYawDuringPlanning = 90.0f,
        )
        server.runCommand("/fill -2 99 9 2 99 70 minecraft:air")
    }

    private fun assertPathingWalk(
        context: ClientGameTestContext,
        server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext,
        scenario: String,
        goal: Stance,
        minLegs: Int = 1,
        maxLegs: Int? = null,
        maxDeviation: Double = REPLAY_DEVIATION_EPSILON,
        expectedJumpDy: Int? = null,
        requireJumpInput: Boolean = false,
        minGapLaunches: Int = 0,
        minContinuousSegments: Int = 1,
        requireMovingSplices: Boolean = false,
        cameraYawDuringPlanning: Float? = null,
        plannerMaxFrames: Int? = null,
        driftBeforeSubmit: Vec3d? = null,
    ) {
        server.runCommand("/tp Steve 0.5 100 0.5 0 0")
        repeat(5) { context.waitTick() }

        context.runOnClient<IllegalStateException> {
            val player = Lambda.mc.player ?: error("Missing client player")
            check(player.isOnGround) { "$scenario: player did not settle" }
            PathingManager.clear()
            val automated = plannerMaxFrames?.let { frames ->
                object : Automated by AutomationConfig.DEFAULT {
                    override val pathingConfig = object : PathingConfig by AutomationConfig.DEFAULT.pathingConfig {
                        override val maxFrames = frames
                    }
                }
            } ?: AutomationConfig.DEFAULT
            // Submit while the body still drifts, reproducing a rapid retry: the manager
            // must settle to true rest before capturing, or frame zero is a moving state
            // the player has already shed by the time the async plan returns.
            driftBeforeSubmit?.let { player.velocity = it }
            PathingRequest(automated, goal).submit()
            // Change view immediately after the manager captured its immutable
            // planning yaw. Waiting for another client tick is racy: short plans
            // can finish before the test thread observes Status.Planning.
            cameraYawDuringPlanning?.let { player.yaw = it }
        }

        // Simulates the user looking elsewhere while the worker owns an immutable
        // start state. Movement yaw must be held/aligned rather than rejecting the
        // certified continuation because the camera changed during planning.
        // The assignment above is intentionally in the submit task, before the
        // worker completion can be installed on a later client task.

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
            val published = PathingManager.published
            if (status !is PathingManager.Status.Complete || published == null) {
                PathingMetricSink.record(
                    PathingMetricSink.Run(
                        scenario, success = false, completionTicks = ticks,
                        trajectoryFrames = published?.plan?.frames?.size ?: 0,
                        collisionFrames = 0, bumps = 0,
                        launchMarginFrames = published?.launchMarginFrames ?: 0,
                        planLatencyMs = published?.planMillis ?: 0L,
                        reroutes = published?.reroutes ?: 0,
                        maxReplayDeviation = PathingManager.maxDeviation,
                    ),
                )
            }
            check(status is PathingManager.Status.Complete) { "$scenario: ended $status" }
            check(status.legs >= minLegs) {
                "$scenario: expected at least $minLegs windows, walked ${status.legs}"
            }
            maxLegs?.let {
                check(status.legs <= it) {
                    "$scenario: expected at most $it windows, walked ${status.legs}"
                }
            }

            val path = checkNotNull(published) { "$scenario: nothing published" }
            check(path.dependencies().isNotEmpty()) { "$scenario: no voxel dependencies published" }

            expectedJumpDy?.let { dy ->
                check(path.route.edges.any { edge ->
                    edge.kind == CoarseMoveKind.JUMP_CANDIDATE &&
                        edge.to.y - edge.from.y == dy
                }) {
                    "$scenario: no dy=$dy jump candidate in ${path.route.edges}"
                }
            }
            if (requireJumpInput) {
                check(path.plan.tape.asList().any { it.jump }) {
                    "$scenario: certified tape never pressed jump"
                }
            }
            check(path.parameters.gapLaunchFrames.size >= minGapLaunches) {
                "$scenario: expected at least $minGapLaunches discovered gap launches, " +
                    "got ${path.parameters.gapLaunchFrames}"
            }
            check(path.controlSegments >= minContinuousSegments) {
                "$scenario: expected at least $minContinuousSegments continuous control segments, " +
                    "got ${path.controlSegments}"
            }
            if (requireMovingSplices) {
                check(path.spliceFrames.isNotEmpty()) { "$scenario: no predicted splice frames published" }
                check(path.spliceFrames.all { frame ->
                    path.plan.frames[frame - 1].state.velocity.horizontalLength() > 0.012
                }) {
                    "$scenario: an internal splice discarded momentum at ${path.spliceFrames}"
                }
            }

            check(PathingManager.maxDeviation <= maxDeviation) {
                "$scenario: max deviation ${PathingManager.maxDeviation} exceeded $maxDeviation"
            }
            val frames = path.plan.frames
            var previousCollision = frames.firstOrNull()?.let { path.plan.initialState.horizontalCollision } ?: false
            var bumps = 0
            frames.forEach { frame ->
                if (frame.state.horizontalCollision && !previousCollision) bumps++
                previousCollision = frame.state.horizontalCollision
            }
            val metrics = PathingMetricSink.Run(
                    scenario = scenario,
                    success = true,
                    completionTicks = ticks,
                    trajectoryFrames = frames.size,
                    collisionFrames = frames.count { it.state.horizontalCollision },
                    bumps = bumps,
                    launchMarginFrames = path.launchMarginFrames,
                    planLatencyMs = path.planMillis,
                    reroutes = path.reroutes,
                    maxReplayDeviation = PathingManager.maxDeviation,
                )
            PathingMetricSink.record(metrics)
            PathingMetricSink.assertWithinBaseline(metrics)
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
                check(predicted.isSprinting == player.isSprinting) {
                    "$scenario frame $frame isSprinting: expected ${predicted.isSprinting}, " +
                        "actual ${player.isSprinting}"
                }
                check(predicted.collidedSoftly == player.collidedSoftly) {
                    "$scenario frame $frame collidedSoftly: expected ${predicted.collidedSoftly}, " +
                        "actual ${player.collidedSoftly}"
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

    /** Stricter measured Euclidean gate than the cursor's frame-scaled per-axis bound. */
    private const val EXECUTION_TOLERANCE = 1.7E-5

    /** Plan latency plus tape length; a walk that needs longer has already failed. */
    private const val MAX_PATHING_TICKS = 1200
}
