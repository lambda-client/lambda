package com.lambda.pathing.coarse

import com.lambda.pathing.actions.StampProgram
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.VoxelPos

internal class StampLayout(moves: SimpleMoveLibrary) {
	val stanceCells: IntArray
	val rowY: IntArray
	val rowZ: IntArray
	val cellRow: IntArray
	val cellDx: IntArray
	val programs: Array<Program?>
	val slotCount: Int
	val clauseCount: Int
	val groupCount: Int
	val stanceRows: Int

	init {
		val cellIds = HashMap<VoxelPos, Int>()
		val rowIds = HashMap<Pair<Int, Int>, Int>()
		val rows = ArrayList<Pair<Int, Int>>()
		val cellRows = ArrayList<Int>()
		val cellDxs = ArrayList<Int>()
		fun id(cell: VoxelPos): Int = cellIds.getOrPut(cell) {
			cellRows += rowIds.getOrPut(cell.y to cell.z) { rows += cell.y to cell.z; rows.lastIndex }
			cellDxs += cell.x
			cellRows.lastIndex
		}
		fun ids(cells: Collection<VoxelPos>) = cells.map(::id).toIntArray()
		val clauseIds = HashMap<List<Any>, Int>()
		fun clause(clause: StampProgram.Clause): Clause {
			val cells = ids(clause.cells)
			val id = clauseIds.getOrPut(listOf(cells.toList(), clause.truth.toList())) { clauseIds.size }
			return Clause(cells, clause.truth, clause.classSets, id)
		}
		stanceCells = ids(STANCE_CELLS)
		stanceRows = rows.size

		var slots = 0
		var groups = 0
		programs = Array(moves.templates.size) { index ->
			val program = moves.stampPrograms[index] ?: return@Array null
			val candidates = program.candidates
			val bendCount = program.bendGroups.sumOf { it.size }
			Program(
				clauses = program.clauses.map(::clause).toTypedArray(),
				after = ids(program.after),
				candidates = candidates,
				scans = candidates.orEmpty().map { candidate ->
					val clips = candidate.clipCells.toSet()
					IntArray(candidate.cells.size) { (id(candidate.cells[it]) shl 1) or (if (candidate.cells[it] in clips) 1 else 0) }
				}.toTypedArray(),
				clips = candidates.orEmpty().map { ids(it.clipCells) }.toTypedArray(),
				bonks = candidates.orEmpty().map { ids(it.bonkCells) }.toTypedArray(),
				behind = program.behind?.let(::clause),
				firstClips = ids(candidates?.firstOrNull()?.clipCells.orEmpty()),
				bendColumns = program.bendColumns,
				bendClips = program.bendGroups.map { group -> group.map { ids(it.clipCells) }.toTypedArray() }.toTypedArray(),
				bendCells = program.bendGroups.map { group -> group.map { ids(it.cells) }.toTypedArray() }.toTypedArray(),
				groupOffset = program.bendGroups.runningFold(0) { offset, group -> offset + group.size }.toIntArray(),
				bends = program.bendGroups.flatten(),
				planes = 32 - Integer.numberOfLeadingZeros(bendCount),
				slot = slots,
			).also {
				slots += it.slotsUsed
				groups = maxOf(groups, program.bendGroups.size)
			}
		}
		slotCount = slots
		clauseCount = clauseIds.size
		groupCount = groups
		rowY = IntArray(rows.size) { rows[it].first }
		rowZ = IntArray(rows.size) { rows[it].second }
		cellRow = cellRows.toIntArray()
		cellDx = cellDxs.toIntArray()
	}

	val stamped: IntArray = programs.indices.filter { programs[it] != null }.toIntArray()

	val unstamped: IntArray = programs.indices.filter { programs[it] == null && moves.templates[it].movement != MovementId.BOUNCE }.toIntArray()

	class Clause(val cells: IntArray, val truth: BooleanArray, val classSets: IntArray?, val id: Int)

	class Program(
		val clauses: Array<Clause>,
		val after: IntArray,
		val candidates: List<StampProgram.Candidate>?,
		val scans: Array<IntArray>,
		val clips: Array<IntArray>,
		val bonks: Array<IntArray>,
		val behind: Clause?,
		val firstClips: IntArray,
		val bendColumns: IntArray,
		val bendClips: Array<Array<IntArray>>,
		val bendCells: Array<Array<IntArray>>,
		val groupOffset: IntArray,
		val bends: List<StampProgram.Candidate>,
		val planes: Int,
		val slot: Int,
	) {
		val slotsUsed: Int get() = 2 + (candidates?.size ?: 0) + planes
	}

	private companion object {
		val STANCE_CELLS = listOf(VoxelPos(0, -2, 0), VoxelPos(0, -1, 0), VoxelPos(0, 0, 0), VoxelPos(0, 1, 0))
	}
}
