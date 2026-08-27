/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.PathingManager
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.debug.ParkourCourseLayout
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.trajectory.PublishedPath
import com.lambda.pathing.trajectory.SearchProbe
import com.lambda.pathing.trajectory.SimulatedTrajectoryFrame
import com.lambda.pathing.trajectory.TrajectoryDiagnostic
import com.lambda.pathing.trajectory.VirtualSearchClock
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag

@Tag("bedrock-corpus")
class HorizonBaselineTest {
    @Test
    fun `the shipping horizon planner matches its recorded baseline`() {
        val measured = scenarios().map { scenario -> walk(scenario) }
        val rendered = measured.joinToString("\n") { it.line() } + "\n"

        if (System.getProperty("lambda.pathing.rebaseline") == "true") {
            Files.createDirectories(BASELINE.parent)
            Files.writeString(BASELINE, HEADER + rendered)
            println("[baseline] rewrote ${measured.size} scenarios to $BASELINE")
            measured.forEach { println("[baseline] ${it.line()}") }
            return
        }

        check(Files.exists(BASELINE)) {
            "no baseline at $BASELINE -- record one with -Prebaseline=true"
        }
        val expected = Files.readAllLines(BASELINE)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { it.substringBefore(' ') to it }

        val drift = ArrayList<String>()
        measured.forEach { record ->
            val was = expected[record.name]
            if (was == null) {
                drift += "  NEW      ${record.line()}"
            } else if (was != record.line()) {
                drift += "  was      $was"
                drift += "  is       ${record.line()}"
            }
        }
        (expected.keys - measured.mapTo(HashSet()) { it.name }).forEach {
            drift += "  MISSING  ${expected[it]}"
        }

        measured.forEach { println("[baseline] ${it.line()}") }
        println("[baseline] total %d frames over %d scenarios, %d arrived".format(
            measured.sumOf { it.frames }, measured.size, measured.count { it.arrived },
        ))
        check(drift.isEmpty()) {
            "the shipping planner moved away from its baseline:\n" + drift.joinToString("\n") +
                "\nIf the change was intended, re-record with -Prebaseline=true."
        }
    }

    /**
     * Holdout courses the baseline never gates on. The four recorded seeds are what the
     * planner is tuned against; a change that helps them and hurts these has overfit.
     * Purely a printed report -- arrival counts here inform review, not CI.
     */
    @Test
    fun `holdout parkour seeds report without gating`() {
        val records = (5..12).map { seed ->
            val course = ParkourCourseLayout.course(jumps = 20, seed = seed)
            walk(Scenario(
                "parkour-holdout-%d".format(seed),
                courseEnvironment(course),
                SimpleMoveOptions(maxJumpSpan = 3, maxJumpDrop = 2),
                course.start, course.goal,
            ))
        }
        records.forEach { println("[holdout] ${it.line()}") }
        println("[holdout] %d/%d arrived".format(records.count { it.arrived }, records.size))
    }

    private data class Record(
        val name: String,
        val status: String,
        val frames: Int,
        val collisions: Int,
        val jumps: Int,
        val publications: Int,
        val adoptions: Int,
        val refusals: Int,
        val firstPublishFrame: Int,
        val largestCommit: Int,
        val turningDegrees: Int,
        val stalledFrames: Int,
        val excessPercent: Int,
        val routeReach: String,
    ) {
        val arrived: Boolean get() = status == "arrived"

        fun line(): String =
            ("%-22s %-8s reach=%-7s frames=%-4d excess=%-4s collisions=%-3d jumps=%-3d " +
                "pubs=%-3d adopt=%-3d refuse=%-3d firstPub=%-4d maxCommit=%-3d turn=%-4d stalled=%d")
                .format(name, status, routeReach, frames, "$excessPercent%", collisions, jumps,
                    publications, adoptions, refusals, firstPublishFrame,
                    largestCommit, turningDegrees, stalledFrames)
    }

    private fun noRoute(name: String) = Record(
        name = name, status = "no-route", frames = 0, collisions = 0, jumps = 0,
        publications = 0, adoptions = 0, refusals = 0, firstPublishFrame = -1,
        largestCommit = 0, turningDegrees = 0, stalledFrames = 0, excessPercent = 0,
        routeReach = "-",
    )

    private class Scenario(
        val name: String,
        val environment: SnapshotSimulationEnvironment,
        val options: SimpleMoveOptions,
        val start: Stance,
        val goal: Stance,
        val yaw: Double? = null,
    )

    private fun walk(scenario: Scenario): Record {
        println("[baseline] running ${scenario.name}")
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = scenario.options,
        )
        val start = scenario.start
        val goal = scenario.goal
        val planner = CoarsePlanner(
            scenario.environment, moves, start, goal,
        )
        if (!planner.repair(Duration.INFINITE).converged) return noRoute(scenario.name)
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = planner.routePlan(0L) ?: return noRoute(scenario.name)

        val dx = (goal.x - start.x).toDouble()
        val dz = (goal.z - start.z).toDouble()
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
            rotation = Rotation(scenario.yaw ?: Math.toDegrees(atan2(-dx, dz)), 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )

        val clock = VirtualSearchClock()
        // The half the harness used to fake: a body that adopts with latency and can
        // refuse. Without it the corpus modelled instant universal acknowledgement and
        // scored a 3% stall rate against ~75% in game.
        val executor = VirtualExecutor(clock)
        var publications = 0
        var previousFrames = 0
        var largestCommit = 0
        var firstPublishFrame = -1
        // How far along the coarse route anything was ever certified. A course that dies
        // at gap one and one that dies at gap fifteen are different failures, and only
        // this tells them apart while the status stays `short`.
        var deepestReach = 0
        val attribution = EdgeAttributionProbe(route.nodes)
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, scenario.environment, CONFIG,
            cursorFrame = { executor.cursorFrame() },
            publish = { path, _ ->
                publications++
                if (firstPublishFrame < 0) firstPublishFrame = clock.cursorFrame()
                deepestReach = maxOf(deepestReach, path.route.nodes.indexOf(path.safeAnchorStance))
                largestCommit = maxOf(largestCommit, path.plan.tape.frameCount - previousFrames)
                previousFrames = path.plan.tape.frameCount
                executor.offer(path)
            },
            started = System.currentTimeMillis(),
            clock = clock,
            adoptedSequence = executor::adoptedSequence,
            probe = attribution,
        )

        val path = (outcome as? PathPlanResult.Planned)?.path
        path?.let { deepestReach = maxOf(deepestReach, it.route.nodes.indexOf(it.safeAnchorStance)) }
        println("[baseline] ${scenario.name} attempts=${path?.attempts ?: 0}")
        val record = Record(
            name = scenario.name,
            status = if (path != null && !path.partial) "arrived" else "short",
            frames = path?.plan?.frames.orEmpty().size,
            collisions = path?.plan?.frames.orEmpty().count { it.state.horizontalCollision },
            jumps = path?.plan?.frames.orEmpty().count { it.input.jump },
            publications = publications,
            adoptions = executor.adoptions,
            refusals = executor.refusals,
            firstPublishFrame = firstPublishFrame,
            largestCommit = largestCommit,
            turningDegrees = turning(path).toInt(),
            stalledFrames = stalled(path?.plan?.frames.orEmpty()),
            excessPercent = excess(path),
            routeReach = "$deepestReach/${route.nodes.lastIndex}",
        )
        attribution.spineLines.forEach { println("[spine] ${scenario.name}: $it") }
        if (scenario.name.startsWith("parkour-course") || scenario.name.startsWith("parkour-holdout") ||
            !record.arrived
        ) {
            attribution.report(scenario.name).forEach(::println)
        }
        return record
    }

    /**
     * Attributes search work to coarse route edges, keyed by the index of the decision's
     * target node ("off" for corridor steps outside the route). The number that matters
     * is the byte-identical repeat count of the worst diagnostic: a healthy search fails
     * an edge a few different ways while learning it, a grinding one repeats the exact
     * same rejection thousands of times.
     */
    private class EdgeAttributionProbe(route: List<Stance>) : SearchProbe {
        private val indexByNode: Map<Stance, Int> =
            route.withIndex().associate { (index, node) -> node to index }

        private class EdgeStats {
            var attempts = 0
            var rejections = 0
            val diagnostics = HashMap<String, Int>()
        }

        private val edges = HashMap<Int, EdgeStats>()

        val spineLines = ArrayList<String>()

        override fun spine(
            reachedElapsed: Int,
            rollouts: Int,
            stalledAt: Stance?,
            diagnostic: TrajectoryDiagnostic?,
        ) {
            val at = stalledAt?.let { indexByNode[it]?.toString() ?: "(${it.x},${it.y},${it.z})" } ?: "finish"
            spineLines += "spine reached=${reachedElapsed}f rollouts=$rollouts stalled=$at" +
                (diagnostic?.let { " $it" } ?: "")
        }

        override fun expansion(
            from: Stance,
            action: TrajectoryDecision,
            diagnostic: TrajectoryDiagnostic?,
        ) {
            val index = action.step?.let { indexByNode[it] } ?: -1
            val stats = edges.getOrPut(index) { EdgeStats() }
            stats.attempts++
            if (diagnostic != null) {
                stats.rejections++
                stats.diagnostics.merge(diagnostic.toString(), 1, Int::plus)
            }
        }

        fun report(name: String): List<String> = edges.entries
            .sortedBy { it.key }
            .map { (index, stats) ->
                val worst = stats.diagnostics.maxByOrNull { it.value }
                "[attribution] %-18s edge=%-4s attempts=%-6d rejected=%-6d distinct=%-3d worst x%d: %s".format(
                    name,
                    if (index < 0) "off" else index.toString(),
                    stats.attempts,
                    stats.rejections,
                    stats.diagnostics.size,
                    worst?.value ?: 0,
                    worst?.key ?: "-",
                )
            }
    }

    /**
     * How far the tape exceeds what the route could not have avoided, as a percentage.
     *
     * `CoarseRoutePlan.lowerBoundTicks` is admissible -- no body crosses that route in
     * fewer ticks -- which makes it the only denominator in the system. Raw frame counts
     * only ever said whether a change moved the number, never whether the number was any
     * good; measured this way the corpus turns out to sit within a few percent of optimal,
     * so a change that moves frames by five percent is moving 1.02x to 1.07x rather than
     * fixing anything.
     */
    private fun excess(path: PublishedPath?): Int {
        if (path == null) return 0
        val ratio = path.excessRatio
        return if (ratio.isFinite()) ((ratio - 1.0) * 100).toInt() else 0
    }

    /**
     * Frames the body spends standing still *inside* the tape, not at its end.
     *
     * Every published tape ends in a certified brake so an unextended one is safe to
     * replay. When the search fails to publish an extension before the body reaches that
     * brake, the body stops -- and because the search then re-roots onto the brake
     * anchor, the deceleration and hold are welded into the prefix of every tape that
     * follows. So a stop in the middle of a finished tape is a recording of a moment the
     * planner could not keep up, and it is the metric that says whether it did.
     *
     * The trailing stop is excluded: arriving at the goal and stopping is the point.
     */
    private fun stalled(frames: List<SimulatedTrajectoryFrame>): Int {
        if (frames.isEmpty()) return 0
        val stopped = MotionConstraints().stoppedSpeed
        val still = frames.map { it.state.velocity.horizontalLength() <= stopped }
        var total = 0
        var index = 0
        while (index < still.size) {
            if (!still[index]) { index++; continue }
            var end = index
            while (end < still.size && still[end]) end++
            // A run that reaches the last frame is the arrival stop, not a stall.
            if (end - index >= MIN_STALL_FRAMES && end < still.size) total += end - index
            index = end
        }
        return total
    }

    private fun turning(path: PublishedPath?): Double {
        val frames = path?.plan?.frames ?: return 0.0
        var total = 0.0
        frames.zipWithNext { a, b -> total += abs(Rotation.wrap(b.state.rotation.yaw - a.state.rotation.yaw)) }
        return total
    }

    private fun scenarios(): List<Scenario> = buildList {
        val bedrock = bedrockEnvironment()
        val bedrockOptions = SimpleMoveOptions(maxJumpDrop = 2)

        BedrockFieldLayout.randomEndpointPairs(count = 12).forEachIndexed { index, (from, to) ->
            add(Scenario(
                "bedrock-%02d".format(index), bedrock, bedrockOptions,
                Stance(from.x, from.y, from.z), Stance(to.x, to.y, to.z),
            ))
        }

        val surface = BedrockFieldLayout.standableSurface(BedrockFieldLayout.solidCells())
        val head = checkNotNull(surface.filter { it.x <= 2 }.minByOrNull { it.z * it.z })
        val tail = checkNotNull(
            surface.filter { it.x >= BedrockFieldLayout.LENGTH - 3 }.minByOrNull { it.z * it.z }
        )
        add(Scenario(
            "bedrock-traverse", bedrock, bedrockOptions,
            Stance(head.x, head.y, head.z), Stance(tail.x, tail.y, tail.z),
        ))

        val flat = flatEnvironment()
        listOf(
            "flat-straight" to Stance(0, 100, 14),
            "flat-diagonal" to Stance(10, 100, 10),
            "flat-shallow" to Stance(14, 100, 5),
        ).forEach { (name, goal) ->
            add(Scenario(name, flat, SimpleMoveOptions(maxJumpDrop = 2), Stance(0, 100, 0), goal))
        }

        add(Scenario(
            "parkour-chain", parkourEnvironment(), SimpleMoveOptions(),
            Stance(0, 100, 0), Stance(3, 102, 3), yaw = -90.0,
        ))

        add(Scenario(
            "parkour-chain-offaxis", parkourEnvironment(), SimpleMoveOptions(),
            Stance(0, 100, 0), Stance(3, 102, 3),
        ))

        // Seeded parkour courses of isolated one-block pads.
        //
        // The corpus had nothing of this shape: bedrock is open terrain where a jump that
        // lands slightly off still lands on something, and the two parkour fixtures are
        // pillar pairs that finish in 36 frames. A body that cannot hit a 1x1 target
        // scored perfectly well on all of it. These courses are 20 gaps long, deterministic
        // from their seed, and every gap is drawn from what a sprint jump provably clears,
        // so a failure here is the planner's and not the terrain's.
        for (seed in 1..4) {
            val course = ParkourCourseLayout.course(jumps = 20, seed = seed)
            add(Scenario(
                "parkour-course-%d".format(seed),
                courseEnvironment(course),
                // Capped at the pad chain's own reach. Left at the default of five the
                // coarse graph routes diagonally *across* pads -- a 4.5-block shortcut it
                // can see and no body can jump -- so the fixture would be measuring the
                // planner against gaps that are not there.
                SimpleMoveOptions(maxJumpSpan = 3, maxJumpDrop = 2),
                course.start, course.goal,
            ))
        }

        add(Scenario(
            "staircase-over-both", staircaseEnvironment(),
            SimpleMoveOptions(allowDiagonal = true, maxWalkOffDepth = 3),
            Stance(0, 1, 0), Stance(0, 1, 22),
        ))
    }

    private fun bedrockEnvironment() = SnapshotSimulationEnvironment.synthetic(
        bounds = SimulationSnapshotBounds(
            -2, 56, -BedrockFieldLayout.HALF_WIDTH - 2,
            BedrockFieldLayout.LENGTH + 1, 71, BedrockFieldLayout.HALF_WIDTH + 2,
        ),
        blocks = BedrockFieldLayout.solidCells().associate {
            BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics.FULL_CUBE
        },
    )

    private fun flatEnvironment(): SnapshotSimulationEnvironment {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -20..20) for (z in -20..20) blocks[BlockPos(x, 99, z)] = SnapshotBlockPhysics.FULL_CUBE
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-24, 92, -24, 24, 112, 24), blocks,
        )
    }

    private fun parkourEnvironment(): SnapshotSimulationEnvironment {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -8..0) for (z in -1..1) blocks[BlockPos(x, 99, z)] = SnapshotBlockPhysics.FULL_CUBE
        blocks[BlockPos(3, 100, 0)] = SnapshotBlockPhysics.FULL_CUBE
        blocks[BlockPos(3, 101, 3)] = SnapshotBlockPhysics.FULL_CUBE
        return SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-10, 90, -10, 20, 112, 12), blocks = blocks,
        )
    }

    private fun courseEnvironment(
        course: ParkourCourseLayout.Course,
        pad: ParkourCourseLayout.PadShape = ParkourCourseLayout.PadShape.BLOCK,
    ): SnapshotSimulationEnvironment = SnapshotSimulationEnvironment.synthetic(
        bounds = SimulationSnapshotBounds(
            course.pads.minOf { it.x } - 4, 90, course.pads.minOf { it.z } - 6,
            course.pads.maxOf { it.x } + 4, 120, course.pads.maxOf { it.z } + 6,
        ),
        blocks = course.pads.associate {
            BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics(
                pad.shape(), coarseVoxel = CoarseVoxel.FULL_BLOCK,
            )
        },
    )

    private fun staircaseEnvironment(): SnapshotSimulationEnvironment {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -8..8) for (z in -8..8) if (z < 3) blocks[BlockPos(x, 0, z)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in -2..2) {
            for (z in 3..4) blocks[BlockPos(x, 1, z)] = SnapshotBlockPhysics.FULL_CUBE
            for (z in 5..6) blocks[BlockPos(x, 2, z)] = SnapshotBlockPhysics.FULL_CUBE
            for (z in 7..8) blocks[BlockPos(x, 3, z)] = SnapshotBlockPhysics.FULL_CUBE
            for (z in 11..14) blocks[BlockPos(x, 3, z)] = SnapshotBlockPhysics.FULL_CUBE
            for (z in 15..16) blocks[BlockPos(x, 2, z)] = SnapshotBlockPhysics.FULL_CUBE
        }
        for (x in -2..2) for (z in 17..24) blocks[BlockPos(x, 0, z)] = SnapshotBlockPhysics.FULL_CUBE
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-12, -7, -12, 12, 9, 28), blocks,
        )
    }

    private companion object {
        /** Shortest run of stationary frames that counts as a stop rather than a slow turn. */
        const val MIN_STALL_FRAMES = 4

        val BASELINE: Path = Path.of("src/test/resources/pathing/horizon-baseline.txt")

        val HEADER = """
            # Characterisation of the shipping horizon planner: PathingManager -> planAsync
            # -> walkHorizon -> ValueFieldAnchorSearch, driven by a VirtualSearchClock so the
            # search-versus-body race resolves identically on every machine.
            #
            # These numbers are a record of current behaviour, not a statement that it is good.
            # Re-record with -Prebaseline=true when a change is meant to move them.
            #
        """.trimIndent() + "\n"

        val CONFIG = MotionConstraints()

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
