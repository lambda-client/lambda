package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.StampProgram
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CellClass
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.TerrainBits
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import net.minecraft.util.math.BlockPos

internal class EdgeStamper(private val view: CoarseVoxelView, private val moves: SimpleMoveLibrary, private val standable: StanceMemo) {
	private val bits = TerrainBits(view)
	private val stanceCells: IntArray
	private val rowY: IntArray
	private val rowZ: IntArray
	private val cellRow: IntArray
	private val cellDx: IntArray
	private val programs: Array<Indexed?>
	private val slotCount: Int
	private val groupMasks: LongArray
	private val cellFootprints: Array<IntArray>
	private val tainted: IntArray
	private val stanceRows: Int

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
		val containing = ArrayList<ArrayList<Int>>()
		var footprints = 0
		fun footprint(cells: List<VoxelPos>): Int {
			for (cell in cells.distinct()) {
				val index = id(cell)
				while (containing.size <= index) containing += ArrayList<Int>()
				containing[index] += footprints
			}
			return footprints++
		}
		fun clause(clause: StampProgram.Clause) = IndexedClause(ids(clause.cells), clause.truth, clause.classSets)
		stanceCells = ids(STANCE_CELLS)
		stanceRows = rows.size

		var slots = 0
		var groups = 0
		programs = Array(moves.templates.size) { index ->
			val program = moves.stampPrograms[index] ?: return@Array null
			val candidates = program.candidates
			val bendCount = program.bendGroups.sumOf { it.size }
			Indexed(
				clauses = program.clauses.map(::clause).toTypedArray(),
				after = ids(program.after),
				candidates = candidates,
				scans = candidates.orEmpty().map { candidate ->
					val clips = candidate.clipCells.toSet()
					IntArray(candidate.cells.size) { (id(candidate.cells[it]) shl 1) or (if (candidate.cells[it] in clips) 1 else 0) }
				}.toTypedArray(),
				clips = candidates.orEmpty().map { ids(it.clipCells) }.toTypedArray(),
				bonks = candidates.orEmpty().map { ids(it.bonkCells) }.toTypedArray(),
				footprints = candidates.orEmpty().map { footprint(it.cells) }.toIntArray(),
				behind = program.behind?.let(::clause),
				firstClips = ids(candidates?.firstOrNull()?.clipCells.orEmpty()),
				bendColumns = program.bendColumns,
				bendClips = program.bendGroups.map { group -> group.map { ids(it.clipCells) }.toTypedArray() }.toTypedArray(),
				bendCells = program.bendGroups.map { group -> group.map { ids(it.cells) }.toTypedArray() }.toTypedArray(),
				bendFootprints = program.bendGroups.map { group -> group.map { footprint(it.cells) }.toIntArray() }.toTypedArray(),
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
		groupMasks = LongArray(groups)
		rowY = IntArray(rows.size) { rows[it].first }
		rowZ = IntArray(rows.size) { rows[it].second }
		cellRow = cellRows.toIntArray()
		cellDx = cellDxs.toIntArray()
		cellFootprints = Array(cellRow.size) { containing.getOrNull(it)?.toIntArray() ?: IntArray(0) }
		tainted = IntArray(footprints)
	}

	private val stamped = programs.indices.filter { programs[it] != null }.toIntArray()
	private val unstamped = programs.indices.filter { programs[it] == null && moves.templates[it].movement != MovementId.BOUNCE }.toIntArray()
	private val words = Array(CellClass.PLANES) { LongArray(rowY.size * 3) }
	private val stamps = Long2ObjectLinkedOpenHashMap<LongArray>()
	private var gate = 0L
	private var specials = false
	private var generation = 0
	private val solids = LongArray(cellRow.size)
	private val hazards = LongArray(cellRow.size)
	private val climbs = LongArray(cellRow.size)
	private val waters = LongArray(cellRow.size)
	private val specialCells = LongArray(cellRow.size)

	fun synchronize() {
		if (bits.synchronize()) stamps.clear()
	}

	fun invalidate() {
		bits.clear()
		stamps.clear()
	}

	fun edge(template: MotionTemplate, origin: Stance): CoarseEdge? {
		synchronize()
		val program = programs[template.id.value] ?: return template.edge(view, origin)
		val stamp = stamp(origin.x shr 6, origin.y, origin.z)
		val bit = 1L shl origin.x
		if (stamp[program.slot] and bit != 0L) return template.edge(view, origin)
		if (program.candidates == null) {
			return if (stamp[program.slot + 1] and bit != 0L) template.stampedEdge(view, origin, null, false) else null
		}
		val candidate = chosen(program, stamp, bit) ?: return null
		return template.stampedEdge(view, origin, candidate, stamp[program.slot + 1] and bit != 0L)
	}

	fun row(word: Int, y: Int, z: Int): RowEdges {
		val stamp = compute(word, y, z)
		val stances = gate
		if (stances == 0L) return RowEdges.EMPTY
		val builder = RowEdges.Builder()
		for (index in stamped) stampedEdges(builder, index, programs[index]!!, stamp, stances, word, y, z)
		for (index in unstamped) addEdges(builder, index, 0L, 0L, 0L, stances, word, y, z)
		return builder.build(moves.groups.rank)
	}

	private fun stampedEdges(builder: RowEdges.Builder, index: Int, program: Indexed, stamp: LongArray, stances: Long, word: Int, y: Int, z: Int) {
		val exact = stamp[program.slot] and stances
		val candidates = program.candidates
		if (candidates == null) {
			val edges = stamp[program.slot + 1] and stances
			return addEdges(builder, index, edges, edges, 0L, exact, word, y, z)
		}
		var edges = 0L
		var standing = 0L
		var irregular = 0L
		val behind = stamp[program.slot + 1]
		for (candidate in candidates.indices) {
			val clear = stamp[program.slot + 2 + candidate] and stances
			edges = edges or clear
			standing = standing or (clear and standingMask(candidates[candidate], behind))
		}
		val planeSlot = program.slot + 2 + candidates.size
		var bent = 0L
		for (plane in 0 until program.planes) bent = bent or stamp[planeSlot + plane]
		bent = bent and stances
		while (bent != 0L) {
			val bit = java.lang.Long.numberOfTrailingZeros(bent)
			bent = bent and (bent - 1)
			val mask = 1L shl bit
			var pair = 0
			for (plane in 0 until program.planes) if (stamp[planeSlot + plane] and mask != 0L) pair = pair or (1 shl plane)
			val bend = program.bends[pair - 1]
			edges = edges or mask
			irregular = irregular or mask
			if (bend.standingStartOn(behind and mask != 0L)) standing = standing or mask
			builder.irregular(index, bit, bend.ticks)
		}
		addEdges(builder, index, edges, standing, irregular, exact, word, y, z)
	}

	private fun addEdges(builder: RowEdges.Builder, index: Int, stampedEdges: Long, stampedStanding: Long, stampedIrregular: Long, exact: Long, word: Int, y: Int, z: Int) {
		var edges = stampedEdges
		var standing = stampedStanding
		var irregular = stampedIrregular
		val template = moves.templates[index]
		var pending = exact
		while (pending != 0L) {
			val bit = java.lang.Long.numberOfTrailingZeros(pending)
			pending = pending and (pending - 1)
			val edge = template.edge(view, Stance((word shl 6) + bit, y, z)) ?: continue
			val mask = 1L shl bit
			edges = edges or mask
			irregular = irregular or mask
			if (edge.standingStart) standing = standing or mask
			builder.irregular(index, bit, edge.lowerBoundTicks)
		}
		if (edges != 0L) builder.add(index, edges, standing, irregular)
	}

	private fun standingMask(candidate: StampProgram.Candidate, behind: Long): Long =
		(if (candidate.standingStart) behind.inv() else 0L) or (if (candidate.standingStartBehind) behind else 0L)

	private fun chosen(program: Indexed, stamp: LongArray, bit: Long): StampProgram.Candidate? {
		val candidates = program.candidates!!
		for (index in candidates.indices) if (stamp[program.slot + 2 + index] and bit != 0L) return candidates[index]
		val planeSlot = program.slot + 2 + candidates.size
		var pair = 0
		for (plane in 0 until program.planes) if (stamp[planeSlot + plane] and bit != 0L) pair = pair or (1 shl plane)
		return if (pair == 0) null else program.bends[pair - 1]
	}

	private fun stamp(word: Int, y: Int, z: Int): LongArray {
		val key = BlockPos.asLong(word, y, z)
		stamps.getAndMoveToLast(key)?.let { return it }
		val stamp = compute(word, y, z)
		stamps.putAndMoveToLast(key, stamp)
		if (stamps.size > MAX_STAMPS) stamps.removeFirst()
		return stamp
	}

	private fun compute(word: Int, y: Int, z: Int): LongArray {
		val stamp = LongArray(slotCount)
		specials = false
		fetch(word, y, z, 0, stanceRows)
		val (below2, below, body, head) = stanceCells
		for (cell in stanceCells) load(cell)
		val gated = (solid(below) and (blocking(body) or blocking(head)).inv()) or climb(body) or (water(body) and blocking(head).inv()) or
				special(below2) or special(below) or special(body) or special(head)
		var stances = 0L
		var remaining = gated
		while (remaining != 0L) {
			val bit = java.lang.Long.numberOfTrailingZeros(remaining)
			remaining = remaining and (remaining - 1)
			if (standable.isStance(Stance((word shl 6) + bit, y, z))) stances = stances or (1L shl bit)
		}
		gate = stances
		if (stances == 0L) return stamp
		fetch(word, y, z, stanceRows, rowY.size)
		for (cell in cellRow.indices) load(cell)
		generation++
		if (specials) taint()
		for (program in programs) {
			if (program == null) continue
			var passing = stances
			var undecided = 0L
			for (clause in program.clauses) {
				var special = 0L
				for (cell in clause.cells) special = special or special(cell)
				undecided = undecided or special
				passing = passing and (mask(clause) or special)
				if (passing == 0L) break
			}
			var fallback = passing and undecided
			var conditions = passing and undecided.inv()
			if (conditions != 0L) {
				var after = 0L
				for (cell in program.after) after = after or special(cell)
				fallback = fallback or (conditions and after)
				conditions = conditions and after.inv()
			}
			stamp[program.slot] = fallback
			if (conditions == 0L) continue
			val candidates = program.candidates
			if (candidates == null) {
				stamp[program.slot + 1] = conditions
				continue
			}
			if (candidates.isEmpty()) continue
			stamp[program.slot + 1] = mask(program.behind!!)
			var open = conditions
			for (index in candidates.indices) {
				if (open == 0L) break
				val bonks = program.bonks[index]
				var bonked = if (bonks.isEmpty()) -1L else 0L
				for (cell in bonks) bonked = bonked or solid(cell)
				if (tainted[program.footprints[index]] != generation) {
					var clipped = 0L
					for (cell in program.clips[index]) {
						clipped = clipped or (blocking(cell) and open)
						if (clipped == open) break
					}
					val clear = open and clipped.inv() and bonked
					stamp[program.slot + 2 + index] = clear
					open = open and clear.inv()
					continue
				}
				var scanning = open
				var clipped = 0L
				for (entry in program.scans[index]) {
					val cell = entry ushr 1
					val special = special(cell) and scanning
					fallback = fallback or special
					scanning = scanning and special.inv()
					if (entry and 1 != 0) {
						val hit = blocking(cell) and scanning
						clipped = clipped or hit
						scanning = scanning and hit.inv()
					}
					if (scanning == 0L) break
				}
				val clear = scanning and bonked
				stamp[program.slot + 2 + index] = clear
				open = clipped or (scanning and clear.inv())
			}
			stamp[program.slot] = if (open != 0L) fallback or bend(program, open, stamp) else fallback
		}
		return stamp
	}

	private fun bend(program: Indexed, needing: Long, stamp: LongArray): Long {
		val groups = program.bendClips.size
		groupMasks.fill(0L, 0, groups)
		var remaining = needing
		for (index in program.firstClips.indices) {
			val hit = blocking(program.firstClips[index]) and remaining
			if (hit == 0L) continue
			val group = program.bendColumns[index]
			groupMasks[group] = groupMasks[group] or hit
			remaining = remaining and hit.inv()
			if (remaining == 0L) break
		}
		var fallback = remaining
		val planeSlot = program.slot + 2 + program.candidates!!.size
		for (group in 0 until groups) {
			var open = groupMasks[group]
			val clips = program.bendClips[group]
			val cells = program.bendCells[group]
			for (index in clips.indices) {
				if (open == 0L) break
				var clipped = 0L
				for (cell in clips[index]) {
					clipped = clipped or (blocking(cell) and open)
					if (clipped == open) break
				}
				var through = open and clipped.inv()
				if (through != 0L && tainted[program.bendFootprints[group][index]] == generation) {
					var special = 0L
					for (cell in cells[index]) special = special or special(cell)
					fallback = fallback or (through and special)
					through = through and special.inv()
				}
				val clear = through
				if (clear != 0L) {
					val pair = program.groupOffset[group] + index + 1
					for (plane in 0 until program.planes) {
						if (pair and (1 shl plane) != 0) stamp[planeSlot + plane] = stamp[planeSlot + plane] or clear
					}
				}
				open = clipped
			}
		}
		return fallback
	}

	private fun mask(clause: IndexedClause): Long {
		val sets = clause.classSets
		if (sets != null) {
			var mask = -1L
			for (index in clause.cells.indices) {
				if (sets[index] == ALL_CLASSES) continue
				mask = mask and among(sets[index], clause.cells[index])
				if (mask == 0L) break
			}
			return mask
		}
		var mask = 0L
		for (assignment in clause.truth.indices) {
			if (!clause.truth[assignment]) continue
			var term = -1L
			for (index in clause.cells.indices) {
				term = term and among(1 shl StampProgram.Clause.classOf(assignment, index), clause.cells[index])
			}
			mask = mask or term
		}
		return mask
	}

	private fun among(classes: Int, cell: Int): Long {
		var mask = 0L
		if (classes and EMPTY_CLASS != 0) mask = mask or (solid(cell) or hazard(cell) or climb(cell) or water(cell)).inv()
		if (classes and SOLID_CLASS != 0) mask = mask or solid(cell)
		if (classes and HAZARD_CLASS != 0) mask = mask or hazard(cell)
		if (classes and CLIMB_CLASS != 0) mask = mask or climb(cell)
		if (classes and WATER_CLASS != 0) mask = mask or water(cell)
		return mask
	}

	private fun load(cell: Int) {
		solids[cell] = shifted(words[CellClass.SOLID.plane], cell)
		hazards[cell] = shifted(words[CellClass.HAZARD.plane], cell)
		climbs[cell] = shifted(words[CellClass.CLIMB.plane], cell)
		waters[cell] = shifted(words[CellClass.WATER.plane], cell)
		specialCells[cell] = if (specials) shifted(words[CellClass.SPECIAL.plane], cell) else 0L
	}

	private fun fetch(word: Int, y: Int, z: Int, from: Int, until: Int) {
		for (row in from until until) bits.fetch(word, y + rowY[row], z + rowZ[row], words, row * 3)
		val special = words[CellClass.SPECIAL.plane]
		for (index in from * 3 until until * 3) if (special[index] != 0L) specials = true
	}

	private fun taint() {
		for (cell in cellRow.indices) {
			if (specialCells[cell] == 0L) continue
			for (footprint in cellFootprints[cell]) tainted[footprint] = generation
		}
	}

	private fun solid(cell: Int): Long = solids[cell]

	private fun hazard(cell: Int): Long = hazards[cell]

	private fun climb(cell: Int): Long = climbs[cell]

	private fun water(cell: Int): Long = waters[cell]

	private fun special(cell: Int): Long = specialCells[cell]

	private fun blocking(cell: Int): Long = solids[cell] or hazards[cell] or waters[cell]

	private fun shifted(words: LongArray, cell: Int): Long {
		val base = cellRow[cell] * 3 + 1
		val dx = cellDx[cell]
		return when {
			dx == 0 -> words[base]
			dx > 0 -> (words[base] ushr dx) or (words[base + 1] shl (64 - dx))
			else -> (words[base] shl -dx) or (words[base - 1] ushr (64 + dx))
		}
	}

	private class IndexedClause(val cells: IntArray, val truth: BooleanArray, val classSets: IntArray?)

	private class Indexed(
		val clauses: Array<IndexedClause>,
		val after: IntArray,
		val candidates: List<StampProgram.Candidate>?,
		val scans: Array<IntArray>,
		val clips: Array<IntArray>,
		val bonks: Array<IntArray>,
		val footprints: IntArray,
		val behind: IndexedClause?,
		val firstClips: IntArray,
		val bendColumns: IntArray,
		val bendClips: Array<Array<IntArray>>,
		val bendCells: Array<Array<IntArray>>,
		val bendFootprints: Array<IntArray>,
		val groupOffset: IntArray,
		val bends: List<StampProgram.Candidate>,
		val planes: Int,
		val slot: Int,
	) {
		val slotsUsed: Int get() = 2 + (candidates?.size ?: 0) + planes
	}

	private companion object {
		const val MAX_STAMPS = 1_024

		const val EMPTY_CLASS = 1
		const val SOLID_CLASS = 2
		const val HAZARD_CLASS = 4
		const val CLIMB_CLASS = 8
		const val WATER_CLASS = 16
		const val ALL_CLASSES = 31

		val STANCE_CELLS = listOf(VoxelPos(0, -2, 0), VoxelPos(0, -1, 0), VoxelPos(0, 0, 0), VoxelPos(0, 1, 0))
	}
}
