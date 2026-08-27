/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.debug.ParkourCourseLayout
import com.lambda.pathing.launch.AirSteering
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.LaunchTrigger
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SegmentFollowerProgram
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.trajectory.TrajectoryRolloutEngine
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag

/**
 * Ground truth for a course the planner cannot finish: is each gap physically
 * crossable at all, from which entry speeds, by which solved launch?
 *
 * The attribution probe says where a course dies and with what diagnostic; this says
 * whether that death is the solver's (no certifying solution exists), the chain's (all
 * certifying solutions exit too fast for the next gap), or the search's (certifying,
 * chainable combinations exist that it never rolled). Brute force over
 * (solution x delay x entry speed) with a real rollout is the cheap oracle -- the same
 * method that established the original 4-block-gap fixtures were unfair.
 *
 * Purely a printed report, no gate.
 */
@Tag("bedrock-corpus")
class ParkourGapFeasibilityTest {
    @Test
    fun `course 4 gaps are individually certifiable ground truth report`() = gapTruth(4)

    @Test
    fun `course 2 gaps are individually certifiable ground truth report`() = gapTruth(2)

    private fun gapTruth(seed: Int) {
        val course = ParkourCourseLayout.course(jumps = 20, seed = seed)
        val environment = courseEnvironment(course)
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(maxJumpSpan = 3, maxJumpDrop = 2),
        )
        val planner = CoarsePlanner(environment, moves, course.start, course.goal)
        check(planner.repair(Duration.INFINITE).converged) { "course-$seed must have a coarse route" }
        val route = checkNotNull(planner.routePlan(0L))

        println("[gap-truth] pads: " + course.pads.joinToString { "(${it.x},${it.y},${it.z})" })
        route.nodes.forEachIndexed { index, node ->
            println("[gap-truth] route[$index] = (${node.x},${node.y},${node.z})")
        }

        for (target in 1 until route.nodes.size) {
            val from = route.nodes[target - 1]
            val to = route.nodes[target]
            val gap = hypot((to.x - from.x).toDouble(), (to.z - from.z).toDouble())
            if (gap < 1.5) continue

            val onward = route.nodes.getOrNull(target + 1)
            val onwardWindow = onward
                ?.takeIf { hypot((it.x - to.x).toDouble(), (it.z - to.z).toDouble()) >= 1.5 }
                ?.let { next ->
                    LaunchSolver.solve(to, next)
                        .takeIf { it.isNotEmpty() }
                        ?.let { solutions ->
                            val entry = solutions.minOf { it.speed - it.speedSlack }..
                                solutions.maxOf { it.speed + it.speedSlack }
                            BallisticProfile.VANILLA.groundReachable(entry, ticks = 3, sprint = true)
                        }
                }

            val solutions = LaunchSolver.solve(from, to, exitSpeedWindow = onwardWindow)
            var certified = 0
            var steeredCertified = 0
            var brokenBySteering = 0
            var rescuedBySteering = 0
            var chained = 0
            var tried = 0
            val examples = ArrayList<String>()
            for (solution in solutions) {
                for (delay in 0..4) {
                    for (entry in ENTRY_SPEEDS) {
                        tried++
                        val open = rollGap(environment, from, to, onward, solution.sprint, delay, entry,
                            solution.holdForward, solution.holdTicks, airPlan = null)
                        val steered = rollGap(environment, from, to, onward, solution.sprint, delay, entry,
                            solution.holdForward, solution.holdTicks, airPlan = airPlanFor(from, to, solution))
                        val openLanding = landingOf(open, delay, to)
                        val steeredLanding = landingOf(steered, delay, to)
                        if (openLanding != null) certified++
                        if (steeredLanding != null) steeredCertified++
                        if (openLanding != null && steeredLanding == null) brokenBySteering++
                        if (openLanding == null && steeredLanding != null) rescuedBySteering++
                        val landing = steeredLanding ?: openLanding ?: continue
                        val exitSpeed = landing.state.velocity.horizontalLength()
                        val fits = onwardWindow == null || exitSpeed in onwardWindow
                        if (fits) chained++
                        if (examples.size < 2 && fits) {
                            examples += "mode=${solution.mode} offset=%.1f delay=%d entry=%.2f exit=%.3f".format(
                                solution.launchOffset, delay, entry, exitSpeed,
                            )
                        }
                    }
                }
            }
            println(
                ("[gap-truth] edge=%-3d (%d,%d,%d)->(%d,%d,%d) gap=%.2f solutions=%d tried=%d open=%d " +
                    "steered=%d broken=%d rescued=%d chained=%d window=%s %s").format(
                    target, from.x, from.y, from.z, to.x, to.y, to.z, gap,
                    solutions.size, tried, certified, steeredCertified, brokenBySteering,
                    rescuedBySteering, chained,
                    onwardWindow?.let { "%.3f..%.3f".format(it.start, it.endInclusive) } ?: "-",
                    examples.joinToString(" | "),
                ),
            )
        }
    }

    private fun landingOf(
        rollout: com.lambda.pathing.trajectory.TrajectoryRollout,
        delay: Int,
        to: Stance,
    ) = rollout.frames.firstOrNull { frame ->
        frame.state.onGround && frame.index > delay &&
            Math.floor(frame.state.position.y).toInt() == to.y &&
            overlapsPad(frame.state.position, to)
    }

    private fun airPlanFor(from: Stance, to: Stance, solution: LaunchSolution): AirSteering.AirPlan {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val length = hypot(dx, dz)
        val unitX = dx / length
        val unitZ = dz / length
        return AirSteering.AirPlan(
            aimX = from.x + 0.5 + unitX * solution.aimDistance,
            aimZ = from.z + 0.5 + unitZ * solution.aimDistance,
            unitX = unitX,
            unitZ = unitZ,
            airTicks = solution.airTicks,
            holdTicks = if (solution.holdForward) solution.holdTicks else 0,
            sprintAcceleration = if (solution.sprint) BallisticProfile.SPRINT_AIR_ACCELERATION
                else BallisticProfile.WALK_AIR_ACCELERATION,
            walkAcceleration = BallisticProfile.WALK_AIR_ACCELERATION,
        )
    }

    private fun rollGap(
        environment: SnapshotSimulationEnvironment,
        from: Stance,
        to: Stance,
        onward: Stance?,
        sprint: Boolean,
        delay: Int,
        entrySpeed: Double,
        holdForward: Boolean,
        holdTicks: Int,
        airPlan: AirSteering.AirPlan?,
    ): com.lambda.pathing.trajectory.TrajectoryRollout {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val length = hypot(dx, dz)
        val yaw = Math.toDegrees(atan2(-dx, dz))
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(from.x + 0.5, from.y.toDouble(), from.z + 0.5),
            rotation = Rotation(yaw, 0.0),
            velocity = Vec3d(entrySpeed * dx / length, -0.0784, entrySpeed * dz / length),
            onGround = true,
        )
        val program = SegmentFollowerProgram(
            nodes = listOfNotNull(from, to, onward).map { it.center() },
            sprint = sprint,
            lookAheadNodes = 1,
            launch = LaunchTrigger(delay),
            maxYawChange = MotionConstraints().maxYawDegreesPerFrame,
            holdForwardInFlight = holdForward,
            holdTicks = holdTicks,
            airPlan = airPlan,
        )
        return TrajectoryRolloutEngine.rollout(initial, PROFILE, environment, program, frameCount = 40)
    }

    private fun overlapsPad(position: Vec3d, pad: Stance): Boolean =
        position.x > pad.x - 0.3 && position.x < pad.x + 1.3 &&
            position.z > pad.z - 0.3 && position.z < pad.z + 1.3

    private fun courseEnvironment(
        course: ParkourCourseLayout.Course,
    ): SnapshotSimulationEnvironment = SnapshotSimulationEnvironment.synthetic(
        bounds = SimulationSnapshotBounds(
            course.pads.minOf { it.x } - 4, 90, course.pads.minOf { it.z } - 6,
            course.pads.maxOf { it.x } + 4, 120, course.pads.maxOf { it.z } + 6,
        ),
        blocks = course.pads.associate {
            BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics(
                ParkourCourseLayout.PadShape.BLOCK.shape(), coarseVoxel = CoarseVoxel.FULL_BLOCK,
            )
        },
    )

    private companion object {
        val ENTRY_SPEEDS = doubleArrayOf(0.0, 0.06, 0.11, 0.15, 0.19, 0.23, 0.26)

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
