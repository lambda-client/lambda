/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.TrajectoryDiagnostic
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * The user's live failure: lone blocks on an alternating diagonal -- a 2x2 pocket of
 * air between each pair, so every hop is a (3,3)/(3,-3) diagonal jump. The coarse
 * route exists; the trajectory search reportedly dies. Non-gating report.
 */
@Tag("bedrock-corpus")
class DiagonalZigZagProbeTest {
    @Test
    fun `a diagonal zig zag of lone blocks walks end to end`() {
        val pads = listOf(
            Stance(0, 100, 0), Stance(3, 100, 3), Stance(6, 100, 0),
            Stance(9, 100, 3), Stance(12, 100, 0), Stance(15, 100, 3),
        )
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-4, 90, -4, 20, 112, 8),
            blocks = pads.associate { BlockPos(it.x, it.y - 1, it.z) to SnapshotBlockPhysics.FULL_CUBE },
        )
        val start = pads.first()
        val goal = pads.last()
        val moves = moveLibrary(SimpleMoveOptions())
        val planner = CoarsePlanner(environment, moves, start, goal)
        check(planner.repair(Duration.INFINITE).converged) { "coarse must converge" }
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) { "coarse route must exist" }
        println("[zigzag] route: " + route.nodes.joinToString("->") { "(${it.x},${it.y},${it.z})" })

        val dx = (goal.x - start.x).toDouble()
        val dz = (goal.z - start.z).toDouble()
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
            rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val clock = VirtualSearchClock()
        val executor = VirtualExecutor(clock)
        val tally = HashMap<String, Int>()
        val probe = object : SearchProbe {
            override fun expansion(from: Stance, action: TrajectoryDecision, diagnostic: TrajectoryDiagnostic?) {
                val step = action.step?.let { "(${it.x},${it.z})" } ?: "?"
                val detail = when (action) {
                    is TrajectoryDecision.Launch -> " delay=${action.delayFrames} offset=%.1f hold=%s".format(
                        action.solution?.launchOffset ?: -1.0,
                        action.solution?.holdTicks?.let { if (it == Int.MAX_VALUE) "all" else "$it" } ?: "?",
                    )
                    is TrajectoryDecision.Heading -> " yaw=%.0f delay=${action.delayFrames}".format(action.yaw)
                    else -> ""
                }
                val key = "${action.movement} ${from.x},${from.z}->$step$detail " +
                    (diagnostic?.let { it::class.simpleName + "@" + it.frame } ?: "ok")
                tally.merge(key, 1, Int::plus)
            }
        }
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, environment, MotionConstraints(),
            cursorFrame = { executor.cursorFrame() },
            publish = { path, _ -> executor.offer(path) },
            started = System.currentTimeMillis(),
            clock = clock,
            adoptedSequence = executor::adoptedSequence,
            probe = probe,
            onExhaustion = { println("[zigzag] $it") },
        )
        val path = (outcome as? PathPlanResult.Planned)?.path
        println("[zigzag] outcome=${outcome::class.simpleName} partial=${path?.partial} frames=${path?.plan?.frames?.size}")
        tally.entries.sortedByDescending { it.value }.take(80).forEach {
            println("[zigzag]   ${it.value}x ${it.key}")
        }
        kotlin.test.assertTrue(
            path != null && !path.partial,
            "the diagonal zig-zag must walk end to end: outcome=${outcome::class.simpleName}",
        )
    }

    @Test
    fun `the first zig zag edge in isolation`() {
        val from = Stance(0, 100, 0)
        val to = Stance(3, 100, 3)
        val onward = Stance(6, 100, 0)
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-4, 90, -4, 20, 112, 8),
            blocks = listOf(from, to, onward).associate {
                BlockPos(it.x, it.y - 1, it.z) to SnapshotBlockPhysics.FULL_CUBE
            },
        )
        val uninformed = com.lambda.pathing.launch.LaunchSolver.solve(from, to)
        val onwardSolutions = com.lambda.pathing.launch.LaunchSolver.solve(to, onward)
        val window = onwardSolutions.takeIf { it.isNotEmpty() }?.let { s ->
            com.lambda.pathing.launch.BallisticProfile.VANILLA.groundReachable(
                s.minOf { it.speed - it.speedSlack }..s.maxOf { it.speed + it.speedSlack },
                ticks = 3, sprint = true,
            )
        }
        val informed = com.lambda.pathing.launch.LaunchSolver.solve(from, to, exitSpeedWindow = window)
        println("[edge1] window=$window uninformed=${uninformed.size} informed=${informed.size}")
        for ((label, solutions) in listOf("uninformed" to uninformed, "informed" to informed)) {
            for (solution in solutions) {
                for (chain in listOf("short", "onward")) {
                    var landed = 0
                    for (delay in 0..8) {
                        val nodes = if (chain == "short") listOf(from, to) else listOf(from, to, onward)
                        val program = com.lambda.pathing.actions.SegmentFollowerProgram(
                            nodes = nodes.map { it.center() },
                            sprint = solution.sprint,
                            lookAheadNodes = 1,
                            launch = com.lambda.pathing.actions.LaunchTrigger(delay),
                            maxYawChange = MotionConstraints().maxYawDegreesPerFrame,
                            holdForwardInFlight = solution.holdForward,
                            holdTicks = solution.holdTicks,
                            airPlan = com.lambda.pathing.launch.AirSteering.AirPlan(
                                aimX = to.x + 0.5, aimZ = to.z + 0.5,
                                unitX = (to.x - from.x) / Math.hypot(3.0, 3.0),
                                unitZ = (to.z - from.z) / Math.hypot(3.0, 3.0),
                                airTicks = solution.airTicks,
                                holdTicks = if (solution.holdForward) solution.holdTicks else 0,
                                sprintAcceleration = com.lambda.pathing.launch.BallisticProfile.SPRINT_AIR_ACCELERATION,
                                walkAcceleration = com.lambda.pathing.launch.BallisticProfile.WALK_AIR_ACCELERATION,
                            ),
                        )
                        val initial = MovementSimulationState.synthetic(
                            profile = PROFILE,
                            position = Vec3d(from.x + 0.5, from.y.toDouble(), from.z + 0.5),
                            rotation = Rotation(Math.toDegrees(atan2(-3.0, 3.0)), 0.0),
                            velocity = Vec3d(0.0, -0.0784, 0.0),
                            onGround = true,
                        )
                        val rollout = com.lambda.pathing.search.TrajectoryRolloutEngine.rollout(
                            initial, PROFILE, environment, program, frameCount = 40,
                        )
                        val landing = rollout.frames.firstOrNull { frame ->
                            frame.state.onGround && frame.index > delay &&
                                Math.floor(frame.state.position.y).toInt() == to.y &&
                                frame.state.position.x > to.x - 0.3 && frame.state.position.x < to.x + 1.3 &&
                                frame.state.position.z > to.z - 0.3 && frame.state.position.z < to.z + 1.3
                        }
                        if (landing != null) landed++
                    }
                    println("[edge1] $label mode=${solution.mode} offset=%.1f hold=%s chain=%s -> landed=%d/9".format(
                        solution.launchOffset,
                        if (solution.holdTicks == Int.MAX_VALUE) "all" else solution.holdTicks.toString(),
                        chain, landed,
                    ))
                }
            }
        }
    }

    @Test
    fun `trace the zag attempt frame by frame`() {
        val pads = listOf(
            Stance(0, 100, 0), Stance(3, 100, 3), Stance(6, 100, 0),
            Stance(9, 100, 3), Stance(12, 100, 0), Stance(15, 100, 3),
        )
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-4, 90, -4, 20, 112, 8),
            blocks = pads.associate { BlockPos(it.x, it.y - 1, it.z) to SnapshotBlockPhysics.FULL_CUBE },
        )
        val from = Stance(3, 100, 3)
        val to = Stance(6, 100, 0)
        val solution = com.lambda.pathing.launch.LaunchSolver.solve(from, to)
            .first { it.launchOffset < 0.2 }
        println("[trace] solution off=%.1f speed=%.3f slack=%.3f air=%d aim=%.2f".format(
            solution.launchOffset, solution.speed, solution.speedSlack, solution.airTicks, solution.aimDistance,
        ))
        for (delay in listOf(1, 3)) {
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(3.4836449373873632, 100.0, 3.057160053058826),
                rotation = Rotation(-127.64854354523072, 0.0),
                velocity = Vec3d(0.16586622906921053, -0.0784000015258789, 0.08150566402397656),
                onGround = true,
            )
            val program = com.lambda.pathing.actions.SegmentFollowerProgram(
                nodes = pads.drop(1).map { it.center() },
                sprint = solution.sprint,
                lookAheadNodes = 1,
                launch = com.lambda.pathing.actions.LaunchTrigger(delay),
                maxYawChange = MotionConstraints().maxYawDegreesPerFrame,
                holdForwardInFlight = solution.holdForward,
                holdTicks = solution.holdTicks,
                airPlan = com.lambda.pathing.launch.AirSteering.AirPlan(
                    aimX = to.x + 0.5, aimZ = to.z + 0.5,
                    unitX = (to.x - from.x) / Math.hypot(3.0, 3.0),
                    unitZ = (to.z - from.z) / Math.hypot(3.0, 3.0),
                    airTicks = solution.airTicks,
                    holdTicks = if (solution.holdForward) solution.holdTicks else 0,
                    sprintAcceleration = com.lambda.pathing.launch.BallisticProfile.SPRINT_AIR_ACCELERATION,
                    walkAcceleration = com.lambda.pathing.launch.BallisticProfile.WALK_AIR_ACCELERATION,
                ),
            )
            val rollout = com.lambda.pathing.search.TrajectoryRolloutEngine.rollout(
                initial, PROFILE, environment, program, frameCount = 18,
            )
            println("[trace] delay=$delay")
            rollout.frames.forEach { f ->
                println("[trace]   #%d pos=(%.3f,%.2f,%.3f) v=(%.3f,%.3f) yaw=%.0f f=%.0f s=%.0f j=%b g=%b".format(
                    f.index, f.state.position.x, f.state.position.y, f.state.position.z,
                    f.state.velocity.x, f.state.velocity.z, f.state.rotation.yaw,
                    f.input.forward, f.input.strafe, f.input.jump, f.state.onGround,
                ))
            }
        }
    }

    @Test
    fun `trace the drop staircase tape`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in 2 downTo 0) for (z in -1..1) blocks[BlockPos(x, 16, z)] = SnapshotBlockPhysics.FULL_CUBE
        for (i in 1..3) blocks[BlockPos(-i, 16 - i * 2, 0)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in -4 downTo -7) for (z in -1..1) blocks[BlockPos(x, 10, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-12, 0, -8, 8, 40, 8), blocks,
        )
        val moves = moveLibrary(SimpleMoveOptions())
        val planner = CoarsePlanner(environment, moves, Stance(0, 17, 0), Stance(-5, 11, 0))
        check(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L))
        println("[droptrace] route: " + route.nodes.joinToString("->") { "(${it.x},${it.y},${it.z})" })
        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 17.0, 0.5),
                rotation = Rotation(90.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )
        val frames = (outcome as? PathPlanResult.Planned)?.path?.plan?.frames ?: return println("[droptrace] $outcome")
        frames.forEach { f ->
            println("[droptrace] #%d pos=(%.3f,%.2f,%.3f) z-drift=%.3f g=%b j=%b f=%.0f s=%.0f".format(
                f.index, f.state.position.x, f.state.position.y, f.state.position.z,
                Math.abs(f.state.position.z - 0.5), f.state.onGround, f.input.jump,
                f.input.forward, f.input.strafe,
            ))
        }
    }

}
