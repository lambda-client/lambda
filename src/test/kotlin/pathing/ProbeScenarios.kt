/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.debug.ParkourCourseLayout
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.trajectory.SearchExhaustion
import com.lambda.pathing.trajectory.VirtualSearchClock
import com.lambda.pathing.world.CoarseVoxel
import kotlin.math.atan2
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/** The shared corpus the planner probes measure against, and one way to plan over it. */
internal object ProbeScenarios {

    val PROFILE = PlayerPhysicsProfile(
        movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
        stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
        width = 0.6, height = 1.8, eyeHeight = 1.62,
    )

    class Scenario(
        val name: String,
        val environment: SnapshotSimulationEnvironment,
        val start: Stance,
        val goal: Stance,
        val options: SimpleMoveOptions,
    )

    class Outcome(
        val result: PathPlanResult,
        val exhaustions: List<SearchExhaustion>,
        /** Kept so a probe can drive the real search machinery over the same world. */
        val planner: CoarsePlanner,
        val route: CoarseRoutePlan,
        val catalog: MovementCatalog,
    )

    fun all(): List<Scenario> = buildList {
        for (seed in 1..4) {
            val course = ParkourCourseLayout.course(jumps = 20, seed = seed)
            add(
                Scenario(
                    "course-$seed", courseEnvironment(course), course.start, course.goal,
                    SimpleMoveOptions(maxJumpSpan = 3, maxJumpDrop = 2),
                )
            )
        }
        val surface = BedrockFieldLayout.standableSurface(BedrockFieldLayout.solidCells())
        val head = surface.filter { it.x <= 2 }.minByOrNull { it.z * it.z }!!
        val tail = surface.filter { it.x >= BedrockFieldLayout.LENGTH - 3 }.minByOrNull { it.z * it.z }!!
        add(
            Scenario(
                "bedrock-traverse", bedrockEnvironment(),
                Stance(head.x, head.y, head.z), Stance(tail.x, tail.y, tail.z),
                SimpleMoveOptions(maxJumpDrop = 2),
            )
        )
    }

    /**
     * The default-configuration plan for a scenario, computed once per JVM.
     *
     * Several probes want the same corpus planned the same way, and this task shares a
     * JVM with `HorizonWalkProbeTest`, which walks against a real wall clock: re-planning
     * per probe burned enough CPU to starve it into intermittent failure.
     */
    private val defaultPlans = HashMap<String, Outcome>()

    @Synchronized
    fun planned(scenario: Scenario): Outcome =
        defaultPlans.getOrPut(scenario.name) { plan(scenario) }

    /**
     * A two-scenario subset for comparative sweeps.
     *
     * A sweep reads relative differences between settings, and one parkour course plus the
     * long traverse separates them as well as five do. The corpus task shares a JVM with a
     * wall-clock test, so the other three are cost without signal.
     */
    fun sample(): List<Scenario> = all().filter { it.name == "course-2" || it.name == "bedrock-traverse" }

    fun plan(
        scenario: Scenario,
        parallelism: Int = 1,
        microsPerExpansion: Long = 640L,
        transitionOverheadTicks: Double = 1.0,
        maxTemperature: Double = 1.0,
        improvementBudget: Int = 0,
        frontierPerKey: Int = 3,
        beamShadowPerKey: Int = 0,
        branchExpansionHeadroomExpansions: Int = 1560,
        momentumSkips: Boolean = false,
        momentumGait: Boolean = false,
        guideWeight: Double = 1.0,
        tipLineCreditTicks: Double = 0.0,
        depthLaneInterval: Int = 0,
        mergeSurchargeTicks: Double = 0.0,
        frontierDomination: com.lambda.pathing.trajectory.FrontierDomination =
            com.lambda.pathing.trajectory.FrontierDomination.FULL,
        probe: com.lambda.pathing.trajectory.SearchProbe = com.lambda.pathing.trajectory.SearchProbe.NONE,
    ): Outcome {
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = transitionOverheadTicks),
            options = scenario.options,
        )
        val planner = CoarsePlanner(scenario.environment, moves, scenario.start, scenario.goal)
        check(planner.repair(Duration.INFINITE).converged) { "${scenario.name}: coarse search did not converge" }
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L)) { "${scenario.name}: no coarse route" }

        val dx = (scenario.goal.x - scenario.start.x).toDouble()
        val dz = (scenario.goal.z - scenario.start.z).toDouble()
        val clock = VirtualSearchClock(microsPerExpansion = microsPerExpansion)
        val executor = VirtualExecutor(clock)
        val exhaustions = ArrayList<SearchExhaustion>()
        val result = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(scenario.start.x + 0.5, scenario.start.y.toDouble(), scenario.start.z + 0.5),
                rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, scenario.environment, MotionConstraints(),
            cursorFrame = { executor.cursorFrame() },
            publish = { path, _ -> executor.offer(path) },
            started = System.currentTimeMillis(),
            clock = clock,
            adoptedSequence = executor::adoptedSequence,
            parallelism = parallelism,
            onExhaustion = { exhaustions += it },
            maxTemperature = maxTemperature,
            improvementBudget = improvementBudget,
            frontierPerKey = frontierPerKey,
            beamShadowPerKey = beamShadowPerKey,
            branchExpansionHeadroomExpansions = branchExpansionHeadroomExpansions,
            momentumSkips = momentumSkips,
            momentumGait = momentumGait,
            guideWeight = guideWeight,
            tipLineCreditTicks = tipLineCreditTicks,
            depthLaneInterval = depthLaneInterval,
            mergeSurchargeTicks = mergeSurchargeTicks,
            frontierDomination = frontierDomination,
            probe = probe,
            fieldExpansionBudget = Duration.INFINITE,
        )
        return Outcome(result, exhaustions, planner, route, moves.catalog)
    }

    private fun courseEnvironment(course: ParkourCourseLayout.Course): SnapshotSimulationEnvironment =
        SnapshotSimulationEnvironment.synthetic(
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

    /** The default-cost move library nearly every fixture builds; options vary per test. */
    fun moveLibrary(options: SimpleMoveOptions = SimpleMoveOptions()) = SimpleMoveLibrary.build(
        costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
        options = options,
    )

    fun bedrockEnvironment() = SnapshotSimulationEnvironment.synthetic(
        bounds = SimulationSnapshotBounds(
            -2, 56, -BedrockFieldLayout.HALF_WIDTH - 2,
            BedrockFieldLayout.LENGTH + 1, 71, BedrockFieldLayout.HALF_WIDTH + 2,
        ),
        blocks = BedrockFieldLayout.solidCells().associate {
            BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics.FULL_CUBE
        },
    )
}
