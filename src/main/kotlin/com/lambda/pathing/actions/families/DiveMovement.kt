package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CellCondition
import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.SteeringField
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.DiveProgram
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

object DiveMovement : Movement {
	override val id = MovementId.DIVE

	val SUBMERGED = object : CellPredicate {
		override fun matches(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean = submerged(view, x, y, z)
		override val extraReads = listOf(VoxelPos(0, 1, 0))
	}

	override fun templates(context: MovementContext): List<TemplateSpec> {
		if (!context.options.allowSwimming || !context.options.allowDiving) return emptyList()
		return buildList {
			for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
				if (dx == 0 && dy == 0 && dz == 0) continue
				val target = if (dy > 0) CellPredicate.WATER else SUBMERGED
				val passage = buildList {
					for (cx in minOf(0, dx)..maxOf(0, dx)) for (cy in minOf(0, dy)..maxOf(0, dy)) for (cz in minOf(0, dz)..maxOf(0, dz)) {
						if ((cx == 0 && cy == 0 && cz == 0) || (cx == dx && cy == dy && cz == dz)) continue
						add(CellCondition(cx, cy, cz, CellPredicate.SWIMMABLE))
					}
				}
				val cost = sqrt((dx * dx + dy * dy + dz * dz).toDouble()) * context.costs.swim * DIVE_COST_FACTOR
				add(TemplateSpec(dx, dy, dz, id, cost, listOf(CellCondition(dx, dy, dz, target)) + passage, departure = CellPredicate.WATER))
			}
		}
	}

	override fun occupies(view: CoarseVoxelView, stance: Stance): Boolean =
		submerged(view, stance.x, stance.y, stance.z) && !openWater(view, stance.x, stance.y, stance.z) && breathableWithin(view, stance, AIR_REACH_BLOCKS)

	fun openWater(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean {
		if (!submerged(view, x, y, z) || !openAbove(view, x, y, z)) return false
		return HORIZONTAL_SIDES.none { side -> keepsBeside(view, x + SIDE_X[side], y, z + SIDE_Z[side]) }
	}

	private fun keepsBeside(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean {
		val voxel = view.voxel(x, y, z)
		if (voxel.medium != Medium.WATER) return exit(voxel)
		val above = view.voxel(x, y + 1, z)
		return if (above.medium == Medium.WATER) !openAbove(view, x, y, z) else true
	}

	fun exit(voxel: CoarseVoxel): Boolean =
		voxel.medium != Medium.WATER && voxel.medium != Medium.UNKNOWN && (voxel.centerPassable || voxel.partial || voxel.openSides != 0)

	private fun openAbove(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean {
		var top = y + 1
		while (view.medium(x, top, z) == Medium.WATER) top++
		return view.voxel(x, top, z).fullyPassable
	}

	fun aboveOpenWater(view: CoarseVoxelView, stance: Stance): Stance {
		var y = stance.y
		while (openWater(view, stance.x, y, stance.z)) y++
		return if (y == stance.y) stance else Stance(stance.x, y, stance.z)
	}

	fun inOpenColumn(view: CoarseVoxelView, goal: Stance, stance: Stance): Boolean =
		stance.x == goal.x && stance.z == goal.z && stance.y >= goal.y &&
				stance.y < aboveOpenWater(view, goal).y && breathableWithin(view, goal, AIR_REACH_BLOCKS)

	fun submerged(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean =
		view.medium(x, y, z) == Medium.WATER && (view.medium(x, y + 1, z) == Medium.WATER || !view.voxel(x, y + 1, z).fullyPassable)

	fun breathable(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean {
		if (view.medium(x, y, z) != Medium.WATER) return false
		if (view.bubbles(x, y, z)) return true
		if (view.medium(x, y + 1, z) == Medium.WATER || !view.voxel(x, y + 1, z).fullyPassable) return false
		return view.medium(x, y - 1, z) == Medium.WATER || HORIZONTAL_SIDES.none { side -> swimOnly(view, x + SIDE_X[side], y, z + SIDE_Z[side]) }
	}

	private fun swimOnly(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean =
		view.medium(x, y, z) == Medium.WATER && view.medium(x, y + 1, z) != Medium.WATER && !view.voxel(x, y + 1, z).fullyPassable

	fun breathableWithin(view: CoarseVoxelView, from: Stance, reach: Int): Boolean {
		require(reach <= AIR_REACH_BLOCKS)
		for (up in 0..reach) {
			val y = from.y + up
			if (breathable(view, from.x, y, from.z)) return true
			if (view.medium(from.x, y, from.z) != Medium.WATER) break
			if (up == reach) return false
		}
		val scratch = AIR_SCRATCH.get()
		val stamp = scratch.next()
		val seen = scratch.seen
		val queue = scratch.queue
		queue[0] = BlockPos.asLong(from.x, from.y, from.z)
		seen[scratch.index(0, 0, 0)] = stamp
		var count = 1
		var head = 0
		var tail = 1
		for (depth in 0..reach) {
			val level = tail
			while (head < level) {
				val cell = queue[head++]
				val x = BlockPos.unpackLongX(cell)
				val y = BlockPos.unpackLongY(cell)
				val z = BlockPos.unpackLongZ(cell)
				if (breathable(view, x, y, z)) return true
				if (count >= MAX_SEARCH_CELLS) return false
				for (side in 0 until 6) {
					val nx = x + SIDE_X[side]
					val ny = y + SIDE_Y[side]
					val nz = z + SIDE_Z[side]
					if (view.medium(nx, ny, nz) != Medium.WATER) continue
					val index = scratch.index(nx - from.x, ny - from.y, nz - from.z)
					if (seen[index] == stamp) continue
					seen[index] = stamp
					count++
					queue[tail++] = BlockPos.asLong(nx, ny, nz)
				}
			}
			if (tail == level) return false
		}
		return false
	}

	private class AirScratch {
		val seen = ShortArray(WIDTH * WIDTH * WIDTH)
		val queue = LongArray(MAX_SEARCH_CELLS + 6)
		private var stamp: Short = 0

		fun next(): Short {
			if (stamp == Short.MAX_VALUE) {
				seen.fill(0)
				stamp = 0
			}
			stamp++
			return stamp
		}

		fun index(dx: Int, dy: Int, dz: Int): Int = ((dx + RADIUS) * WIDTH + dy + RADIUS) * WIDTH + dz + RADIUS

		private companion object {
			const val RADIUS = AIR_REACH_BLOCKS + 1
			const val WIDTH = 2 * RADIUS + 1
		}
	}

	private val AIR_SCRATCH: ThreadLocal<AirScratch> = ThreadLocal.withInitial(::AirScratch)

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val from = context.edge.from
		val to = context.edge.to
		val view = context.view
		val chain = context.steering.chain(from, to, AIM_NODES + 1)
		var aim = to
		for (index in 2 until chain.size) {
			val node = chain[index]
			val previous = chain[index - 1]
			if (node.x - previous.x != to.x - from.x || node.y - previous.y != to.y - from.y || node.z - previous.z != to.z - from.z) break
			if (!submerged(view, node.x, node.y, node.z)) break
			aim = node
		}
		val reach = maxOf(abs(to.x - from.x), abs(to.y - from.y), abs(to.z - from.z))
		return listOf(TrajectoryDecision.Dive(to, surface = !submerged(view, to.x, to.y, to.z), aim = aim, reach = reach))
	}

	override fun proposals(context: ProposalContext): List<TrajectoryDecision> {
		val leading = context.steps.firstOrNull() ?: return emptyList()
		if (!context.body.state.touchingWater) return emptyList()
		val ahead = context.steering.chain(context.body.stance, leading.to, AIR_LOOKAHEAD_NODES).drop(1)
		return listOfNotNull(breath(context, leading, ahead), plunge(context, ahead))
	}

	private fun breath(context: ProposalContext, leading: CoarseEdge, ahead: List<Stance>): TrajectoryDecision? {
		val body = context.body
		val view = context.view
		if (body.state.air >= Vanilla.MAX_AIR) return null
		var holding = 0
		var previous = body.stance
		for (node in ahead) {
			if (!submerged(view, node.x, node.y, node.z) || view.bubbles(node.x, node.y, node.z)) break
			holding += maxOf(abs(node.x - previous.x), abs(node.y - previous.y), abs(node.z - previous.z), 1)
			previous = node
		}
		if (body.state.air >= Vanilla.SAFE_AIR + holding * AIR_TICKS_PER_NODE) return null
		val here = body.stance
		if (breathable(view, here.x, here.y, here.z)) {
			return if (leading.movement == id) TrajectoryDecision.Dive(leading.to, breathe = true) else null
		}
		val pocket = nearbyAir(view, context.steering, here) ?: return null
		return TrajectoryDecision.Dive(pocket, breathe = true, detour = true)
	}

	private fun plunge(context: ProposalContext, ahead: List<Stance>): TrajectoryDecision? {
		val view = context.view
		val here = context.body.stance
		var previous = here
		var shafted = false
		var target: Stance? = null
		for ((index, node) in ahead.withIndex()) {
			if (index >= PLUNGE_NODES || view.medium(node.x, node.y, node.z) != Medium.WATER) break
			if (node.x == previous.x && node.z == previous.z && abs(node.y - previous.y) > 1) shafted = true
			if (shafted && index > 0 && clearWater(view, context.body.state.position, node)) target = node
			previous = node
		}
		val chosen = target ?: return null
		val reach = maxOf(abs(chosen.x - here.x), abs(chosen.y - here.y), abs(chosen.z - here.z))
		return TrajectoryDecision.Dive(chosen, surface = !submerged(view, chosen.x, chosen.y, chosen.z), aim = chosen, reach = reach)
	}

	private fun clearWater(view: CoarseVoxelView, from: Vec3d, to: Stance): Boolean {
		val startY = from.y + BODY_CENTER
		val endX = to.x + 0.5
		val endY = to.y + BODY_CENTER
		val endZ = to.z + 0.5
		val samples = ceil(maxOf(abs(endX - from.x), abs(endY - startY), abs(endZ - from.z)) / LINE_SAMPLE).toInt().coerceAtLeast(1)
		for (sample in 0..samples) {
			val t = sample.toDouble() / samples
			val x = from.x + (endX - from.x) * t
			val y = floor(startY + (endY - startY) * t).toInt()
			val z = from.z + (endZ - from.z) * t
			for (corner in BODY_CORNERS) {
				if (view.medium(floor(x + corner.first).toInt(), y, floor(z + corner.second).toInt()) != Medium.WATER) return false
			}
		}
		return true
	}

	private fun nearbyAir(view: CoarseVoxelView, steering: SteeringField, from: Stance): Stance? {
		val limit = steering.guide(from) + DETOUR_SLACK_TICKS
		if (!limit.isFinite()) return null
		val seen = LongOpenHashSet()
		var frontier = LongArrayFIFOQueue()
		frontier.enqueue(BlockPos.asLong(from.x, from.y, from.z))
		seen.add(BlockPos.asLong(from.x, from.y, from.z))
		repeat(DETOUR_REACH) {
			val next = LongArrayFIFOQueue()
			while (!frontier.isEmpty) {
				val cell = frontier.dequeueLong()
				val x = BlockPos.unpackLongX(cell)
				val y = BlockPos.unpackLongY(cell)
				val z = BlockPos.unpackLongZ(cell)
				for (side in 0 until 6) {
					val nx = x + SIDE_X[side]
					val ny = y + SIDE_Y[side]
					val nz = z + SIDE_Z[side]
					if (view.medium(nx, ny, nz) != Medium.WATER || !seen.add(BlockPos.asLong(nx, ny, nz))) continue
					val candidate = Stance(nx, ny, nz)
					if (breathable(view, nx, ny, nz) && steering.isMapped(candidate) && steering.guide(candidate) <= limit) return candidate
					next.enqueue(BlockPos.asLong(nx, ny, nz))
				}
			}
			frontier = next
		}
		return null
	}

	override fun transitionFrames(decision: TrajectoryDecision): Int {
		val dive = decision as? TrajectoryDecision.Dive ?: return 0
		return when {
			dive.detour -> DETOUR_FRAMES
			dive.breathe -> BREATHE_FRAMES
			else -> dive.reach * SHAFT_FRAMES_PER_BLOCK
		}
	}

	override fun program(context: ProgramContext): ControlProgram =
		DiveProgram(context.decision as TrajectoryDecision.Dive, context)

	override fun completed(context: CompletionContext): Boolean =
		context.stance != context.body.stance && context.observed.touchingWater && refilled(context) &&
				kotlin.math.abs(context.stance.x - context.decision.step.x) <= 1 &&
				kotlin.math.abs(context.stance.y - context.decision.step.y) <= 1 &&
				kotlin.math.abs(context.stance.z - context.decision.step.z) <= 1

	private fun refilled(context: CompletionContext): Boolean {
		val dive = context.decision as? TrajectoryDecision.Dive ?: return true
		if (dive.detour) return context.observed.air >= Vanilla.MAX_AIR
		return !dive.breathe || context.observed.air >= Vanilla.MAX_AIR - BREATHE_COMPLETION_SLACK
	}

	override val inMedium: Boolean get() = true

	override fun descentAllowance(decision: TrajectoryDecision): Double = DIVE_DESCENT_ALLOWANCE

	const val AIR_REACH_BLOCKS = 20

	private const val MAX_SEARCH_CELLS = 512

	private const val DIVE_COST_FACTOR = 1.25

	private const val DIVE_DESCENT_ALLOWANCE = 48.0

	private const val AIR_LOOKAHEAD_NODES = 48

	private const val AIM_NODES = 3

	private const val AIR_TICKS_PER_NODE = 8

	private const val BREATHE_FRAMES = 140

	private const val BREATHE_COMPLETION_SLACK = 20

	private const val DETOUR_FRAMES = 220

	private const val DETOUR_REACH = 6

	private const val DETOUR_SLACK_TICKS = 30.0

	private const val SHAFT_FRAMES_PER_BLOCK = 6

	private const val PLUNGE_NODES = 12

	private const val BODY_CENTER = 0.3

	private const val LINE_SAMPLE = 0.25

	private val BODY_CORNERS = listOf(-0.3 to -0.3, -0.3 to 0.3, 0.3 to -0.3, 0.3 to 0.3)

	private val HORIZONTAL_SIDES = intArrayOf(0, 1, 4, 5)

	private val SIDE_X = intArrayOf(1, -1, 0, 0, 0, 0)
	private val SIDE_Y = intArrayOf(0, 0, 1, -1, 0, 0)
	private val SIDE_Z = intArrayOf(0, 0, 0, 0, 1, -1)
}
