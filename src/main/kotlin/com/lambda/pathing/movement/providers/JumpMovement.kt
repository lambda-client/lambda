package com.lambda.pathing.movement.providers

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.alongEdge
import com.lambda.pathing.core.center
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.movement.CoarseMoveRates
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.movement.MotionTemplate
import com.lambda.pathing.launch.AirSteering
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.launch.HorizontalDynamics
import com.lambda.pathing.movement.*
import kotlin.math.abs
import kotlin.math.hypot

object JumpMovement : Movement {
    override val id = MovementId.JUMP

    override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
        val options = context.options
        if (!options.allowJumpCandidates) return@buildList

        val reach = options.maxJumpSpan
        for (rise in -options.maxJumpDrop..LaunchMode.MAX_JUMP_RISE) {
            for (dx in -reach..reach) {
                for (dz in -reach..reach) {
                    if (!offered(dx, dz, rise, options)) continue
                    add(spec(dx, dz, rise, context.costs.jumpCandidateCost(hypot(dx, dz), rise)))
                }
            }
        }
    }

    /**
     * A jump candidate must be provably reachable from a standing start on its own
     * block, because the coarse graph cannot promise a run-up exists at the launch --
     * a route through a jump that needs carried momentum is a route the trajectory
     * layer may be unable to honour, and the coarse graph is supposed to be a lower
     * bound the search solves every time.
     *
     * The distance that matters is the AIR GAP, corner to corner: the body launches
     * from the lip nearest the target and catches the landing at its nearest corner,
     * so a (4,2) cell jump is not 4.47 blocks of flight but the diagonal of the 3x1
     * air rectangle between the blocks, 3.16. The old gate compared cell-centre
     * distance against `hypot(maxJumpSpan, 1)`, which granted exactly one block of
     * lateral offset at full span and silently dropped real jumps like (4,2) while
     * admitting momentum-only ones like straight span-5.
     *
     * The ceilings are rollout-measured, not derived (OffAxisJumpProbeTest, standing
     * start at pad centre, every solution x delay): flat and dropping jumps certify
     * solidly up to a 3.16 air gap and only 1-in-9 at 4.0; rising jumps stop at 2.83.
     * [SimpleMoveOptions.maxJumpSpan] remains the user-facing cap, now per axis.
     */
    private fun offered(dx: Int, dz: Int, rise: Int, options: SimpleMoveOptions): Boolean {
        val distance = hypot(dx, dz)
        if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE) return false
        if (dx != 0 && dz != 0 && !options.allowDiagonal) return false
        if (abs(dx) != abs(dz) && dx != 0 && dz != 0 && !options.allowOffAxisJumps) return false
        if (maxOf(abs(dx), abs(dz)) > options.maxJumpSpan) return false

        val airGap = kotlin.math.hypot(
            (abs(dx) - 1).coerceAtLeast(0).toDouble(),
            (abs(dz) - 1).coerceAtLeast(0).toDouble(),
        )
        val ceiling = if (rise > 0) RISING_STANDING_AIR_GAP_BLOCKS else STANDING_AIR_GAP_BLOCKS
        return airGap <= ceiling + REACH_EPSILON
    }

    private const val REACH_EPSILON = 1e-9

    /** Air gap a standing start clears on flat and dropping jumps: hypot(3, 1). */
    private val STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 1.0)

    /** Rising jumps trade reach for the block of height: hypot(2, 2). */
    private val RISING_STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(2.0, 2.0)

    private fun hypot(dx: Int, dz: Int): Double = kotlin.math.hypot(dx.toDouble(), dz.toDouble())

    private fun spec(dx: Int, dz: Int, rise: Int, cost: Double) = TemplateSpec(
        dx = dx, dy = rise, dz = dz,
        movement = id,
        cost = cost,
        conditions = WalkMovement.stanceConditions(dx, rise, dz),
        arc = MotionTemplate.ArcSpec(dx, dz, rise, MODES),
    )

    override fun offersFor(edge: CoarseEdge): Boolean = edge.to.y >= edge.from.y

    override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
        val solutions = solutionsFor(context)
            .filter { context.constraints.sprintModes.contains(it.sprint) }
            .distinctBy { it.mode to it.launchOffset }
            .sortedByDescending { it.margin }

        val closing = closingSpeed(context)
        val standard = solutions.flatMap { solution ->
            launchDelays(context, solution).map { delay ->
                TrajectoryDecision.Launch(solution.sprint, context.edge.to, delay, solution)
            }
        }
        return standard + runUpDecisions(context, solutions, closing)
    }

    /**
     * A launch is priced by how much of the feasible entry-speed band it has to hit.
     *
     * [LaunchSolution.speedSlack] is the half-width of that band in blocks per tick: a
     * jump onto the middle of a wide ledge has plenty, one that has to clear a lip and
     * stop before the far edge has almost none, and the second is where the rollouts get
     * spent. A run-up pays on top of that -- it is real frames of retreating and
     * rebuilding speed, and it commits the body to a stretch of ground before the jump
     * even starts.
     */
    override fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice {
        val solution = when (decision) {
            is TrajectoryDecision.Launch -> decision.solution
            is TrajectoryDecision.RunUpLaunch -> decision.solution
            else -> null
        } ?: return DecisionPrice.FREE

        val tightness = 1.0 - (solution.speedSlack / COMFORTABLE_SPEED_SLACK).coerceIn(0.0, 1.0)
        val base = DecisionPrice(
            difficulty = maxOf(tightness, context.landingRisk(solution)),
        )
        if (decision !is TrajectoryDecision.RunUpLaunch) return base
        return base + DecisionPrice(
            ticks = transitionFrames(decision).toDouble(),
            difficulty = RUN_UP_DIFFICULTY,
        )
    }

    private fun runUpDecisions(
        context: DecisionContext,
        solutions: List<LaunchSolution>,
        closing: Double,
    ): List<TrajectoryDecision> {
        if (solutions.isEmpty()) return emptyList()
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val body = context.body.state
        val along = alongEdge(from, to, body.position.x, body.position.z)

        val anyReachable = solutions.any { solution ->
            val available = (solution.launchOffset - along).coerceAtLeast(0.0)
            val ticks = context.ballistics.groundRunUpTicks(
                entrySpeed = closing, distance = available,
                sprint = solution.sprint, maxTicks = MAX_LAUNCH_FRAME,
            )
            context.ballistics.runUpSpeed(closing, ticks, solution.sprint) >=
                solution.speed - solution.speedSlack * 0.5
        }
        if (anyReachable) return emptyList()

        val ballistics = context.ballistics
        val decisions = ArrayList<TrajectoryDecision>()
        for (solution in solutions) {
            val groundNeeded = ballistics.runUpDistanceFor(solution.speed, solution.sprint)
            if (groundNeeded != null) {
                val retreatAlong = solution.launchOffset - (groundNeeded + RUN_UP_MARGIN_BLOCKS)
                if (clearBehind(context, -retreatAlong)) {
                    decisions += TrajectoryDecision.RunUpLaunch(
                        solution.sprint, edge.to, solution, retreatAlong, hopAlong = null,
                    )
                }
                continue
            }

            // entry beyond what ground running reaches: build it with a preparatory hop
            val mode = if (solution.sprint) LaunchMode.SPRINT_JUMP else LaunchMode.WALK_JUMP
            val cruise = ballistics.cruiseSpeed(solution.sprint)
            val hop = ballistics.fly(mode, cruise, 0.0) ?: continue
            if (hop.exitSpeed < solution.speed - solution.speedSlack * 0.5 - HOP_EXIT_TOLERANCE) continue
            val groundToCruise = ballistics.runUpDistanceFor(cruise * CRUISE_FRACTION, solution.sprint)
                ?: continue
            for (gap in HOP_LANDING_GAPS) {
                val hopAlong = solution.launchOffset - hop.distance - gap
                val retreatAlong = hopAlong - groundToCruise - RUN_UP_MARGIN_BLOCKS
                if (!clearBehind(context, -retreatAlong)) continue
                decisions += TrajectoryDecision.RunUpLaunch(
                    solution.sprint, edge.to, solution, retreatAlong, hopAlong,
                )
            }
        }
        return decisions.take(MAX_RUN_UP_VARIANTS)
    }

    private fun clearBehind(context: DecisionContext, distance: Double): Boolean {
        if (distance <= 0.0) return true
        return retreatClear(context, distance)
    }

    private fun retreatClear(context: DecisionContext, distance: Double): Boolean {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val length = kotlin.math.hypot(to.x - from.x, to.z - from.z)
        if (length <= 1e-9) return false
        val unitX = (to.x - from.x) / length
        val unitZ = (to.z - from.z) / length
        val view = context.view
        var sampled = 1.0
        while (sampled <= distance + 1.0) {
            val x = kotlin.math.floor(from.x - unitX * sampled).toInt()
            val z = kotlin.math.floor(from.z - unitZ * sampled).toInt()
            val y = edge.from.y
            val standable = view.standingSurface(x, y - 1, z) != null &&
                view.voxel(x, y, z).centerPassable &&
                view.voxel(x, y + 1, z).centerPassable
            if (!standable) return false
            sampled += 1.0
        }
        return true
    }

    private fun closingSpeed(context: DecisionContext): Double {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val body = context.body.state
        return alongEdge(
            from, to,
            from.x + body.velocity.x,
            from.z + body.velocity.z,
        ).coerceAtLeast(0.0)
    }

    /**
     * Whether the body simply cannot be going the right speed by the time it launches.
     *
     * Horizontal movement is `v' = friction * (v + acceleration * u)`, which makes the
     * velocities reachable in a given number of ticks a disc with a closed-form centre and
     * radius. Projecting it onto the gap gives the speeds the body could be travelling at
     * when it launches, and the arc's own entry band says which of those will do -- one
     * interval overlap, exactly, replacing a scalar approximation that forgave a fixed
     * tenth of a block per tick because it ignored which way the body was already moving.
     *
     * Deliberately the projection and not the whole velocity: sideways drift at launch is
     * real but the arc carries its own lateral tolerance for it, and refusing launches on
     * that basis measured as a corpus route falling from thirty certified nodes to three.
     */
    private fun unreachableEntry(
        context: DecisionContext,
        delay: Int,
        solution: LaunchSolution,
    ): Boolean {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val length = kotlin.math.hypot(to.x - from.x, to.z - from.z)
        if (length <= 1e-9) return false
        val unitX = (to.x - from.x) / length
        val unitZ = (to.z - from.z) / length
        val velocity = context.body.state.velocity
        val along = HorizontalDynamics.ground(context.ballistics, solution.sprint)
            .reachable(velocity.x, velocity.z, delay)
            .projectOnto(unitX, unitZ)
        val needed = (solution.speed - solution.speedSlack)..(solution.speed + solution.speedSlack)
        return along.endInclusive < needed.start || along.start > needed.endInclusive
    }

    private fun solutionsFor(context: DecisionContext): List<LaunchSolution> {
        val onward = onwardEntryWindow(context)
        // `edge.launch` is solved once when the graph is built and knows nothing about
        // what follows, so where an onward gap is known it is re-solved rather than reused.
        val ideal = onward?.let { window ->
            LaunchSolver.best(
                context.edge.from, context.edge.to,
                profile = context.ballistics, modes = MODES, exitSpeedWindow = window,
            )
        } ?: context.edge.launch ?: hopOver(context)
        val asIs = LaunchSolver.best(
            context.edge.from, context.edge.to,
            profile = context.ballistics,
            modes = MODES,
            maxEntrySpeed = { context.body.speed },
            preferredEntrySpeed = { context.body.speed },
            exitSpeedWindow = onward,
        )
        return listOfNotNull(ideal, asIs)
    }

    /**
     * Entry speeds the gap after this one can accept, when there is one.
     *
     * Only gaps are asked about. A walk onward imposes nothing -- the body brakes on the
     * ground between them -- so informing the solver there would narrow its choices for no
     * reason. A jump onward is the case that matters: this launch's exit speed becomes
     * that one's entry speed with a single block in between, and choosing an arc that
     * lands too fast to leave again is how a chain of pads dead-ends at the second one.
     */
    private fun onwardEntryWindow(context: DecisionContext): ClosedFloatingPointRange<Double>? {
        val steering = context.steering ?: return null
        val onward = steering
            .chain(context.edge.to, null, ONWARD_LOOKAHEAD, null)
            .getOrNull(1)
            ?: return null
        if (onward == context.edge.to) return null
        if (hypot(onward.x - context.edge.to.x, onward.z - context.edge.to.z) <
            CoarseMoveRates.MIN_JUMP_DISTANCE
        ) return null
        val solutions = LaunchSolver.solve(
            context.edge.to, onward, profile = context.ballistics, modes = MODES,
        )
        if (solutions.isEmpty()) return null
        val entry = solutions.minOf { it.speed - it.speedSlack }..
            solutions.maxOf { it.speed + it.speedSlack }
        // Pulled back through the pad. This launch's exit speed does not have to *be* an
        // entry the next gap accepts -- it has to be one the body can turn into such an
        // entry with the ticks it gets standing there. Comparing the two directly rejected
        // launches that work: on `parkour-course-1` the first gap exits at 0.2431 into a
        // window of 0.0917-0.1702, and a single coasting tick lands it at 0.1327.
        return context.ballistics.groundReachable(entry, PAD_GROUND_TICKS, sprint = true)
    }

    private fun hopOver(context: DecisionContext): LaunchSolution? {
        val edge = context.edge
        if (edge.to.y < edge.from.y) return null
        val reachable = maxOf(context.body.speed, context.ballistics.cruiseSpeed(sprint = true))
        return LaunchSolver.best(
            edge.from, edge.to,
            profile = context.ballistics,
            modes = MODES,
            maxEntrySpeed = { reachable },
        )
    }

    /**
     * Ticks of run-up after which the body is actually able to make this launch.
     *
     * A jump has to satisfy two conditions on the same tick: the body must be *at* the
     * launch offset along the gap, and it must be *travelling* at the entry speed the arc
     * was solved for. These were handled separately and approximately -- a distance-based
     * estimate of when the offset is reached, then a bracket of one tick either side of it
     * in the hope that the speed came out right. Both are computable exactly.
     *
     * Horizontal movement is `v' = friction * (v + acceleration * u)`, and the game moves
     * the body by the pre-friction velocity, so rolling the run-up forward analytically
     * gives position and speed at every tick for the cost of a few multiplications. The
     * ticks where the speed lands inside the arc's entry band are the candidates, ranked
     * by how close the body is to the launch offset when they arrive.
     *
     * Falls back to the old estimate when no tick satisfies the speed. The run-up modelled
     * here drives straight down the gap while the follower steers toward the nodes, so the
     * two disagree slightly on a curve, and losing the launch entirely is the worse error.
     */
    private fun launchDelays(context: DecisionContext, solution: LaunchSolution): List<Int> {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val length = kotlin.math.hypot(to.x - from.x, to.z - from.z)
        if (length <= 1e-9) return emptyList()
        val unitX = (to.x - from.x) / length
        val unitZ = (to.z - from.z) / length

        val dynamics = HorizontalDynamics.ground(context.ballistics, solution.sprint)
        val body = context.body.state
        var velocityX = body.velocity.x
        var velocityZ = body.velocity.z
        var along = alongEdge(from, to, body.position.x, body.position.z)

        // The follower accelerates along its YAW, and the yaw turns toward the gap at a
        // bounded rate -- on a ninety-degree zig-zag the first three run-up ticks push
        // sideways, not down the gap. Rolling with gap-aligned acceleration from tick
        // zero overestimated every delay's entry speed, so each proposed arc undershot
        // and clipped the landing face; modelling the turn makes the roll match what
        // the body will actually do.
        val bearing = Math.toDegrees(kotlin.math.atan2(-(to.x - from.x), to.z - from.z))
        var yaw = body.rotation.yaw

        class Fit(val delay: Int, val cost: Double, val misplacement: Double, val speedError: Double)

        // The body must still be standing when the trigger fires: a delay long enough
        // that the run has carried it past the lip proposes a jump that never happens
        // (the trigger only presses on ground) -- the body just runs off and falls. The
        // standable extent along the gap is the cell's half-extent in that direction
        // plus the body's half width hanging over the lip.
        val standableAlong = 0.5 * (kotlin.math.abs(unitX) + kotlin.math.abs(unitZ)) + 0.3

        val fitting = ArrayList<Fit>()
        for (delay in 0..MAX_LAUNCH_FRAME) {
            if (along > standableAlong) break
            val speed = velocityX * unitX + velocityZ * unitZ
            if (kotlin.math.abs(speed - solution.speed) <= solution.speedSlack) {
                // Cost in ticks, so the two terms are commensurable: the run-up itself,
                // plus what the offset error would take to walk off at cruise. Ranking on
                // the error alone buys perfect placement with arbitrarily long run-ups --
                // measured as a corpus route going from four percent over its bound to
                // forty-two.
                val misplacement = kotlin.math.abs(along - solution.launchOffset)
                fitting += Fit(
                    delay,
                    delay + misplacement / dynamics.cruise,
                    misplacement,
                    kotlin.math.abs(speed - solution.speed),
                )
            }
            // One tick of running: the yaw turns toward the gap bearing at the bounded
            // rate and acceleration follows the yaw. The body is displaced by the
            // pre-friction velocity, which is what the stored velocity divides back out to.
            val yawError = Rotation.wrap(bearing - yaw)
            yaw += yawError.coerceIn(-context.constraints.maxYawDegreesPerFrame, context.constraints.maxYawDegreesPerFrame)
            val radians = Math.toRadians(yaw)
            val forwardX = -kotlin.math.sin(radians)
            val forwardZ = kotlin.math.cos(radians)
            velocityX = (velocityX + dynamics.acceleration * forwardX) * dynamics.friction
            velocityZ = (velocityZ + dynamics.acceleration * forwardZ) * dynamics.friction
            along += (velocityX * unitX + velocityZ * unitZ) / dynamics.friction
        }
        if (fitting.isEmpty()) {
            val nominal = launchFrame(context, solution)
            return LAUNCH_BRACKET.map { (nominal + it).coerceAtLeast(0) }
                .distinct()
                .filter { !unreachableEntry(context, it, solution) }
        }
        // Three objectives that genuinely disagree, so offer the best of each rather than
        // weighing them against one another. Cheapest run-up is what open ground wants --
        // ranking on placement alone took a corpus route from four percent over its bound
        // to forty-two. Best placement is what a one-block pad wants, and ranking on cost
        // alone lost a whole parkour course. Best ENTRY SPEED is what a diagonal lone-pad
        // hop wants: the band admits slow early delays whose arcs land at the window's
        // near edge, and on the zig-zag fixture the cheap and true picks were exactly the
        // three undershooting delays -- an arc short on entry speed is beyond the air
        // controller's help, because holding forward is already the whole along-track
        // authority. The frontier is priced; it can decide.
        val cheapest = fitting.sortedBy { it.cost }.map { it.delay }
        val truest = fitting.sortedBy { it.misplacement }.map { it.delay }
        val fastest = fitting.sortedBy { it.speedError }.map { it.delay }
        return (cheapest.take(2) + truest.take(2) + fastest.take(2))
            .distinct()
            .take(MAX_LAUNCH_DELAY_CANDIDATES)
    }

    private fun launchFrame(context: DecisionContext, solution: LaunchSolution): Int {
        val edge = context.edge
        val from = edge.from.center()
        val to = edge.to.center()
        val body = context.body.state
        val remaining = solution.launchOffset - alongEdge(from, to, body.position.x, body.position.z)
        if (remaining <= 0.0) return 0

        val closing = alongEdge(
            from, to,
            from.x + body.velocity.x,
            from.z + body.velocity.z,
        ).coerceAtLeast(0.0)

        return context.ballistics.groundRunUpTicks(
            entrySpeed = closing,
            distance = remaining,
            sprint = solution.sprint,
            maxTicks = MAX_LAUNCH_FRAME,
        )
    }

    override fun program(context: ProgramContext): ControlProgram {
        val decision = context.decision
        if (decision is TrajectoryDecision.RunUpLaunch) {
            val from = context.body.stance.center()
            val to = (decision.step ?: context.body.stance).center()
            return RunUpLaunchProgram(
                takeoff = from,
                aim = to,
                solution = decision.solution,
                retreatAlong = decision.retreatAlong,
                hopAlong = decision.hopAlong,
                maxYawChange = context.constraints.maxYawDegreesPerFrame,
            )
        }
        val solution = (decision as? TrajectoryDecision.Launch)?.solution
        return SegmentFollowerProgram(
            nodes = context.nodes,
            sprint = context.decision.sprint,
            lookAheadNodes = LOOK_AHEAD_NODES,
            launch = context.launch,
            maxYawChange = context.constraints.maxYawDegreesPerFrame,
            holdForwardInFlight = solution?.holdForward ?: true,
            holdTicks = solution?.holdTicks ?: Int.MAX_VALUE,
            airPlan = if (context.openLanding) null
                else airPlanFor(context.body.stance, (decision as? TrajectoryDecision.Launch)?.step, solution),
        )
    }

    /**
     * The solved launch as a closed-loop flight target: the solver's aim point in world
     * coordinates and the schedule it certified against. Null (no solved launch, or a
     * degenerate edge) flies the historical open schedule.
     */
    private fun airPlanFor(from: Stance, to: Stance?, solution: LaunchSolution?): AirSteering.AirPlan? {
        if (solution == null || to == null || to == from) return null
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val length = hypot(dx, dz)
        if (length <= 1e-9) return null
        val unitX = dx / length
        val unitZ = dz / length
        return AirSteering.AirPlan(
            // The centre of the landing cell, not the solver's own aim: every onward
            // solution and delay roll is solved from a cell-centre origin, so landing
            // there makes the next edge's model true -- and it is the point of maximum
            // margin against both lips. The solver's aim optimises this landing alone;
            // the centre serves the chain.
            aimX = to.x + 0.5,
            aimZ = to.z + 0.5,
            unitX = unitX,
            unitZ = unitZ,
            airTicks = solution.airTicks,
            holdTicks = if (solution.holdForward) solution.holdTicks else 0,
            sprintAcceleration = if (solution.sprint) BallisticProfile.SPRINT_AIR_ACCELERATION
                else BallisticProfile.WALK_AIR_ACCELERATION,
            walkAcceleration = BallisticProfile.WALK_AIR_ACCELERATION,
        )
    }

    override fun completed(context: CompletionContext): Boolean {
        val decision = context.decision
        if (decision is TrajectoryDecision.RunUpLaunch) {
            if (!context.airborne) return false
            val from = context.body.stance.center()
            val to = (decision.step ?: context.body.stance).center()
            val along = alongEdge(from, to, context.observed.position.x, context.observed.position.z)
            return along > decision.solution.launchOffset + POST_LAUNCH_MARGIN_BLOCKS
        }
        val launch = context.launch ?: return context.stance != context.body.stance
        return launch.hasFired && context.airborne
    }

    override fun transitionFrames(decision: TrajectoryDecision): Int {
        val runUp = decision as? TrajectoryDecision.RunUpLaunch ?: return 0
        val retreatFrames = (-runUp.retreatAlong).coerceAtLeast(0.0) * RETREAT_FRAMES_PER_BLOCK
        return RUN_UP_TRANSITION_FRAMES + retreatFrames.toInt() + runUp.solution.airTicks
    }

    fun triggerFor(decision: TrajectoryDecision): LaunchTrigger? =
        (decision as? TrajectoryDecision.Launch)?.let { LaunchTrigger(it.delayFrames) }

    private val MODES = LaunchMode.entries.filter { it.jumps }

    private val LAUNCH_BRACKET = listOf(0, -1, 1)

    /** Launch ticks offered per solution: the cheapest two run-ups and the truest two. */
    private const val MAX_LAUNCH_CANDIDATES = 4

    /**
     * Delay candidates per solution: two picks from each of the three ranking
     * objectives, before dedup. A tighter cap silently drops one objective's picks and
     * re-creates the failure that objective exists to prevent.
     */
    private const val MAX_LAUNCH_DELAY_CANDIDATES = 6

    private const val MAX_LAUNCH_FRAME = 8

    private const val RUN_UP_MARGIN_BLOCKS = 0.75

    private const val RUN_UP_TRANSITION_FRAMES = 50

    private const val RETREAT_FRAMES_PER_BLOCK = 10

    private const val CRUISE_FRACTION = 0.97

    private const val POST_LAUNCH_MARGIN_BLOCKS = 1.0

    private const val HOP_EXIT_TOLERANCE = 0.02

    private val HOP_LANDING_GAPS = listOf(-0.35, -0.1, 0.2)

    private const val MAX_RUN_UP_VARIANTS = 4

    private const val LOOK_AHEAD_NODES = 1

    /** Steering nodes fetched to find the gap after this one. */
    private const val ONWARD_LOOKAHEAD = 2

    /**
     * Ticks the body is assumed to get on the pad between two gaps.
     *
     * A landing pad on a parkour course is one block, which is a tick or three of contact
     * depending on how fast the body crosses it. Three is the generous end on purpose:
     * this widens what the solver will consider, and the rollout still has to certify it.
     */
    private const val PAD_GROUND_TICKS = 3

    /**
     * Entry-speed slack, in blocks per tick, at which a launch stops being fussy.
     *
     * Measured against the corpus rather than guessed: solved jump edges there run
     * p10 = 0.076, p50 = 0.095, p90 = 0.119 blocks per tick of slack. Setting the bar at
     * the low decile prices the ordinary jump at nothing and reserves the difficulty for
     * the genuinely tight minority -- the first attempt at this used a third of the value
     * and priced the whole population as fussy, which starved the search of jumps.
     */
    private const val COMFORTABLE_SPEED_SLACK = 0.08

    /** A run-up is never a casual option: it commits ground behind the body as well as ahead. */
    private const val RUN_UP_DIFFICULTY = 0.75
}
