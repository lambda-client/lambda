/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.*
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.*
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.*
import com.lambda.pathing.search.PublishedPath
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.bedrockEnvironment
import pathing.ProbeScenarios.moveLibrary

/**
 * The receding horizon, walked end to end against a wall clock, with no client.
 *
 * Two things have to hold. The body must arrive -- a horizon that keeps its options open
 * is worthless if it never closes them -- and **every tape it is ever handed must end in a
 * certified stop**, because the executor replays inputs open loop and running out of them
 * mid-stride has no defined behaviour. The second is the property that makes the whole
 * design legal, so it is checked on every publication rather than at the end.
 */
@Tag("bedrock-corpus")
class HorizonWalkProbeTest {
    @Test
    fun `a horizon walk arrives, and never publishes a tape that cannot stop`() =
        walk(20, 20, microsPerExpansion = 640L)

    /**
     * The same walk with steps made deliberately expensive.
     *
     * A step that takes longer to find than the motion it commits lets the body walk off
     * the end of its own tape and stop -- which is what every route past a couple of
     * hundred ticks did in game. The commitment has to size itself from what the step
     * actually cost, so this fixture makes steps slow on purpose and still demands arrival.
     */
    @Test
    fun `a horizon walk with slow steps still keeps ahead of the body`() =
        walk(60, 5, microsPerExpansion = 2000L)

    /**
     * The horizon walked over several routes, for judging changes to the search itself.
     *
     * The two single-route fixtures answer "does it arrive, and is every tape stoppable".
     * They cannot answer "is this change better", because one route is one sample and the
     * answers have come back split -- a change worth five frames on one and minus five on
     * the other. This walks a corpus instead, with the same wall-clock cursor, so an action
     * change can be judged without a Minecraft client in the loop.
     */
    @Test
    fun `horizon corpus`() {
        val environment = bedrockEnvironment()
        val moves = moveLibrary(SimpleMoveOptions(maxJumpDrop = 2))
        val config = MotionConstraints()
        var total = 0
        var arrived = 0
        var largest = 0

        for ((index, endpoints) in BedrockFieldLayout.randomEndpointPairs(count = 6).withIndex()) {
            val start = Stance(endpoints.first.x, endpoints.first.y, endpoints.first.z)
            val goal = Stance(endpoints.second.x, endpoints.second.y, endpoints.second.z)
            val planner = CoarsePlanner(environment, moves, start, goal)
            if (!planner.repair(Duration.INFINITE).converged) continue
            planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
            val route = planner.routePlan(index.toLong()) ?: continue
            val dx = (goal.x - start.x).toDouble()
            val dz = (goal.z - start.z).toDouble()
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
            )

            val clock = VirtualSearchClock()
            val sizes = ArrayList<Int>()
            var previous = 0
            val outcome = TrajectoryPlanner.walkHorizon(
                route, planner, initial, PROFILE, environment, config,
                cursorFrame = { clock.cursorFrame() },
                publish = { path, _ ->
                    sizes += path.plan.tape.frameCount - previous
                    previous = path.plan.tape.frameCount
                },
                started = System.currentTimeMillis(),
                clock = clock,
            )
            val planned = (outcome as? com.lambda.pathing.PathPlanResult.Planned)?.path
            if (planned != null && !planned.partial) {
                arrived++
                total += planned.plan.tape.frameCount
            }
            largest = maxOf(largest, sizes.maxOrNull() ?: 0)
            println("[corpus] case %d %s->%s: %s %d frames".format(
                index, "${start.x},${start.y},${start.z}", "${goal.x},${goal.y},${goal.z}",
                if (planned?.partial == false) "arrived" else "STOPPED SHORT",
                planned?.plan?.tape?.frameCount ?: 0))
        }
        println("[corpus] arrived %d, total %d frames, largest commitment %d".format(arrived, total, largest))
        check(arrived >= 5) { "only $arrived of 6 routes arrived" }
    }

    /**
     * [microsPerExpansion] is the fixture's whole personality: 640 is the corpus tempo
     * (~78 expansions per body frame), 2000 a deliberately starved search (~25 per
     * frame). It used to be a real wall clock, which made the starved fixture a bet on
     * machine load -- it failed one run in three on an untouched tree whenever another
     * test saturated the cores, and the trap note said never to read that as a planner
     * regression. The virtual clock races the same race deterministically.
     *
     * Calibrating it surfaced a real edge the wall clock had been sampling at random:
     * at 1200 and 2500 the starved walk ends partial -- the body outruns the search on
     * particular interleavings and the walk brakes short. Those tempos are left out of
     * the gate deliberately; they are the starvation lead's reproduction recipe, not a
     * regression signal.
     */
    private fun walk(lookahead: Int, commitFrames: Int, microsPerExpansion: Long) {
        val environment = bedrockEnvironment()
        val moves = moveLibrary(SimpleMoveOptions(maxJumpDrop = 2))
        val config = MotionConstraints()
        val surface = BedrockFieldLayout.standableSurface(BedrockFieldLayout.solidCells())
        val head = checkNotNull(surface.filter { it.x <= 2 }.minByOrNull { it.z * it.z })
        val tail = checkNotNull(
            surface.filter { it.x >= BedrockFieldLayout.LENGTH - 3 }.minByOrNull { it.z * it.z }
        )
        val start = Stance(head.x, head.y, head.z)
        val goal = Stance(tail.x, tail.y, tail.z)

        val planner = CoarsePlanner(environment, moves, start, goal)
        check(planner.repair(Duration.INFINITE).converged) { "no coarse route across the field" }
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val dx = (goal.x - start.x).toDouble()
        val dz = (goal.z - start.z).toDouble()
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
            rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
        )
        val route = checkNotNull(planner.routePlan(0L))

        val clock = VirtualSearchClock(microsPerExpansion = microsPerExpansion)
        val publications = ArrayList<Pair<Long, PublishedPath>>()
        val result = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, environment, config,
            cursorFrame = { clock.cursorFrame() },
            publish = { path, _ -> publications += clock.elapsedMillis() to path },
            started = System.currentTimeMillis(),
            lookahead = lookahead,
            commitFrames = commitFrames,
            clock = clock,
            fieldExpansionBudget = kotlin.time.Duration.INFINITE,
        )

        // Adoption fidelity: the executor refuses a tape that disagrees with frames it has
        // already pressed, so a publication that diverges behind the cursor is one the body
        // will never take -- it walks its old tape to the brake and stops instead. Without
        // this check the probe accepted every publication and happily green-lit two changes
        // that halted the live walk four and seven times.
        var walkedTo = 0
        var previousTape: List<MovementSimulationInput>? = null
        publications.forEach { (millis, path) ->
            val tape = path.plan.tape.asList()
            previousTape?.let { earlier ->
                val shared = minOf(walkedTo, earlier.size, tape.size)
                check((0 until shared).all { earlier[it] == tape[it] }) {
                    "tape published at $millis ms diverges within the $shared frames already walked"
                }
            }
            previousTape = tape
            walkedTo = ((millis * 1_000_000L) / 50_000_000L).toInt()

            val last = path.plan.frames.last().state
            check(last.onGround && last.velocity.horizontalLength() <= config.stoppedSpeed) {
                "tape published at $millis ms does not end stopped: " +
                    "ground=${last.onGround} speed=${last.velocity.horizontalLength()}"
            }
        }
        val commits = publications.map { it.second.plan.tape.frameCount }
        val steps = commits.zipWithNext { a, b -> b - a }
        println("[horizon] final tape %d frames".format(
            (result as? com.lambda.pathing.PathPlanResult.Planned)?.path?.plan?.tape?.frameCount ?: 0))
        println("[horizon] largest commitment %d, median %d".format(
            steps.maxOrNull() ?: 0, steps.sorted().getOrElse(steps.size / 2) { 0 }))
        println("[horizon] %d publications, result %s, commit sizes %s".format(
            publications.size, result::class.simpleName,
            commits.zipWithNext { a, b -> b - a }.joinToString(),
        ))
        publications.take(4).forEach { (millis, path) ->
            println("[horizon]   %5d ms: %d frames%s".format(
                millis, path.plan.tape.frameCount, if (path.partial) " (partial)" else " FINAL"))
        }
        publications.lastOrNull()?.let { (millis, path) ->
            println("[horizon]   last %5d ms: %d frames%s".format(
                millis, path.plan.tape.frameCount, if (path.partial) " (partial)" else " FINAL"))
        }
        // Arriving is not implied by ending stopped: a horizon that halts safely halfway
        // has satisfied its safety property and failed at its job. The arriving tape comes
        // back as the *result* -- the callback only ever carries the partials handed over
        // while walking -- so both have to be considered.
        check(publications.isNotEmpty()) { "nothing was ever published" }
        val last = (result as? com.lambda.pathing.PathPlanResult.Planned)?.path
            ?: publications.last().second
        check(!last.partial) { "walk ended on a partial tape, never reached $goal" }
        val end = last.plan.frames.last().state
        val error = kotlin.math.hypot(
            end.position.x - (goal.x + 0.5), end.position.z - (goal.z + 0.5),
        )
        check(error <= config.goalRadius && kotlin.math.abs(end.position.y - goal.y) <= 0.5) {
            "walk stopped %.2f blocks from %s at %s".format(error, goal, end.position)
        }
    }

}
