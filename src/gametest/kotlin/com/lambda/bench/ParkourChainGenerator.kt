/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.bench

import com.lambda.util.world.fastVectorOf
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * The reliability corpus: seeded random-walk parkour chains on 1x1 pads.
 *
 * [ParkourCourseGenerator] measures each jump class ONCE, in isolation, from a
 * canonical entry state — a six-block runway, a pre-aimed spawn, and a landing
 * pad widened away from the gap. Production reliability is the product over
 * *chained* jumps entered from whatever state the previous landing left behind,
 * which is exactly the dimension that corpus holds fixed at its most favorable
 * value. Every field failure (sloppy arcs, launch-window dances, falls on the
 * 2-forward/1-up template gap) lives in that gap between the two.
 *
 * So: chains of 10-30 hops, every landing a SINGLE block, every hop drawn from
 * the maneuver library's validated reachable set, and headings that turn — 45,
 * 90, 135 degrees and reversals. Two properties make the result a trustworthy
 * signal rather than noise:
 *
 * - **Completable by construction.** Each hop is an offset the planner's move
 *   set or discovery fan already claims to serve, so a course that cannot be
 *   completed is a defect, never an unfair fixture.
 * - **Shortcut-shielded.** Non-consecutive pads are kept beyond discovery's
 *   reach ([SHORTCUT_CLEARANCE]), so the agent cannot skip a hop, and a
 *   recovery onto a later pad cannot launder a missed one.
 *
 * Falling is failure: pads float over a catch floor far below `minAllowedY`,
 * so the run terminates the moment the agent leaves the course.
 *
 * The [entrySweep] cases attack the same gap from the other side: one jump,
 * many *entry states* (run-up length, approach elbow), which is where the
 * "wastes motion aligning at the lip" behavior is reproducible on demand.
 */
object ParkourChainGenerator {
    private const val PREFIX = "chain-"
    private const val SWEEP_PREFIX = "entry-"

    private const val BASE_FEET_Y = 80
    private const val MIN_FEET_Y = 70
    private const val MAX_FEET_Y = 88

    /** Deterministic catch floor: any fall lands here, far under any pad. */
    private const val FLOOR_Y = 64

    /**
     * Shortcut shielding is a REACHABILITY question, not a distance one.
     *
     * A blanket separation radius cannot express it: a 90-degree turn between
     * two 3-block hops leaves the new pad only 4.2 blocks from the pad two
     * back, so any radius wide enough to shield that turn also forbids the
     * turn itself (measured: every generated course collapsed to a single
     * hop). What actually matters is whether the PLANNER could build an edge
     * across the skip — discovery reaches 4.3 blocks horizontally and only
     * spans rises in -3..+1 — so a pad may sit close to an earlier one as long
     * as no such edge can exist, typically because it is too high to ascend to.
     */
    private const val EDGE_REACH = 4.6
    private const val EDGE_MAX_ASCEND = 1
    private const val EDGE_MAX_DROP = 3

    /** How far off the desired heading a candidate hop may point. */
    private const val HEADING_TOLERANCE_DEGREES = 23.0

    private const val HOP_SAMPLE_ATTEMPTS = 48

    /**
     * Three families. Each isolates ONE thing the current engine requires of
     * the world, so a failure names its own cause instead of just saying
     * "parkour is unreliable".
     *
     * - **wide** — 3x3 pads, hops >= 4 blocks. The geometry the engine was
     *   built for: a landing broad enough to absorb a fast-entry carry. This
     *   one plans today, so it is the EXECUTOR gauge — arcs, landings and lip
     *   dithering over a long chain of turns.
     *
     * - **thin** — the same flat/descending chain on SINGLE-block pads. Fails
     *   today at plan time: a discovered jump is admitted only if it lands at
     *   both ends of the 0.12-0.30 entry band, and the fast anchor is allowed
     *   to carry one block past the landing. A 1x1 pad has nowhere to carry
     *   to, so the fast anchor falls and the edge is rejected — the whole
     *   route dies in `lazy_validation`. Admission demands a forgiving
     *   platform precisely BECAUSE entry speed is not planned.
     *
     * - **rise** — 1x1 pads with rising hops. Fails for a second, independent
     *   reason: `PathfinderManager.invalidMomentumApproach` deletes any rising
     *   jump >= 1.9 blocks whose route reaches the takeoff through a turn of
     *   more than ~15 degrees, unless the world supplies two straight stance
     *   blocks behind it. A single block never does.
     *
     * `thin` and `rise` are the target. They are expected to be red until
     * jump edges carry real entry contracts; they are the acceptance tests for
     * that work, not fixtures to be weakened until they pass.
     */
    /**
     * 1x1 is the target: real parkour lands on single blocks. `wide` exists
     * only as a CONTROL — it is the same generator with the pads inflated, so
     * that "the chain collapsed" can be attributed to pad forgiveness rather
     * than to anything else. Do not widen `thin`/`rise` to make them pass;
     * widening the pad is the crutch, not the fix.
     */
    private val THIN_COURSES = listOf(1L to 12, 2L to 16, 3L to 20, 4L to 26)
    private val RISE_COURSES = listOf(5L to 12, 6L to 16)
    private val WIDE_COURSES = listOf(1L to 12, 3L to 20)

    /** Wide pads must not swallow the gap: 3x3 pads need >= 4-block hops. */
    private const val WIDE_MIN_HOP = 4.0
    private const val THIN_MIN_HOP = 2.0

    /**
     * The jump classes, each an offset band the planner already claims.
     * Weights over-sample the two classes that fail in the field: the
     * cardinal 2-forward template gap (flat and, above all, the +1 rise —
     * `MoveTable.gapJump(rise = 1)`, the "one up two forward" that drops the
     * agent into the hole) and the discovered angled jumps.
     */
    private enum class JumpClass(val id: String, val weight: Int, val rising: Boolean = false) {
        TemplateGapFlat("tmpl-gap-flat", 3),
        TemplateGapRise("tmpl-gap-rise", 3, rising = true),
        DiscoveredFlat("disc-flat", 4),
        DiscoveredAscend("disc-ascend", 2, rising = true),
        DiscoveredDescend("disc-descend", 2),
    }

    private data class Hop(val dx: Int, val dy: Int, val dz: Int, val cls: JumpClass) {
        val distance get() = hypot(dx.toDouble(), dz.toDouble())
        val heading get() = atan2(dz.toDouble(), dx.toDouble())
    }

    private data class Pad(val x: Int, val feetY: Int, val z: Int, val halfWidth: Int = 0) {
        val landing get() =
            ParkourLanding(x - halfWidth, x + halfWidth, feetY, z - halfWidth, z + halfWidth)
    }

    /**
     * Every hop the move set / discovery fan can serve. Template gaps are
     * cardinal-only (`MoveTable.gapJump` stamps `2*dx, rise, 2*dz` over the
     * four cardinals); discovered jumps take any integer angle inside their
     * distance band, with rises restricted to discovery's `TAKEOFF_RISES`.
     */
    private val ALL_HOPS: List<Hop> = buildList {
        for (dx in -5..5) for (dz in -5..5) {
            val cardinal = dx == 0 || dz == 0
            val squared = dx * dx + dz * dz
            val distance = hypot(dx.toDouble(), dz.toDouble())
            if (squared == 4 && cardinal) {
                add(Hop(dx, 0, dz, JumpClass.TemplateGapFlat))
                add(Hop(dx, 1, dz, JumpClass.TemplateGapRise))
            }
            if (distance in 2.2..4.3) {
                add(Hop(dx, 0, dz, JumpClass.DiscoveredFlat))
                add(Hop(dx, -1, dz, JumpClass.DiscoveredDescend))
            }
            if (distance in 2.2..3.2) {
                add(Hop(dx, 1, dz, JumpClass.DiscoveredAscend))
            }
        }
    }

    private fun classLottery(allowRise: Boolean): List<JumpClass> = JumpClass.entries
        .filter { allowRise || !it.rising }
        .flatMap { cls -> List(cls.weight) { cls } }

    /** Turn applied to the running heading before each hop, in degrees. */
    private val TURN_LOTTERY: List<Double> = buildList {
        repeat(3) { add(0.0) }
        repeat(2) { add(45.0); add(-45.0) }
        repeat(2) { add(90.0); add(-90.0) }
        add(135.0); add(-135.0)
    }

    val chains: List<TraversalScenario> =
        THIN_COURSES.map { (seed, hops) ->
            chainCase("thin", seed, hops, allowRise = false, padHalfWidth = 0, minHop = THIN_MIN_HOP)
        } + RISE_COURSES.map { (seed, hops) ->
            chainCase("rise", seed, hops, allowRise = true, padHalfWidth = 0, minHop = THIN_MIN_HOP)
        } + WIDE_COURSES.map { (seed, hops) ->
            chainCase("wide", seed, hops, allowRise = false, padHalfWidth = 1, minHop = WIDE_MIN_HOP)
        }

    val entrySweep: List<TraversalScenario> = buildEntrySweep()

    val all: List<TraversalScenario> = chains + entrySweep

    // ------------------------------------------------------------------
    // Random-walk chains
    // ------------------------------------------------------------------

    private fun chainCase(
        family: String,
        seed: Long,
        requestedHops: Int,
        allowRise: Boolean,
        padHalfWidth: Int,
        minHop: Double,
    ): TraversalScenario {
        val random = Random(seed)
        val lottery = classLottery(allowRise)
        // The takeoff of the first hop: the runway's last block. Pads are the
        // landings, so the runway end is not itself a contract landing.
        val takeoff = Pad(0, BASE_FEET_Y, 0)
        var heading = 0.0
        val pads = ArrayList<Pad>(requestedHops)
        val classes = ArrayList<JumpClass>(requestedHops)

        // The runway extends BACKWARD from the takeoff (the course runs +X, so
        // the run-up runs -X) and is shielded like any other geometry: a pad
        // drifting back over it would let the agent walk off the course and
        // re-enter it further along, skipping hops.
        val runway = runwayBlocks(
            headingDegrees = 180.0,
            length = 6,
            endX = 0,
            endZ = 0,
            jumpDx = 1,
            jumpDz = 0,
        )

        while (pads.size < requestedHops) {
            val previous = pads.lastOrNull() ?: takeoff
            val hop = sampleHop(
                random, lottery, heading, previous, pads, takeoff, runway, padHalfWidth, minHop,
            ) ?: break
            pads += Pad(
                previous.x + hop.dx,
                previous.feetY + hop.dy,
                previous.z + hop.dz,
                padHalfWidth,
            )
            classes += hop.cls
            heading = hop.heading
        }

        val hopCount = pads.size
        val caseId = "$family-seed$seed-h$hopCount"
        val lowestFeet = pads.minOf(Pad::feetY).coerceAtMost(BASE_FEET_Y)
        val classSummary = classes.groupingBy(JumpClass::id).eachCount()
            .entries.sortedBy { it.key }.joinToString(",") { "${it.key}x${it.value}" }

        return TraversalScenario(
            name = PREFIX + caseId,
            purpose = "Seeded random-walk chain ($family), $hopCount single-block pads, " +
                "shortcut-shielded. Classes: $classSummary.",
            fixture = emptyList(),
            serverFixture = courseFixture(pads, runway, extraSupports = emptySet()),
            start = runwayStart(headingDegrees = 180.0, endX = 0, endZ = 0, feetY = BASE_FEET_Y, back = 5.0),
            startYaw = yawToward(pads.first().x - takeoff.x, pads.first().z - takeoff.z),
            goal = fastVectorOf(pads.last().x, pads.last().feetY, pads.last().z),
            // A hop plus its approach costs ~20-30 ticks; the allowance is
            // loose because a stall is caught by the contract, not the clock.
            timeoutTicks = 160 + 45 * hopCount,
            allowJump = true,
            // Diagnostic tier until the reliability work lands (repo
            // convention: promote to gated after consecutive green runs).
            gated = false,
            minAllowedY = lowestFeet - 1.5,
            parkourContract = ParkourContract(caseId, pads.map(Pad::landing)),
        )
    }

    /**
     * One hop: sample a class and a turn, then take the first offset of that
     * class pointing (within [HEADING_TOLERANCE_DEGREES]) along the turned
     * heading whose landing survives every shielding rule. Cardinal-only
     * classes simply find no candidate on a 45-degree heading and the next
     * attempt resamples — which is why angled headings self-select the
     * discovered classes without any special casing.
     */
    private fun sampleHop(
        random: Random,
        lottery: List<JumpClass>,
        heading: Double,
        previous: Pad,
        pads: List<Pad>,
        takeoff: Pad,
        runway: Set<BlockPos>,
        padHalfWidth: Int,
        minHop: Double,
    ): Hop? {
        repeat(HOP_SAMPLE_ATTEMPTS) {
            val cls = lottery.random(random)
            val turn = Math.toRadians(TURN_LOTTERY.random(random))
            val desired = heading + turn
            val candidates = ALL_HOPS.filter { hop ->
                hop.cls == cls && hop.distance >= minHop &&
                    angleBetween(hop.heading, desired) <= Math.toRadians(HEADING_TOLERANCE_DEGREES)
            }.shuffled(random)

            for (hop in candidates) {
                val landing = Pad(
                    previous.x + hop.dx,
                    previous.feetY + hop.dy,
                    previous.z + hop.dz,
                    padHalfWidth,
                )
                if (landing.feetY !in MIN_FEET_Y..MAX_FEET_Y) continue
                if (!shielded(landing, pads, takeoff, runway)) continue
                return hop
            }
        }
        return null
    }

    /**
     * A candidate landing is admissible only if the sole way to reach it is
     * the hop that produced it: beyond discovery's reach from every earlier
     * pad except its own takeoff, and from the runway the agent starts on.
     */
    private fun shielded(
        candidate: Pad,
        pads: List<Pad>,
        takeoff: Pad,
        runway: Set<BlockPos>,
    ): Boolean {
        val previous = pads.lastOrNull() ?: takeoff
        for (pad in pads + takeoff) {
            if (pad === previous) continue
            if (canReach(pad, candidate)) return false
        }
        // The runway is walkable ground: a pad the agent could jump to
        // straight off the run-up is a way into the course that skips
        // everything before it. Only the first pad may launch from there.
        if (pads.isNotEmpty()) {
            for (block in runway) {
                if (canReach(Pad(block.x, BASE_FEET_Y, block.z), candidate)) return false
            }
        }
        return true
    }

    /**
     * Could the planner build a direct edge from [from] to [to]? Measured
     * between the nearest blocks of the two pads, not their centers — a wide
     * pad reaches further than its center suggests.
     */
    private fun canReach(from: Pad, to: Pad): Boolean {
        val rise = to.feetY - from.feetY
        if (rise > EDGE_MAX_ASCEND || rise < -EDGE_MAX_DROP) return false
        val slack = (from.halfWidth + to.halfWidth).toDouble()
        return horizontal(from, to) - slack <= EDGE_REACH
    }

    private fun horizontal(a: Pad, b: Pad): Double =
        hypot((a.x - b.x).toDouble(), (a.z - b.z).toDouble())

    private fun angleBetween(a: Double, b: Double): Double {
        var delta = abs(a - b) % (2 * Math.PI)
        if (delta > Math.PI) delta = 2 * Math.PI - delta
        return delta
    }

    // ------------------------------------------------------------------
    // Entry-state sweep: one jump, many approaches
    // ------------------------------------------------------------------

    private data class SweepJump(val id: String, val dx: Int, val dy: Int, val dz: Int)

    private data class Approach(val id: String, val runUp: Int, val elbowDegrees: Double)

    /**
     * The jump classes that fail in production, crossed with the entry states
     * a real path delivers. A short run-up arrives below the speed the arc was
     * validated at; an elbow arrives fast but sideways to the jump line, which
     * is the state that produces the lip dance (the executor's own
     * `alignmentBlocked` branch) and the walk-off-without-jumping falls.
     */
    private fun buildEntrySweep(): List<TraversalScenario> {
        val jumps = listOf(
            // The reported failure: MoveTable.gapJump(rise = 1).
            SweepJump("gap2-up1", 2, 1, 0),
            SweepJump("gap2-flat", 2, 0, 0),
            SweepJump("gap3-flat", 3, 0, 0),
            SweepJump("gap4-flat", 4, 0, 0),
        )
        val approaches = listOf(
            Approach("run6", 6, 0.0),
            Approach("run2", 2, 0.0),
            Approach("elbow90", 5, 90.0),
            Approach("elbow45", 5, 45.0),
        )
        return jumps.flatMap { jump -> approaches.map { approach -> sweepCase(jump, approach) } }
    }

    private fun sweepCase(jump: SweepJump, approach: Approach): TraversalScenario {
        val takeoff = Pad(0, BASE_FEET_Y, 0)
        val landing = Pad(jump.dx, BASE_FEET_Y + jump.dy, jump.dz)
        val caseId = "${jump.id}-${approach.id}"

        // The takeoff block plus the run-up leg. With an elbow the leg runs in
        // from the side, so the agent must finish its turn onto the jump line
        // while it still has runway left — no pre-aimed spawn.
        val legHeading = 180.0 + approach.elbowDegrees
        // An elbow case must actually deny the straight run-up, or it tests
        // nothing: the leg's 3x3 patches otherwise happen to lay blocks right
        // behind the takeoff along the jump line, which satisfies
        // `hasPhysicalMomentumRunway` and lets the planner take the edge as if
        // the approach were straight (observed: the 45-degree case passing for
        // exactly this reason). Carving them out leaves the turn genuinely
        // unsupported — the state the field hits on a real platform corner.
        val runway = runwayBlocks(
            headingDegrees = legHeading,
            length = approach.runUp,
            endX = 0,
            endZ = 0,
            jumpDx = jump.dx,
            jumpDz = jump.dz,
        ) - if (approach.elbowDegrees != 0.0) straightRunUpBlocks(jump) else emptySet()
        val start = runwayStart(
            headingDegrees = legHeading,
            endX = 0,
            endZ = 0,
            feetY = BASE_FEET_Y,
            back = (approach.runUp - 1).coerceAtLeast(1).toDouble(),
        )

        return TraversalScenario(
            name = SWEEP_PREFIX + caseId,
            purpose = "Entry-state sweep: ${jump.id} from ${approach.id} " +
                "(run-up ${approach.runUp}, approach elbow ${approach.elbowDegrees}deg), single-block landing.",
            fixture = emptyList(),
            serverFixture = courseFixture(listOf(landing), runway, extraSupports = emptySet()),
            start = start,
            // Face along the run-up leg, NOT at the landing: acquiring the
            // jump line is the executor's job and part of what is measured.
            startYaw = yawToward(-directionX(legHeading), -directionZ(legHeading)),
            goal = fastVectorOf(landing.x, landing.feetY, landing.z),
            timeoutTicks = 220,
            allowJump = true,
            gated = false,
            minAllowedY = minOf(takeoff.feetY, landing.feetY) - 1.5,
            parkourContract = ParkourContract(caseId, listOf(landing.landing)),
        )
    }

    // ------------------------------------------------------------------
    // Fixture construction
    // ------------------------------------------------------------------

    /**
     * Clears the course's bounding box, lays a catch floor far below it, then
     * writes the runway and the single-block pads. The floor makes a fall
     * deterministic (and always below `minAllowedY`) instead of depending on
     * whatever terrain the world generated underneath.
     */
    private fun courseFixture(
        pads: List<Pad>,
        runway: Set<BlockPos>,
        extraSupports: Set<BlockPos>,
    ): (MinecraftServer) -> Unit = { server ->
        val world = server.overworld
        val padHalf = pads.maxOfOrNull(Pad::halfWidth) ?: 0
        val xs = pads.flatMap { listOf(it.x - padHalf, it.x + padHalf) } +
            runway.map(BlockPos::getX) + extraSupports.map(BlockPos::getX)
        val zs = pads.flatMap { listOf(it.z - padHalf, it.z + padHalf) } +
            runway.map(BlockPos::getZ) + extraSupports.map(BlockPos::getZ)
        val margin = 6
        val minX = xs.min() - margin
        val maxX = xs.max() + margin
        val minZ = zs.min() - margin
        val maxZ = zs.max() + margin
        val maxY = pads.maxOf(Pad::feetY).coerceAtLeast(BASE_FEET_Y) + 6

        for (x in minX..maxX) for (z in minZ..maxZ) {
            world.setBlockState(
                BlockPos(x, FLOOR_Y, z),
                Blocks.STONE.defaultState,
                Block.NOTIFY_LISTENERS,
            )
            for (y in FLOOR_Y + 1..maxY) {
                world.setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultState, Block.NOTIFY_LISTENERS)
            }
        }
        for (support in runway + extraSupports) {
            world.setBlockState(support, Blocks.SMOOTH_QUARTZ.defaultState, Block.NOTIFY_LISTENERS)
        }
        for (pad in pads) {
            for (px in pad.x - pad.halfWidth..pad.x + pad.halfWidth) {
                for (pz in pad.z - pad.halfWidth..pad.z + pad.halfWidth) {
                    world.setBlockState(
                        BlockPos(px, pad.feetY - 1, pz),
                        Blocks.QUARTZ_BLOCK.defaultState,
                        Block.NOTIFY_LISTENERS,
                    )
                }
            }
        }
    }

    /**
     * A run-up leg of [length] blocks ending at ([endX], [endZ]) — the takeoff
     * block — extending backwards along [headingDegrees].
     *
     * Each step stamps a 3x3 patch rather than a rasterized perpendicular
     * line: independently rounded samples along a 45-degree heading leave
     * diagonal pinholes, and a runway with holes in it is not a runway (the
     * planner finds no walkable route to the takeoff at all).
     *
     * The patch is then clipped at the takeoff along the jump direction
     * ([jumpDx], [jumpDz]): a block one past the lip would fill the very hole
     * the jump exists to cross, turning the case into a walk.
     */
    private fun runwayBlocks(
        headingDegrees: Double,
        length: Int,
        endX: Int,
        endZ: Int,
        jumpDx: Int,
        jumpDz: Int,
    ): Set<BlockPos> = buildSet {
        val ux = directionX(headingDegrees)
        val uz = directionZ(headingDegrees)
        for (step in 0..length) {
            val cx = Math.round(endX + ux * step).toInt()
            val cz = Math.round(endZ + uz * step).toInt()
            for (ox in -1..1) for (oz in -1..1) {
                val x = cx + ox
                val z = cz + oz
                // Beyond the takeoff lip, toward the landing: this is the gap.
                if ((x - endX) * jumpDx + (z - endZ) * jumpDz > 0) continue
                add(BlockPos(x, BASE_FEET_Y - 1, z))
            }
        }
    }

    /** The stance blocks directly behind a takeoff, along the jump line. */
    private fun straightRunUpBlocks(jump: SweepJump): Set<BlockPos> = buildSet {
        val length = hypot(jump.dx.toDouble(), jump.dz.toDouble())
        for (back in 1..3) {
            val x = -Math.round(jump.dx / length * back).toInt()
            val z = -Math.round(jump.dz / length * back).toInt()
            add(BlockPos(x, BASE_FEET_Y - 1, z))
        }
    }

    private fun runwayStart(
        headingDegrees: Double,
        endX: Int,
        endZ: Int,
        feetY: Int,
        back: Double,
    ): Vec3d = Vec3d(
        Math.round(endX + directionX(headingDegrees) * back).toDouble() + 0.5,
        feetY.toDouble(),
        Math.round(endZ + directionZ(headingDegrees) * back).toDouble() + 0.5,
    )

    /** Snapped: cos(90 deg) is 6e-17, and 6e-17 formats as "6.0E-17" — which
     *  is not a number the /tp command parser accepts. A malformed teleport
     *  fails silently and the scenario then runs from wherever the previous
     *  one ended (observed: an entry-sweep case that "started" on the previous
     *  case's landing pad). Every value that reaches a command string is
     *  rounded to a plain decimal. */
    private fun directionX(degrees: Double): Double = snap(cos(Math.toRadians(degrees)))

    private fun directionZ(degrees: Double): Double = snap(sin(Math.toRadians(degrees)))

    private fun snap(value: Double): Double = if (abs(value) < 1.0E-9) 0.0 else value

    private fun yawToward(dx: Number, dz: Number): Float {
        val degrees = Math.toDegrees(atan2(-dx.toDouble(), dz.toDouble()))
        return (Math.round(degrees * 100.0) / 100.0).toFloat()
    }

    // ------------------------------------------------------------------
    // Manifest
    // ------------------------------------------------------------------

    fun writeManifest(directory: File) {
        File(directory, "chain-manifest.json").writeText(
            all.joinToString(prefix = "[\n", postfix = "\n]\n", separator = ",\n") { scenario ->
                val contract = requireNotNull(scenario.parkourContract)
                buildString {
                    append("  {\"name\":\"").append(scenario.name).append("\"")
                    append(",\"caseId\":\"").append(contract.caseId).append("\"")
                    append(",\"purpose\":\"").append(scenario.purpose.replace("\"", "\\\"")).append("\"")
                    append(",\"hops\":").append(contract.landings.size)
                    append(",\"landings\":[")
                    append(contract.landings.joinToString(",") {
                        "{\"x\":[${it.minX},${it.maxX}],\"y\":${it.feetY},\"z\":[${it.minZ},${it.maxZ}]}"
                    })
                    append("]}")
                }
            },
        )
    }
}
