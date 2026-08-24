/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.PathingManager
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.movement.MotionConstraints
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

    private data class Record(
        val name: String,
        val status: String,
        val frames: Int,
        val collisions: Int,
        val jumps: Int,
        val publications: Int,
        val largestCommit: Int,
        val turningDegrees: Int,
    ) {
        val arrived: Boolean get() = status == "arrived"

        fun line(): String = "%-22s %-8s frames=%-4d collisions=%-3d jumps=%-3d pubs=%-3d maxCommit=%-3d turn=%d"
            .format(name, status, frames, collisions, jumps,
                publications, largestCommit, turningDegrees)
    }

    private fun noRoute(name: String) = Record(name, "no-route", 0, 0, 0, 0, 0, 0)

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
        var publications = 0
        var previousFrames = 0
        var largestCommit = 0
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, scenario.environment, CONFIG,
            cursorFrame = { clock.cursorFrame() },
            publish = { path, _ ->
                publications++
                largestCommit = maxOf(largestCommit, path.plan.tape.frameCount - previousFrames)
                previousFrames = path.plan.tape.frameCount
            },
            started = System.currentTimeMillis(),
            clock = clock,
        )

        val path = (outcome as? PathPlanResult.Planned)?.path
        val frames = path?.plan?.frames.orEmpty()
        return Record(
            name = scenario.name,
            status = if (path != null && !path.partial) "arrived" else "short",
            frames = frames.size,
            collisions = frames.count { it.state.horizontalCollision },
            jumps = frames.count { it.input.jump },
            publications = publications,
            largestCommit = largestCommit,
            turningDegrees = turning(path).toInt(),
        )
    }

    private fun turning(path: PathingManager.PublishedPath?): Double {
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
