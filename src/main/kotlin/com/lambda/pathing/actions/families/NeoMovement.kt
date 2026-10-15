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
import com.lambda.pathing.actions.NeoTerrain
import com.lambda.pathing.actions.control.NeoProgram
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium

object NeoMovement : Movement {
	override val id = MovementId.NEO

	const val MIN_TURN_DEGREES_PER_FRAME = 90.0

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
					cost = variant.ticks + options.jumpPenaltyTicks,
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
		return buildList {
			for ((fx, fz) in StanceRules.CARDINALS) for ((sx, sz) in sides(fx, fz)) for (variant in VARIANTS) {
				val landing = variant.landing
				if (edge.to.y - origin.y != landing.y) continue
				if (edge.to.x - origin.x != fx * landing.x + sx * landing.z || edge.to.z - origin.z != fz * landing.x + sz * landing.z) continue
				if (!variant.conditions(fx, fz, sx, sz).all { it.matches(context.view, origin.x, origin.y, origin.z) }) continue
				val terrain = terrain(context.view, origin, fx, fz, sx, sz, landing)
				for ((heading, margin) in STYLES) add(TrajectoryDecision.Neo(edge.to, fx, fz, sx, sz, terrain, heading, margin))
			}
		}
	}

	override fun program(context: ProgramContext): ControlProgram =
		NeoProgram(context.decision as TrajectoryDecision.Neo, context)

	override fun completed(context: CompletionContext): Boolean = context.observed.onGround && context.stance == context.decision.step

	private fun sides(fx: Int, fz: Int) = listOf(-fz to fx, fz to -fx)

	private fun terrain(view: CoarseVoxelView, origin: Stance, fx: Int, fz: Int, sx: Int, sz: Int, landing: VoxelPos): NeoTerrain {
		val lowest = minOf(landing.y, 0) - 1
		val widest = maxOf(landing.z, 1)
		return NeoTerrain.of(
			-TERRAIN_MARGIN, -TERRAIN_MARGIN, lowest,
			landing.x + 2 * TERRAIN_MARGIN + 1, widest + 2 * TERRAIN_MARGIN + 1, TERRAIN_TOP - lowest + 1,
		) { forward, side, up ->
			val voxel = view.voxel(origin.x + fx * forward + sx * side, origin.y + up, origin.z + fz * forward + sz * side)
			when {
				voxel.fullyPassable && voxel.medium == Medium.AIR && voxel.velocityMultiplier == 1.0 -> NeoTerrain.CLEAR
				!voxel.partial && voxel.medium == Medium.SOLID && !voxel.bouncy -> NeoTerrain.FLOOR
				else -> NeoTerrain.BLOCK
			}
		}
	}

	private class Variant(
		val landing: VoxelPos,
		val walls: List<VoxelPos>,
		val ledges: List<VoxelPos>,
		val ticks: Double,
		val lane: List<VoxelPos>,
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

	private fun straight(reach: Int, rise: Int, thickness: Int, ticks: Double, lane: List<VoxelPos>) = Variant(
		landing = VoxelPos(reach, rise, 0),
		walls = (1..thickness).flatMap { listOf(VoxelPos(it, 1, 0), VoxelPos(it, 0, 0)) },
		ledges = listOf(VoxelPos(0, -1, 1)),
		ticks = ticks,
		lane = lane,
	)

	private fun cells(vararg packed: Int): List<VoxelPos> = packed.toList().chunked(3).map { (forward, up, side) -> VoxelPos(forward, up, side) }

	private val CLEAR = CellPredicate { view, x, y, z -> view.voxel(x, y, z).let { it.fullyPassable && it.medium == Medium.AIR } }

	private val OPEN = CellPredicate { view, x, y, z -> view.voxel(x, y, z).fullyPassable }

	private val CUBE = CellPredicate { view, x, y, z -> view.voxel(x, y, z).let { !it.partial && it.medium == Medium.SOLID } }

	private val VARIANTS = listOf(
		straight(
			reach = 2, rise = 0, thickness = 1, ticks = 16.0,
			lane = cells(0, 0, 1, 1, 0, 1, 2, 0, 0, 2, 0, 1, 3, 0, 0, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 0, 2, 1, 1, 3, 1, 0, 0, 2, 1, 1, 2, 1, 2, 2, 0, 2, 2, 1, 1, 3, 1, 2, 3, 1),
		),
		straight(
			reach = 2, rise = 1, thickness = 1, ticks = 15.0,
			lane = cells(0, 0, 1, 1, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 0, 2, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 0, 2, 2, 1, 1, 3, 1, 2, 3, 1),
		),
		straight(
			reach = 2, rise = -1, thickness = 1, ticks = 17.0,
			lane = cells(2, -1, 0, 2, -1, 1, 0, 0, 1, 1, 0, 1, 2, 0, 0, 2, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 0, 2, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 1, 1, 3, 1),
		),
		straight(
			reach = 3, rise = 0, thickness = 1, ticks = 16.0,
			lane = cells(0, 0, 1, 1, 0, 1, 2, 0, 0, 2, 0, 1, 3, 0, 0, 3, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 0, 2, 1, 1, 3, 1, 0, 3, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 0, 2, 2, 1, 1, 3, 1, 2, 3, 1),
		),
		straight(
			reach = 3, rise = -1, thickness = 1, ticks = 18.0,
			lane = cells(
				2, -1, 0, 2, -1, 1, 3, -1, 0, 3, -1, 1, 0, 0, 1, 1, 0, 1, 2, 0, 0, 2, 0, 1, 3, 0, 0, 3, 0, 1, 0, 1, 0, 0, 1, 1,
				1, 1, 1, 2, 1, 0, 2, 1, 1, 3, 1, 0, 3, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 0, 2, 2, 1, 1, 3, 1, 2, 3, 1,
			),
		),
		straight(
			reach = 3, rise = 0, thickness = 2, ticks = 17.0,
			lane = cells(
				0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 0, 0, 3, 0, 1, 4, 0, 0, 4, 0, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 2, 1, 1, 3, 1, 0,
				3, 1, 1, 4, 1, 0, 4, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 1, 3, 2, 1, 1, 3, 1, 2, 3, 1,
			),
		),
		straight(
			reach = 3, rise = -1, thickness = 2, ticks = 19.0,
			lane = cells(
				3, -1, 0, 3, -1, 1, 4, -1, 0, 4, -1, 1, 0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 0, 0, 3, 0, 1, 4, 0, 0, 4, 0, 1, 0, 1, 0,
				0, 1, 1, 1, 1, 1, 2, 1, 1, 3, 1, 1, 0, 2, 1, 1, 2, 1, 2, 2, 1, 3, 2, 1, 1, 3, 1, 2, 3, 1,
			),
		),
		Variant(
			landing = VoxelPos(2, 0, 2),
			walls = listOf(VoxelPos(1, 1, 1), VoxelPos(1, 0, 1)),
			ledges = listOf(VoxelPos(0, -1, 1)),
			ticks = 16.0,
			lane = cells(
				0, 0, 1, 0, 0, 2, 1, 0, 2, 1, 0, 3, 2, 0, 2, 2, 0, 3, 0, 1, 0, 0, 1, 1, 0, 1, 2, 0, 1, 3, 1, 1, 2, 1, 1, 3,
				2, 1, 2, 2, 1, 3, 0, 2, 1, 0, 2, 2, 0, 2, 3, 1, 2, 2, 1, 2, 3, 0, 3, 2, 1, 3, 2,
			),
		),
	)

	private val STYLES = listOf(90.0 to ROBUST_MARGIN, 75.0 to ROBUST_MARGIN, 75.0 to 0.0)

	private const val ROBUST_MARGIN = 0.04

	private const val TERRAIN_MARGIN = 2

	private const val TERRAIN_TOP = 3
}
