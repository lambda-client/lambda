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
	val clausesByCell: Array<IntArray>
	val clauses: Array<Clause>
	val programNeeds: IntArray
	val clauseStart: IntArray
	val clauseIds: IntArray
	val clauseCellStart: IntArray
	val clauseCells: IntArray
	val clauseSets: IntArray

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
		val diagrams = HashMap<Int, ClauseDiagram>()
		val cellsOfClauses = ArrayList<IntArray>()
		fun clause(clause: StampProgram.Clause): Clause {
			val cells = ids(clause.cells)
			val id = clauseIds.getOrPut(listOf(cells.toList(), clause.truth.toList())) { cellsOfClauses += cells; clauseIds.size }
			val diagram = if (clause.classSets != null) null else diagrams.getOrPut(id) { ClauseDiagram.of(clause.truth, cells.size) }
			return Clause(cells, clause.classSets, id, diagram, needsOf(clause.truth, cells.size))
		}
		stanceCells = ids(STANCE_CELLS)
		stanceRows = rows.size

		var slots = 0
		var groups = 0
		programs = Array(moves.templates.size) { index ->
			val program = moves.stampPrograms[index] ?: return@Array null
			val candidates = program.candidates
			val bendCount = program.bendGroups.sumOf { it.size }
			val clauses = program.clauses.map(::clause).toTypedArray()
			Program(
				clauses = clauses,
				needs = clauses.fold(0) { needs, clause -> needs or clause.needs },
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
		val byId = arrayOfNulls<Clause>(clauseCount)
		for (program in programs) program?.clauses?.forEach { byId[it.id] = byId[it.id] ?: it }
		clauses = Array(clauseCount) { byId[it] ?: Clause(IntArray(0), null, it, null, 0) }
		programNeeds = IntArray(programs.size) { programs[it]?.needs ?: 0 }
		clauseStart = IntArray(programs.size + 1)
		for (index in programs.indices) clauseStart[index + 1] = clauseStart[index] + (programs[index]?.clauses?.size ?: 0)
		this.clauseIds = IntArray(clauseStart[programs.size])
		for (index in programs.indices) programs[index]?.clauses?.forEachIndexed { position, clause -> this.clauseIds[clauseStart[index] + position] = clause.id }
		clauseCellStart = IntArray(clauseCount + 1)
		for (id in 0 until clauseCount) clauseCellStart[id + 1] = clauseCellStart[id] + (if (clauses[id].classSets != null) clauses[id].cells.size else 0)
		clauseCells = IntArray(clauseCellStart[clauseCount])
		clauseSets = IntArray(clauseCellStart[clauseCount])
		for (id in 0 until clauseCount) {
			val sets = clauses[id].classSets ?: continue
			sets.copyInto(clauseSets, clauseCellStart[id])
			clauses[id].cells.copyInto(clauseCells, clauseCellStart[id])
		}
		groupCount = groups
		rowY = IntArray(rows.size) { rows[it].first }
		rowZ = IntArray(rows.size) { rows[it].second }
		cellRow = cellRows.toIntArray()
		cellDx = cellDxs.toIntArray()
		val byCell = Array(cellRow.size) { ArrayList<Int>() }
		for ((id, cells) in cellsOfClauses.withIndex()) for (cell in cells.distinct()) byCell[cell] += id
		clausesByCell = Array(cellRow.size) { byCell[it].toIntArray() }
	}

	val stamped: IntArray = programs.indices.filter { programs[it] != null }.toIntArray()

	val unstamped: IntArray = programs.indices.filter { programs[it] == null && moves.templates[it].movement != MovementId.BOUNCE }.toIntArray()

	class Clause(val cells: IntArray, val classSets: IntArray?, val id: Int, val diagram: ClauseDiagram?, val needs: Int)

	class Program(
		val clauses: Array<Clause>,
		val needs: Int,
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

	companion object {
		const val NEEDS_CLIMB = 1

		const val NEEDS_WATER = 2

		private val STANCE_CELLS = listOf(VoxelPos(0, -2, 0), VoxelPos(0, -1, 0), VoxelPos(0, 0, 0), VoxelPos(0, 1, 0))

		private fun needsOf(truth: BooleanArray, cells: Int): Int {
			var needs = NEEDS_CLIMB or NEEDS_WATER
			var satisfiable = false
			for (assignment in truth.indices) {
				if (!truth[assignment]) continue
				satisfiable = true
				var classes = 0
				for (cell in 0 until cells) classes = classes or (1 shl StampProgram.Clause.classOf(assignment, cell))
				if (classes and (1 shl StampProgram.CLIMB_CLASS) == 0) needs = needs and NEEDS_CLIMB.inv()
				if (classes and (1 shl StampProgram.WATER_CLASS) == 0) needs = needs and NEEDS_WATER.inv()
				if (needs == 0) break
			}
			return if (satisfiable) needs else 0
		}
	}
}

internal class ClauseDiagram private constructor(
	val root: Int,
	private val levels: IntArray,
	private val starts: IntArray,
	private val sets: IntArray,
	private val targets: IntArray,
) {
	fun level(node: Int): Int = levels[node]

	fun firstEdge(node: Int): Int = starts[node]

	fun endEdge(node: Int): Int = starts[node + 1]

	fun classes(edge: Int): Int = sets[edge]

	fun target(edge: Int): Int = targets[edge]

	companion object {
		const val TRUE = -1
		const val FALSE = -2

		fun of(truth: BooleanArray, cells: Int): ClauseDiagram {
			val classes = StampProgram.CLASSES
			val levels = ArrayList<Int>()
			val starts = ArrayList<Int>()
			val sets = ArrayList<Int>()
			val targets = ArrayList<Int>()
			val unique = HashMap<List<Int>, Int>()
			fun build(level: Int, assignment: Int): Int {
				if (level == cells) return if (truth[assignment]) TRUE else FALSE
				val children = IntArray(classes) { build(level + 1, assignment or (it shl (StampProgram.CLASS_BITS * level))) }
				if (children.all { it == children[0] }) return children[0]
				val key = listOf(level) + children.toList()
				unique[key]?.let { return it }
				val node = levels.size
				levels += level
				starts += sets.size
				for (child in children.distinct()) {
					if (child == FALSE) continue
					var set = 0
					for (index in children.indices) if (children[index] == child) set = set or (1 shl index)
					sets += set
					targets += child
				}
				unique[key] = node
				return node
			}

			val root = build(0, 0)
			starts += sets.size
			return ClauseDiagram(root, levels.toIntArray(), starts.toIntArray(), sets.toIntArray(), targets.toIntArray())
		}
	}
}
