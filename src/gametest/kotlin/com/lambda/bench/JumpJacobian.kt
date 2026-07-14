/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.bench

import com.lambda.Lambda.LOG
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.threading.runSafe
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import com.lambda.worldview.LiveWorldView
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * W0 — the physics constants the window solver is built on.
 *
 * The solver's whole claim to being *smart* rather than brute-force is that a
 * missed landing tells it how to fix itself:
 *
 * ```
 * overshot by e  =>  launch e earlier      (de/ds ~ +1)
 *                or  arrive e / T_air slower  (de/dv ~ +T_air)
 * ```
 *
 * and that the launch positions available to it form a lattice spaced one
 * per-tick stride apart, so a correction smaller than a stride can only be made
 * by changing the approach. Those are physical claims. This measures them
 * instead of assuming them — against the SIMULATOR, because the simulator is
 * what the solver will search over. (Simulator-vs-server divergence is a
 * separate quantity; the executor's tube monitor owns it.)
 *
 * Measured, for each gait, over a sweep of launch progress x entry speed:
 *
 * - `D(s, v)`   landing distance along the jump line from the takeoff centre
 * - `dD/ds`     expected ~ +1  (launch a bit further along, land a bit further)
 * - `dD/dv`     expected ~ +8..12 blocks per (b/t) — the number that makes
 *               entry velocity load-bearing rather than a detail
 * - `T_air`     flight ticks
 * - `stride`    per-tick ground displacement (the launch-lattice spacing)
 * - monotonicity of D in both s and v, which is what licenses bisection
 *   instead of a scan. Where it breaks, the solver must fall back to
 *   enumeration — so we need to know exactly where it breaks.
 *
 * Open loop on purpose: hold forward, no brake policy. This is the *base*
 * ballistic curve the (schedule, tick) search perturbs; the brake policy is a
 * controller modifier layered on top, and folding it in here would make the
 * measurement circular (the brake depends on distance-to-target, which is the
 * thing being measured).
 *
 * Run: `-Pbench.jacobian=true`. Output: `jump-jacobian.json` + a logged table.
 */
object JumpJacobian {
    private const val FEET_Y = 70
    /** Long enough for the stride run-up (40 ticks x 0.28 b/t). */
    private const val PAD_MIN_X = -24
    /**
     * The takeoff platform ends at block 0; everything from x=1 is void.
     *
     * A player may stand with their centre up to ~0.8 past a block's centre and
     * stay grounded (the AABB still overlaps the block top) — that is the lip
     * stance — so the whole progress ladder is supported by block 0 alone, and
     * every arc then flies into open air. Simulating into the void is what lets
     * ONE launch yield the landing distance for EVERY rise class: we read off
     * where the descending arc crosses each candidate landing height.
     */
    private const val PAD_MAX_X = 0
    private const val PAD_HALF_Z = 4
    private const val VOID_MAX_X = 40

    /** Landing heights, relative to the takeoff. Discovery's TAKEOFF_RISES + drops. */
    private val RISES = listOf(1, 0, -1, -2, -3)

    /** Launch progress: blocks past the takeoff node centre, along the line. */
    private val PROGRESS = listOf(-0.2, -0.1, 0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7)

    /** Entry speed: STORED velocity (post-friction), the quantity the gates read. */
    private val SPEEDS = listOf(0.00, 0.03, 0.06, 0.09, 0.12, 0.15, 0.18, 0.21, 0.24, 0.27, 0.30)

    private const val MAX_TICKS = 40

    private data class Shot(
        val gait: String,
        /** Landing height relative to the takeoff (+1 = ascend, -3 = drop). */
        val rise: Int,
        val progress: Double,
        val speed: Double,
        /** Landing distance along the line from the takeoff node centre. */
        val distance: Double,
        val airTicks: Int,
        val landed: Boolean,
    )

    fun run(context: ClientGameTestContext, server: TestServerContext) {
        LOG.info("[Jacobian] building takeoff platform + void")
        server.runOnServer<IllegalStateException> { minecraftServer ->
            val world = minecraftServer.overworld
            for (x in PAD_MIN_X..VOID_MAX_X) for (z in -PAD_HALF_Z..PAD_HALF_Z) {
                // Clear a deep well so a -3 arc keeps falling instead of
                // landing on terrain and truncating the trajectory.
                for (y in FEET_Y - 12..FEET_Y + 8) {
                    world.setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultState, Block.NOTIFY_LISTENERS)
                }
                if (x <= PAD_MAX_X) {
                    world.setBlockState(
                        BlockPos(x, FEET_Y - 1, z),
                        Blocks.SMOOTH_QUARTZ.defaultState,
                        Block.NOTIFY_LISTENERS,
                    )
                }
            }
        }
        server.runCommand("/tp Steve 0.5 $FEET_Y.0 0.5 -90 0")
        context.waitTicks(10)

        val shots = context.computeOnClient<List<Shot>, IllegalStateException> {
            runSafe {
                val profile = PlayerPhysicsProfile.capture(player)
                val environment = SnapshotSimulationEnvironment(LiveWorldView(world))
                buildList {
                    for (sprint in listOf(false, true)) {
                        for (progress in PROGRESS) {
                            for (speed in SPEEDS) {
                                addAll(shoot(profile, environment, progress, speed, sprint))
                            }
                        }
                    }
                }
            } ?: emptyList()
        }

        val strides = context.computeOnClient<Map<String, Double>, IllegalStateException> {
            runSafe {
                val profile = PlayerPhysicsProfile.capture(player)
                val environment = SnapshotSimulationEnvironment(LiveWorldView(world))
                mapOf(
                    "walk" to stride(profile, environment, sprint = false),
                    "sprint" to stride(profile, environment, sprint = true),
                )
            } ?: emptyMap()
        }

        report(shots, strides)
    }

    /**
     * One open-loop jump into the void: stand at [progress] past the takeoff
     * centre carrying [speed] along +x, jump on tick 0, hold forward, and let
     * the arc fly. Record the trajectory, then read off — for every rise class
     * at once — the horizontal distance at which the *descending* arc crosses
     * that landing height. One simulation, five rise classes.
     *
     * A landing is taken on the way DOWN. An ascend is only a landing once the
     * arc is past its apex and coming back onto the higher block; the crossing
     * on the way *up* is the player still rising through that height, which is
     * not a touchdown.
     */
    private fun shoot(
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        progress: Double,
        speed: Double,
        sprint: Boolean,
    ): List<Shot> {
        val origin = Vec3d(0.5, FEET_Y.toDouble(), 0.5)
        val from = origin.add(Vec3d(progress, 0.0, 0.0))
        // Aim down the line so the facing IS the jump line and the sprint-jump
        // boost fires along it.
        val rotation = Rotation(-90.0, 0.0)

        val simulator = MovementSimulator(
            profile = profile,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = profile,
                position = from,
                rotation = rotation,
                velocity = Vec3d(speed, 0.0, 0.0),
                onGround = true,
                isSprinting = sprint,
            ),
            skipEntityCollisions = true,
        )

        // Tick-by-tick arc, starting from the launch pose.
        val arc = ArrayList<Vec3d>(MAX_TICKS)
        arc += from
        var apexPassed = false
        for (tick in 0 until MAX_TICKS) {
            val before = simulator.lastTick
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = 1.0,
                    strafe = 0.0,
                    jump = tick == 0,
                    sneak = false,
                    sprint = sprint,
                    useItemSlowdown = false,
                    rotation = rotation,
                )
            )
            arc += current.position
            if (tick > 0 && current.position.y < before.position.y) apexPassed = true
            if (current.simulator.state.horizontalCollision) break
            if (apexPassed && current.position.y < FEET_Y + RISES.min() - 1.0) break
        }

        val gait = if (sprint) "sprint" else "walk"
        return RISES.map { rise ->
            crossing(arc, FEET_Y + rise.toDouble())?.let { (distance, ticks) ->
                Shot(gait, rise, progress, speed, distance - (origin.x - 0.5) - 0.5, ticks, true)
            } ?: Shot(gait, rise, progress, speed, Double.NaN, MAX_TICKS, false)
        }
    }

    /**
     * Horizontal distance (absolute x) at which the DESCENDING arc first
     * crosses [height], linearly interpolated between the bracketing ticks,
     * plus the tick count to get there. Null if the arc never reaches it.
     */
    private fun crossing(arc: List<Vec3d>, height: Double): Pair<Double, Int>? {
        for (i in 1 until arc.size) {
            val a = arc[i - 1]
            val b = arc[i]
            val descending = b.y < a.y
            if (!descending) continue
            if (a.y >= height && b.y <= height) {
                val span = a.y - b.y
                val t = if (span < 1.0E-9) 0.0 else (a.y - height) / span
                return (a.x + (b.x - a.x) * t) to i
            }
        }
        return null
    }

    /**
     * Steady-state per-tick ground displacement — the spacing of the launch
     * lattice. A correction smaller than this cannot be made by choosing a
     * different launch tick; only by changing the approach.
     */
    private fun stride(
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        sprint: Boolean,
    ): Double {
        val rotation = Rotation(-90.0, 0.0)
        val simulator = MovementSimulator(
            profile = profile,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = profile,
                position = Vec3d(0.5, FEET_Y.toDouble(), 0.5),
                rotation = rotation,
                velocity = Vec3d.ZERO,
                onGround = true,
                isSprinting = sprint,
            ),
            skipEntityCollisions = true,
        )
        var previous = simulator.lastTick.position
        var last = 0.0
        // Run well past the acceleration ramp; report the terminal stride.
        for (tick in 0 until 40) {
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = 1.0, strafe = 0.0, jump = false, sneak = false,
                    sprint = sprint, useItemSlowdown = false, rotation = rotation,
                )
            )
            last = hypot(current.position.x - previous.x, current.position.z - previous.z)
            previous = current.position
        }
        return last
    }

    // ------------------------------------------------------------------
    // Fitting + reporting
    // ------------------------------------------------------------------

    private fun report(shots: List<Shot>, strides: Map<String, Double>) {
        val landed = shots.filter { it.landed }
        val table = StringBuilder("\n[Jacobian] ==== JUMP PHYSICS CONSTANTS (simulator) ====\n")
        strides.forEach { (gait, stride) ->
            table.append("[Jacobian] stride %-6s = %.4f blocks/tick  (launch-lattice spacing)\n".format(gait, stride))
        }

        val fits = LinkedHashMap<String, Triple<Double, Double, Double>>()
        table.append(
            "[Jacobian] %-6s %5s %8s %8s %8s %10s  %s\n".format(
                "gait", "rise", "dD/ds", "dD/dv", "T_air", "reach@.15", "monotone s / v",
            )
        )
        for (gait in listOf("walk", "sprint")) {
            for (rise in RISES) {
                val group = landed.filter { it.gait == gait && it.rise == rise }
                if (group.size < 4) continue
                // dD/ds at fixed speed, averaged over speeds; dD/dv at fixed
                // progress, averaged over progresses.
                val dDds = group.groupBy { it.speed }.values
                    .mapNotNull { slopeOver(it, { s -> s.progress }, { s -> s.distance }) }
                val dDdv = group.groupBy { it.progress }.values
                    .mapNotNull { slopeOver(it, { s -> s.speed }, { s -> s.distance }) }
                if (dDds.isEmpty() || dDdv.isEmpty()) continue
                val air = group.map { it.airTicks.toDouble() }.average()
                val dsMean = dDds.average()
                val dvMean = dDdv.average()
                // Reach from a centred launch at a realistic ground entry
                // (stored ~0.15): the practical "how far does this class go".
                val reach = group.filter { abs(it.progress) < 1.0E-6 && abs(it.speed - 0.15) < 1.0E-6 }
                    .map { it.distance }.average()
                fits["$gait/$rise"] = Triple(dsMean, dvMean, air)
                table.append(
                    "[Jacobian] %-6s %+5d %+8.3f %+8.2f %8.1f %10s  %s / %s\n".format(
                        gait, rise, dsMean, dvMean, air,
                        if (reach.isNaN()) "-" else "%.2f".format(reach),
                        monotone(group.groupBy { it.speed }.values, { it.progress }, { it.distance }),
                        monotone(group.groupBy { it.progress }.values, { it.speed }, { it.distance }),
                    )
                )
            }
        }
        // The load-bearing check: if dD/dv were small, entry velocity would not
        // matter and the whole design premise would be wrong.
        fits["sprint/0"]?.let { (_, dv, _) ->
            table.append(
                "[Jacobian] => a 0.02 b/t entry error moves a flat sprint landing %.2f blocks\n".format(abs(dv) * 0.02)
            )
        }
        table.append("[Jacobian] %d/%d sampled (launch x rise) pairs landed\n".format(landed.size, shots.size))
        LOG.info(table.toString())

        File(ScenarioRunner.outputDir, "jump-jacobian.json").writeText(
            buildString {
                append("{\"strides\":{")
                append(strides.entries.joinToString(",") { "\"${it.key}\":${"%.5f".format(it.value)}" })
                append("},\"fits\":{")
                append(fits.entries.joinToString(",") { (key, f) ->
                    "\"$key\":{\"dD_ds\":${"%.4f".format(f.first)}," +
                        "\"dD_dv\":${"%.3f".format(f.second)},\"T_air\":${"%.2f".format(f.third)}}"
                })
                append("},\"shots\":[\n")
                append(shots.joinToString(",\n") { shot ->
                    "  {\"gait\":\"${shot.gait}\",\"rise\":${shot.rise}," +
                        "\"progress\":${shot.progress},\"speed\":${shot.speed}," +
                        "\"distance\":${if (shot.landed) "%.4f".format(shot.distance) else "null"}," +
                        "\"airTicks\":${shot.airTicks},\"landed\":${shot.landed}}"
                })
                append("\n]}\n")
            }
        )
        LOG.info("[Jacobian] wrote jump-jacobian.json")
    }

    /** Least-squares slope of y over x for one series (needs >= 2 points). */
    private fun slopeOver(
        series: List<Shot>,
        x: (Shot) -> Double,
        y: (Shot) -> Double,
    ): Double? {
        if (series.size < 2) return null
        val xs = series.map(x)
        val ys = series.map(y)
        val mx = xs.average()
        val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        return if (den < 1.0E-9) null else num / den
    }

    private fun spread(values: List<Double>): Double =
        if (values.size < 2) 0.0 else values.max() - values.min()

    /**
     * Monotone increasing along each series? This is what licenses bisection
     * over the launch tick instead of an exhaustive scan — and where it fails
     * (head bonks flatten the arc) the solver must enumerate instead.
     */
    private fun monotone(
        series: Collection<List<Shot>>,
        x: (Shot) -> Double,
        y: (Shot) -> Double,
    ): String {
        var violations = 0
        var pairs = 0
        for (group in series) {
            val sorted = group.sortedBy(x)
            sorted.zipWithNext { a, b ->
                pairs++
                if (y(b) < y(a) - 1.0E-6) violations++
            }
        }
        return if (violations == 0) "yes ($pairs pairs)" else "NO — $violations/$pairs violations"
    }
}
