package com.lambda.pathing.actions.families

import com.lambda.pathing.actions.CellCondition
import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.StanceRules
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.TrajectoryDecision.Neo.Steer
import com.lambda.pathing.actions.control.NeoProgram
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.Medium

object NeoMovement : Movement {
	override val id = MovementId.NEO

	const val MIN_TURN_DEGREES_PER_FRAME = 90.0

	const val DIAGONAL_SHUFFLE_TICKS = 9

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		if (!options.allowNeoJumps || !options.allowJumpCandidates) return@buildList
		for ((fx, fz) in StanceRules.CARDINALS) for ((sx, sz) in sides(fx, fz)) for (variant in VARIANTS) {
			val landing = variant.landing
			add(
				TemplateSpec(
					dx = fx * landing.x + sx * landing.z,
					dy = landing.y,
					dz = fz * landing.x + sz * landing.z,
					movement = id,
					cost = POSITIONING_TICKS + variant.ticks + options.jumpPenaltyTicks,
					conditions = variant.conditions(fx, fz, sx, sz),
					departure = CLEAR,
				)
			)
		}
	}

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> {
		val edge = context.edge
		val origin = edge.from
		if (!CLEAR.matches(context.view, origin.x, origin.y, origin.z)) return emptyList()
		val fast = context.constraints.maxYawDegreesPerFrame >= FULL_TURN_DEGREES_PER_FRAME
		return buildList {
			for ((fx, fz) in StanceRules.CARDINALS) for ((sx, sz) in sides(fx, fz)) for (variant in VARIANTS) {
				val landing = variant.landing
				if (edge.to.y - origin.y != landing.y) continue
				if (edge.to.x - origin.x != fx * landing.x + sx * landing.z || edge.to.z - origin.z != fz * landing.x + sz * landing.z) continue
				if (!variant.conditions(fx, fz, sx, sz).all { it.matches(context.view, origin.x, origin.y, origin.z) }) continue
				val steers = if (fast) variant.steers + variant.fastSteers else variant.steers
				for (steer in steers) add(TrajectoryDecision.Neo(edge.to, fx, fz, sx, sz, steer, variant.diagonal))
			}
		}
	}

	override fun program(context: ProgramContext): ControlProgram =
		NeoProgram(context.decision as TrajectoryDecision.Neo, context)

	override fun completed(context: CompletionContext): Boolean = context.observed.onGround && context.stance == context.decision.step

	override fun transitionFrames(decision: TrajectoryDecision): Int =
		if ((decision as? TrajectoryDecision.Neo)?.diagonal == true) NEO_FRAMES + DIAGONAL_SHUFFLE_TICKS else NEO_FRAMES

	private fun sides(fx: Int, fz: Int) = listOf(-fz to fx, fz to -fx)

	private class Variant(
		val landing: VoxelPos,
		val walls: List<VoxelPos>,
		val ledges: List<VoxelPos>,
		val ticks: Int,
		val lane: List<VoxelPos>,
		val steers: List<Steer>,
		val fastSteers: List<Steer> = emptyList(),
		val diagonal: Boolean = false,
	) {
		private val cached = HashMap<Int, List<CellCondition>>()

		fun conditions(fx: Int, fz: Int, sx: Int, sz: Int): List<CellCondition> = synchronized(cached) {
			cached.getOrPut((fx + 1) * 9 + (fz + 1) * 3 + (sx + 1) + 27 * (sz + 1)) {
				fun at(cell: VoxelPos, predicate: CellPredicate) =
					CellCondition(fx * cell.x + sx * cell.z, cell.y, fz * cell.x + sz * cell.z, predicate)
				buildList {
					walls.forEach { add(at(it, CUBE)) }
					ledges.forEach { add(at(it, OPEN)) }
					add(at(VoxelPos(0, -1, 0), CUBE))
					add(at(VoxelPos(landing.x, landing.y - 1, landing.z), CUBE))
					for (cell in lane) if (cell.x != 0 || cell.y != 0 || cell.z != 0) add(at(cell, CLEAR))
				}
			}
		}
	}

	private fun straight(reach: Int, rise: Int, thickness: Int, ticks: Int, lane: List<VoxelPos>, steers: List<Steer>, fastSteers: List<Steer>) = Variant(
		landing = VoxelPos(reach, rise, 0),
		walls = (1..thickness).flatMap { listOf(VoxelPos(it, 1, 0), VoxelPos(it, 0, 0)) },
		ledges = listOf(VoxelPos(0, -1, 1)),
		ticks = ticks,
		lane = lane,
		steers = steers,
		fastSteers = fastSteers,
	)

	private fun cells(vararg packed: Int): List<VoxelPos> = packed.toList().chunked(3).map { (forward, up, side) -> VoxelPos(forward, up, side) }

	private fun steers(vararg packed: Int): List<Steer> = packed.toList().chunked(4).map { (takeoff, launch, ticks, landing) -> Steer(takeoff, launch, ticks, landing) }

	private val CLEAR = CellPredicate { view, x, y, z -> view.voxel(x, y, z).let { it.fullyPassable && it.medium == Medium.AIR } }

	private val OPEN = CellPredicate { view, x, y, z -> view.voxel(x, y, z).fullyPassable }

	private val CUBE = CellPredicate { view, x, y, z -> view.voxel(x, y, z).let { !it.partial && it.medium == Medium.SOLID } }

	private val VARIANTS = listOf(
		straight(
			reach = 2, rise = 0, thickness = 1, ticks = 13,
			lane = cells(0, 0, 0, 0, 0, 1, 1, 0, 1, 2, 0, 0, 2, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 0, 2, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 1, 1, 3, 1, 2, 3, 1, 2, 2, 0),
			steers = steers(-15, 30, 2, 75, -45, 15, 2, 90, -15, 45, 6, 60, -15, 45, 9, 30, -15, 30, 5, 105, -45, 30, 5, 90),
			fastSteers = steers(150, 15, 6, 135, -15, 15, 5, 135, -90, 15, 5, 120, -45, 15, 5, 120, -165, 15, 6, 135, 150, 0, 3, 90),
		),
		straight(
			reach = 2, rise = 1, thickness = 1, ticks = 10,
			lane = cells(0, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 0, 2, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 0, 2, 2, 1, 1, 3, 1, 2, 3, 1),
			steers = steers(-15, 15, 2, 45, -60, 0, 2, 45, -15, 15, 5, 75, -60, 15, 5, 75, -15, 30, 5, 30, -15, 30, 8, 90),
			fastSteers = steers(-15, 0, 5, 90, -90, 0, 3, 45, -120, 0, 2, 30, -15, 15, 7, 135),
		),
		straight(
			reach = 2, rise = -1, thickness = 1, ticks = 15,
			lane = cells(2, -1, 0, 2, -1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 1, 2, 0, 0, 2, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 0, 2, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 1, 1, 3, 1, 2, 3, 1, 2, 2, 0),
			steers = steers(-15, 30, 4, 90, -15, 45, 7, 105, -45, 30, 3, 90, -45, 45, 7, 105, -15, 15, 1, 90, -75, 15, 2, 90),
			fastSteers = steers(150, 15, 6, 135, -120, 15, 6, 135, -90, 15, 6, 135, -165, 15, 6, 120, -60, 15, 5, 135, -120, 0, 3, 105),
		),
		straight(
			reach = 3, rise = 0, thickness = 1, ticks = 13,
			lane = cells(0, 0, 0, 0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 0, 0, 3, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 1, 3, 1, 0, 3, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 1, 3, 2, 1, 1, 3, 1, 2, 3, 1, 2, 0, 0, 2, 1, 0),
			steers = steers(-60, 0, 6, 45, -15, -15, 3, 60, -30, 0, 7, 60, -90, 0, 6, 45, -60, 0, 7, 90, 0, 0, 7, 45),
			fastSteers = steers(-45, 0, 7, 90, -135, 0, 7, 90, -120, 0, 6, 45, 150, 0, 8, 45, 150, 0, 8, 135, 150, 0, 9, 90),
		),
		straight(
			reach = 3, rise = -1, thickness = 1, ticks = 15,
			lane = cells(
				3, -1, 0, 3, -1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 0, 0, 3, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 1, 3, 1, 1,
				0, 2, 1, 1, 2, 1, 2, 2, 1, 3, 2, 1, 1, 3, 1, 2, 3, 1, 2, 0, 0, 2, 1, 0, 2, -1, 0, 2, -1, 1, 3, 1, 0, 2, 2, 0,
			),
			steers = steers(-15, 30, 8, 45, -15, 30, 2, 45, -60, 0, 2, 75, -30, 30, 5, 30, -45, 30, 2, 45, -30, 0, 2, 75),
			fastSteers = steers(-165, 30, 2, 30, -165, 0, 2, 60, -165, 15, 8, 90, -135, 0, 2, 60, 150, 0, 3, 60, 150, 15, 9, 120),
		),
		straight(
			reach = 3, rise = 0, thickness = 2, ticks = 13,
			lane = cells(0, 0, 0, 0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 0, 0, 3, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 1, 3, 1, 0, 3, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 1, 3, 2, 1, 1, 3, 1, 2, 3, 1),
			steers = steers(-15, 0, 5, 30, -15, 0, 2, 15, -45, 0, 2, 15, -45, 0, 5, 30, -75, 0, 1, 15, 0, 0, 8, 75),
			fastSteers = emptyList(),
		),
		straight(
			reach = 3, rise = -1, thickness = 2, ticks = 15,
			lane = cells(3, -1, 0, 3, -1, 1, 0, 0, 0, 0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 0, 0, 3, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 1, 3, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 1, 3, 2, 1, 1, 3, 1, 2, 3, 1, 2, -1, 1, 3, 1, 0),
			steers = steers(-15, 0, 2, 45, -60, 15, 8, 45, -45, 0, 2, 45, -60, 15, 5, 45, -15, 15, 9, 60, -30, 15, 5, 45),
			fastSteers = steers(-90, 15, 8, 30, -120, 15, 9, 30, -45, 15, 9, 60, -90, 15, 3, 30, -135, 0, 2, 30),
		),
		Variant(
			landing = VoxelPos(2, 0, 2),
			walls = listOf(VoxelPos(1, 1, 1), VoxelPos(1, 0, 1)),
			ledges = listOf(VoxelPos(1, -1, 0), VoxelPos(1, -1, -1)),
			ticks = DIAGONAL_SHUFFLE_TICKS + 13,
			lane = cells(
				0, 0, -1, 0, 0, 0, 1, 0, -1, 1, 0, 0, 2, 0, 0, 2, 0, 1, 2, 0, 2, 3, 0, 1, 3, 0, 2, 0, 1, -1, 0, 1, 0, 1, 1, -1, 1, 1, 0,
				2, 1, 0, 2, 1, 1, 2, 1, 2, 3, 1, 0, 3, 1, 1, 3, 1, 2, 1, 2, 0, 2, 2, 0, 2, 2, 1, 3, 2, 0, 3, 2, 1, 2, 3, 0, 2, 3, 1,
			),
			steers = steers(-30, -30, 3, -90, -15, -30, 3, -90, -45, -30, 3, -90, -30, -30, 3, -105, -30, -30, 4, -105, -30, -30, 4, -90, -45, -30, 4, -90, -75, -30, 4, -90),
			diagonal = true,
		),
	)

	private const val POSITIONING_TICKS = 6.0

	private const val FULL_TURN_DEGREES_PER_FRAME = 180.0

	private const val NEO_FRAMES = 60
}
