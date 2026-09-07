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
import com.lambda.pathing.core.HorizontalPoint

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
     * A jump template must be reachable from a standing start on its own block (the
     * coarse graph cannot promise a run-up). The gate is the corner-to-corner AIR GAP,
     * not cell-centre distance, against rollout-measured ceilings; [SimpleMoveOptions.maxJumpSpan]
     * is a per-axis user cap on top. See docs/decisions/movement-tuning.md (standing reach).
     */
    private fun offered(dx: Int, dz: Int, rise: Int, options: SimpleMoveOptions): Boolean {
        val distance = hypot(dx, dz)
        if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE) return false
        if (dx != 0 && dz != 0 && !options.allowDiagonal) return false
        if (abs(dx) != abs(dz) && dx != 0 && dz != 0 && !options.allowOffAxisJumps) return false
        if (maxOf(abs(dx), abs(dz)) > options.maxJumpSpan) return false

        // Statically the WIDEST ceiling this stance delta could reach; the real
        // (surface-corrected) rise is judged in [spec]'s riseAdmission. The deep-drop
        // ceiling is opt-in and never offered for LEVEL deltas.
        // See docs/decisions/movement-tuning.md (deep-drop jumps).
        val ceiling = if (rise < 0 && options.allowDeepDropJumps) FULL_DROP_AIR_GAP_BLOCKS
            else STANDING_AIR_GAP_BLOCKS
        return airGap(dx, dz) <= ceiling + REACH_EPSILON
    }

    private fun airGap(dx: Int, dz: Int): Double = kotlin.math.hypot(
        (abs(dx) - 1).coerceAtLeast(0).toDouble(),
        (abs(dz) - 1).coerceAtLeast(0).toDouble(),
    )

    private const val REACH_EPSILON = 1e-9

    /** Air gap a standing start clears on flat and near-flat jumps: hypot(3, 1). */
    private val STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 1.0)

    /** Rising jumps trade reach for the block of height: hypot(2, 2). */
    private val RISING_STANDING_AIR_GAP_BLOCKS = kotlin.math.hypot(2.0, 2.0)

    /**
     * Drop-extended reaches: half a block of real descent certifies a 4.0 air gap, a
     * full block the 4.12-4.24 diagonals; 5.0 never certifies.
     * See docs/decisions/movement-tuning.md (drop-extended reaches).
     */
    private const val HALF_DROP_AIR_GAP_BLOCKS = 4.0

    private val FULL_DROP_AIR_GAP_BLOCKS = kotlin.math.hypot(3.0, 3.0)

    /** Real-rise boundaries for the drop-extended ceilings, placed between measured clusters. */
    private const val HALF_DROP_RISE = -0.25

    private const val FULL_DROP_RISE = -0.75

    private fun hypot(dx: Int, dz: Int): Double = kotlin.math.hypot(dx.toDouble(), dz.toDouble())

    /** This point shifted sideways (perp of the [from]->here axis) by [offset] blocks. */
    private fun HorizontalPoint.laterallyShifted(
        from: HorizontalPoint,
        offset: Double,
    ): HorizontalPoint {
        if (offset == 0.0) return this
        val length = kotlin.math.hypot(x - from.x, z - from.z)
        if (length <= 1e-9) return this
        val unitX = (x - from.x) / length
        val unitZ = (z - from.z) / length
        return copy(x = x - unitZ * offset, z = z + unitX * offset)
    }

    private fun spec(dx: Int, dz: Int, rise: Int, cost: Double): TemplateSpec {
        val gap = airGap(dx, dz)
        return TemplateSpec(
            dx = dx, dy = rise, dz = dz,
            movement = id,
            cost = cost,
            conditions = WalkMovement.stanceConditions(dx, rise, dz),
            arc = MotionTemplate.ArcSpec(
                dx, dz, rise, MODES,
                // Reach ladder judged against the surface-corrected rise: a real ascent
                // keeps the rising reach, a near-flat one gets the flat ceiling, longer
                // gaps must be bought with descent. See docs/decisions/movement-tuning.md.
                riseAdmission = { realRise ->
                    when {
                        gap <= RISING_STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
                        realRise > NEAR_FLAT_RISE -> false
                        gap <= STANDING_AIR_GAP_BLOCKS + REACH_EPSILON -> true
                        realRise > HALF_DROP_RISE -> false
                        gap <= HALF_DROP_AIR_GAP_BLOCKS + REACH_EPSILON -> true
                        else -> realRise <= FULL_DROP_RISE
                    }
                },
            ),
        )
    }

    /** Real ascents at or below this fly like flat jumps; above it, the rising reach applies. */
    private const val NEAR_FLAT_RISE = 0.75

    override fun offersFor(edge: CoarseEdge): Boolean = edge.to.y >= edge.from.y

    /**
     * Momentum skips: a sprint-jump across cells the route merely walks. Targets come
     * from the steering chain, furthest reachable first; landing short is not a failure,
     * the rollout anchors wherever the body comes down.
     */
    override fun proposals(context: ProposalContext): Proposals {
        val gait = if (context.momentumGait) gaitHop(context) else null
        val skips = skipProposals(context)
        if (gait == null) return skips
        return Proposals(launches = listOf(gait) + skips.launches)
    }

    /**
     * The chained sprint-jump gait: one natural-distance hop along a straight, level
     * chain stretch, as a solution-less [TrajectoryDecision.Launch] (open-loop, forward
     * held, jump on the first grounded tick). The landing anchor is grounded and fast,
     * so the next poll proposes the next hop; no chaining machinery is needed.
     */
    private fun gaitHop(context: ProposalContext): TrajectoryDecision? {
        val body = context.body
        if (!body.state.onGround) return null
        if (body.speed < GAIT_MIN_SPEED) return null
        val heading = body.heading() ?: return null
        val first = context.steps.firstOrNull()?.to ?: return null
        val chain = context.steering.chain(body.stance, first, GAIT_LOOKAHEAD, heading)
        val headingLength = hypot(heading.first, heading.second)
        var target: Stance? = null
        for (index in 1 until chain.size) {
            val cell = chain[index]
            // The level bound guards every crossed cell; alignment only the target cell
            // (chains zigzag half a block). See docs/decisions/movement-tuning.md (gait).
            if (abs(cell.y - body.stance.y) > 1) break
            val towardX = (cell.x - body.stance.x).toDouble()
            val towardZ = (cell.z - body.stance.z).toDouble()
            val distance = hypot(towardX, towardZ)
            if (distance > GAIT_MAX_HOP_BLOCKS) break
            if (distance < GAIT_MIN_HOP_BLOCKS) continue
            // Flown-over cells may rise a block; the landing may not (an uphill landing
            // spends the arc's tail on the climb and arrives slow).
            if (cell.y > body.stance.y) continue
            val alignment = (heading.first * towardX + heading.second * towardZ) /
                (headingLength * distance)
            if (alignment >= GAIT_MIN_ALIGNMENT) target = cell
        }
        val hop = target ?: return null
        return TrajectoryDecision.Launch(sprint = true, step = hop, delayFrames = 0, solution = null)
    }

    private fun skipProposals(context: ProposalContext): Proposals {
        if (!context.momentumSkips) return Proposals.EMPTY
        val body = context.body
        if (!body.state.onGround) return Proposals.EMPTY
        if (body.speed < SKIP_MIN_SPEED) return Proposals.EMPTY
        val first = context.steps.firstOrNull()?.to ?: return Proposals.EMPTY
        val chain = context.steering.chain(body.stance, first, SKIP_LOOKAHEAD, body.heading())
        if (chain.size <= SKIP_MIN_CELLS) return Proposals.EMPTY
        val reachable = maxOf(body.speed, BallisticProfile.VANILLA.cruiseSpeed(sprint = true))
        // MOVING guides on both sides: the skipper is moving and lands moving, and the
        // blended guide mixed classes inconsistently across the comparison.
        val here = context.movingGuideTicks(body.stance)
        val heading = body.heading() ?: return Proposals.EMPTY
        for (index in chain.lastIndex downTo SKIP_MIN_CELLS) {
            val target = chain[index]
            if (target.y > body.stance.y) continue
            if (body.stance.y - target.y > SKIP_MAX_DROP) continue
            val towardX = (target.x - body.stance.x).toDouble()
            val towardZ = (target.z - body.stance.z).toDouble()
            val distance = hypot(towardX, towardZ)
            if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE) continue
            // The body flies where its momentum points; a skip against the grain is a
            // turn first, and turns are the walk proposer's business.
            val alignment = (heading.first * towardX + heading.second * towardZ) /
                (hypot(heading.first, heading.second) * distance)
            if (alignment < SKIP_MIN_ALIGNMENT) continue
            val solution = LaunchSolver.best(
                body.stance, target,
                modes = MODES,
                maxEntrySpeed = { reachable },
            ) ?: continue
            // A skip must merge two gaps into one flight, cut the walked path, or convert
            // a real drop; flat jump spam loses on open ground. See
            // docs/decisions/movement-tuning.md (skip admission).
            var walked = 0.0
            var skipsGap = false
            for (step in 1..index) {
                val stride = hypot(
                    (chain[step].x - chain[step - 1].x).toDouble(),
                    (chain[step].z - chain[step - 1].z).toDouble(),
                )
                walked += stride
                if (stride > SKIP_GAP_STRIDE_BLOCKS) skipsGap = true
            }
            val falls = body.stance.y - target.y >= SKIP_MIN_FALL_BLOCKS
            val cuts = walked - distance >= SKIP_MIN_CUT_BLOCKS
            if (!skipsGap && !falls && !cuts) continue
            // And the model must still claim a saving on its own terms.
            if (here.isFinite()) {
                val there = context.movingGuideTicks(target)
                if (there.isFinite()) {
                    val flightTicks = solution.airTicks + SKIP_LAUNCH_PREP_TICKS
                    if (here - there < flightTicks + SKIP_MIN_SAVING_TICKS) continue
                }
            }
            return Proposals(
                launches = listOf(
                    TrajectoryDecision.Launch(solution.sprint, target, delayFrames = 0, solution),
                ),
            )
        }
        return Proposals.EMPTY
    }

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
        }
        if (solution == null) return DecisionPrice.FREE

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
     * Whether the body cannot be at the arc's entry speed after [delay] ticks: the
     * reachable velocity disc, projected onto the gap, must overlap the entry band.
     * Deliberately the projection, not the whole velocity -- the arc carries its own
     * lateral tolerance. See docs/decisions/movement-tuning.md (entry reachability).
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
        // A lateral offset on the certified coarse edge is geometry, not entry-speed
        // policy: the centre line is blocked by a partial shape for EVERY solution of
        // this edge, so re-solved solutions inherit the dodge the probe found. The
        // rollout still certifies the shifted flight.
        val dodge = context.edge.launch?.lateralOffset ?: 0.0
        return listOfNotNull(ideal, asIs).map { solution ->
            if (dodge != 0.0 && solution.lateralOffset == 0.0) solution.copy(lateralOffset = dodge)
            else solution
        }
    }

    /**
     * Entry speeds the gap after this one can accept, or null when the onward step is a
     * walk (the body brakes on the ground, so no window applies). This launch's exit
     * speed becomes that gap's entry speed with a single block in between.
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
        // Pulled back through the pad: the exit speed need not BE an entry the next gap
        // accepts, only one the body can coast into during its ticks on the pad. See
        // docs/decisions/movement-tuning.md (exit-speed window through the pad).
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
     * Ticks of run-up after which the body is both AT the launch offset and AT the arc's
     * entry speed. The run-up is rolled analytically (`v' = friction * (v + acceleration * u)`,
     * displacement by the pre-friction velocity); ticks whose speed lies in the entry band
     * are candidates. Falls back to the distance estimate when none fits: this roll drives
     * straight down the gap while the follower steers toward nodes, and losing the launch
     * entirely is the worse error.
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

        // Acceleration follows the YAW, which turns toward the gap at a bounded rate;
        // rolling gap-aligned from tick zero overestimates entry speed on a zig-zag.
        val bearing = Math.toDegrees(kotlin.math.atan2(-(to.x - from.x), to.z - from.z))
        var yaw = body.rotation.yaw

        class Fit(val delay: Int, val cost: Double, val misplacement: Double, val speedError: Double)

        // The body must still be standing when the trigger fires (it only presses on
        // ground): the cell's half-extent along the gap plus the body's half width.
        val standableAlong = 0.5 * (kotlin.math.abs(unitX) + kotlin.math.abs(unitZ)) + 0.3

        val fitting = ArrayList<Fit>()
        for (delay in 0..MAX_LAUNCH_FRAME) {
            if (along > standableAlong) break
            val speed = velocityX * unitX + velocityZ * unitZ
            if (kotlin.math.abs(speed - solution.speed) <= solution.speedSlack) {
                // Cost in ticks so the terms are commensurable: the run-up plus the offset
                // error walked off at cruise.
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
        // No delay-0 for bodies faster than the band: see docs/decisions/movement-tuning.md
        // (launch-delay ranking).
        if (fitting.isEmpty()) {
            val nominal = launchFrame(context, solution)
            return LAUNCH_BRACKET.map { (nominal + it).coerceAtLeast(0) }
                .distinct()
                .filter { !unreachableEntry(context, it, solution) }
        }
        // Three objectives that genuinely disagree (open ground wants cheap, a one-block
        // pad wants placement, a lone diagonal pad wants entry speed): offer the best of
        // each and let the priced frontier decide. See docs/decisions/movement-tuning.md.
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
                .laterallyShifted(from, decision.solution.lateralOffset)
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
        // A dodged flight line must be RUN, not just aimed at: air-steering authority
        // develops late in the arc, so shifting the landing node makes the ground
        // approach and launch bearing follow the swept-clear line from the start.
        val step = (decision as? TrajectoryDecision.Launch)?.step
        val nodes = if (solution != null && solution.lateralOffset != 0.0 && step != null) {
            val from = context.body.stance.center()
            context.nodes.map { node ->
                if (node.x == step.x + 0.5 && node.z == step.z + 0.5) {
                    node.laterallyShifted(from, solution.lateralOffset)
                } else node
            }
        } else context.nodes
        return SegmentFollowerProgram(
            nodes = nodes,
            sprint = context.decision.sprint,
            lookAheadNodes = PursuitTracker.DEFAULT_LOOK_AHEAD_NODES,
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
            // The landing cell's centre, not the solver's aim: onward solutions and delay
            // rolls are solved from a cell-centre origin, and centre has maximum margin
            // against both lips. A lateral offset shifts the aim by the swept-clear line.
            aimX = to.x + 0.5 - unitZ * solution.lateralOffset,
            aimZ = to.z + 0.5 + unitX * solution.lateralOffset,
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

    private val MODES = LaunchMode.entries.filter { it.jumps }

    /** Below this the body has no momentum worth spending on a skip. */
    private const val SKIP_MIN_SPEED = 0.15

    /** Chain cells considered ahead; a skip past five is outside the solver's reach anyway. */
    private const val SKIP_LOOKAHEAD = 5

    /** A skip must clear at least two chain edges, or it is just the jump the edge already offers. */
    private const val SKIP_MIN_CELLS = 2

    private const val SKIP_MAX_DROP = 3

    /** Blocks of walked path a level skip must cut away to be worth a flight. */
    private const val SKIP_MIN_CUT_BLOCKS = 1.0

    private const val SKIP_MIN_FALL_BLOCKS = 2

    /** A chain stride longer than a walkable step: the ground between is a gap. */
    private const val SKIP_GAP_STRIDE_BLOCKS = 1.5

    /** cos(30 degrees): the flight must go where the momentum already points. */
    private const val SKIP_MIN_ALIGNMENT = 0.866

    /** Ticks of ground run a level launch typically needs before it is airborne. */
    private const val SKIP_LAUNCH_PREP_TICKS = 2

    private const val SKIP_MIN_SAVING_TICKS = 2.0

    /** The gait maintains speed; it does not create it. Near-sprint bodies only. */
    private const val GAIT_MIN_SPEED = 0.2

    private const val GAIT_LOOKAHEAD = 5

    /** Shorter than this is a step, not a hop. */
    private const val GAIT_MIN_HOP_BLOCKS = 2.5

    /** A level sprint hop's reliable reach; beyond it the arc needs solving, not assuming. */
    private const val GAIT_MAX_HOP_BLOCKS = 3.8

    /** cos(26 degrees): the hop's aim may wobble with the chain; real corners belong to walks. */
    private const val GAIT_MIN_ALIGNMENT = 0.9

    private val LAUNCH_BRACKET = listOf(0, -1, 1)

    /** Two picks from each of the three ranking objectives, before dedup; a tighter cap drops an objective. */
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


    /** Steering nodes fetched to find the gap after this one. */
    private const val ONWARD_LOOKAHEAD = 2

    /**
     * Ticks the body is assumed to stand on a one-block pad between two gaps; the
     * generous end on purpose, since the rollout still certifies what the solver admits.
     */
    private const val PAD_GROUND_TICKS = 3

    /**
     * Entry-speed slack, in blocks per tick, at which a launch stops being fussy: the
     * corpus p10 of solved-edge slack, so the ordinary jump prices at nothing.
     * See docs/decisions/movement-tuning.md (comfortable slack).
     */
    private const val COMFORTABLE_SPEED_SLACK = 0.08

    /** A run-up is never a casual option: it commits ground behind the body as well as ahead. */
    private const val RUN_UP_DIFFICULTY = 0.75
}
