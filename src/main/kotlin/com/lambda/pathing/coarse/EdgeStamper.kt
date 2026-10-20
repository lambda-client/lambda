package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.actions.StampProgram
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CellClass
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.TerrainBits
import com.lambda.pathing.world.TerrainWatch
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import net.minecraft.util.math.BlockPos
import kotlin.math.abs

internal class EdgeStamper(private val view: CoarseVoxelView, private val moves: SimpleMoveLibrary, private val standable: StanceMemo) {
	private val bits = TerrainBits(view)
	private val watch = TerrainWatch(view)
	private val layout = moves.stampLayout
	private val stanceCells = layout.stanceCells
	private val rowY = layout.rowY
	private val rowZ = layout.rowZ
	private val cellRow = layout.cellRow
	private val cellDx = layout.cellDx
	private val programs = layout.programs
	private val stanceRows = layout.stanceRows
	private val groupMasks = LongArray(layout.groupCount)

	private val stamped = layout.stamped
	private val unstamped = layout.unstamped.filter { moves.templates[it].movement != MovementId.HONEY_SLIDE }.toIntArray()
	private val honeyed = layout.unstamped.filter { moves.templates[it].movement == MovementId.HONEY_SLIDE }.toIntArray()
	private val honeyReach = honeyed.maxOfOrNull { maxOf(abs(moves.templates[it].dx), abs(moves.templates[it].dz)) + 1 } ?: 0
	private val honeyDepth = honeyed.minOfOrNull { moves.templates[it].dy } ?: 0
	private val landings = Long2LongOpenHashMap()
	private val words = Array(CellClass.PLANES) { LongArray(rowY.size * 3) }
	private val stamps = Long2ObjectLinkedOpenHashMap<LongArray>()
	private var gate = 0L
	private var specials = false
	private var climbing = false
	private var wet = false
	private var generation = 0
	private val solids = LongArray(cellRow.size)
	private val hazards = LongArray(cellRow.size)
	private val climbs = LongArray(cellRow.size)
	private val waters = LongArray(cellRow.size)
	private val specialCells = LongArray(cellRow.size)
	private val blockings = LongArray(cellRow.size)
	private val empties = LongArray(cellRow.size)
	private val scratch = LongArray(layout.slotCount)
	private val clauseMasks = LongArray(layout.clauseCount)
	private val clauseSpecials = LongArray(layout.clauseCount)
	private val clauseGenerations = IntArray(layout.clauseCount)
	private val active = IntArray(programs.size)
	private var activeCount = 0
	private val clausesByCell = layout.clausesByCell
	private val clauses = layout.clauses
	private val programNeeds = layout.programNeeds
	private val clauseStart = layout.clauseStart
	private val clauseIds = layout.clauseIds
	private val clauseCellStart = layout.clauseCellStart
	private val clauseCells = layout.clauseCells
	private val clauseSets = layout.clauseSets
	private val irregulars = LongArray(cellRow.size)
	private var irregularLanes = 0L
	private var hazardous = false

	fun synchronize() = watch.poll(::invalidate) { box ->
		bits.forget(box)
		forgetStamps(box)
		forgetLandings(box)
	}

	fun invalidate() {
		bits.clear()
		stamps.clear()
		landings.clear()
	}

	private fun forgetLandings(changed: CellBox) {
		if (landings.isEmpty()) return
		for (word in ((changed.xs.first - STANCE_REACH) shr 6)..((changed.xs.last + STANCE_REACH) shr 6)) {
			for (y in changed.ys.first - STANCE_REACH - 1..changed.ys.last + STANCE_REACH + 2) {
				for (z in changed.zs.first - STANCE_REACH..changed.zs.last + STANCE_REACH) landings.remove(BlockPos.asLong(word, y, z))
			}
		}
	}

	private fun stancesAt(word: Int, y: Int, z: Int): Long {
		val key = BlockPos.asLong(word, y, z)
		if (landings.containsKey(key)) return landings.get(key)
		return candidates(word, y, z).also { landings.put(key, it) }
	}

	private fun landing(word: Int, y: Int, z: Int, template: MotionTemplate): Long {
		val first = (word shl 6) + template.dx
		val low = stancesAt(first shr 6, y + template.dy, z + template.dz)
		val offset = first and 63
		if (offset == 0) return low
		return (low ushr offset) or (stancesAt((first shr 6) + 1, y + template.dy, z + template.dz) shl (64 - offset))
	}

	private fun forgetStamps(changed: CellBox) {
		val origins = moves.originsReading(changed, view).grown(-STANCE_REACH, STANCE_REACH, -STANCE_REACH - 1, STANCE_REACH + 1, -STANCE_REACH, STANCE_REACH)
		val keys = stamps.keys.iterator()
		while (keys.hasNext()) {
			val key = keys.nextLong()
			val minX = BlockPos.unpackLongX(key) shl 6
			val y = BlockPos.unpackLongY(key)
			val z = BlockPos.unpackLongZ(key)
			if (origins.overlaps(minX, y, z, minX + 63, y, z)) keys.remove()
		}
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
		val stamp = compute(word, y, z, scratch)
		val stances = gate
		if (stances == 0L) return RowEdges.EMPTY
		val builder = RowEdges.Builder()
		for (position in 0 until activeCount) active[position].let { stampedEdges(builder, it, programs[it]!!, stamp, stances) }
		clearActive(stamp)
		for (index in unstamped) addEdges(builder, index, 0L, 0L, 0L, stances and landing(word, y, z, moves.templates[index]))
		if (honeyed.isNotEmpty() && view.mayContainHoney((word shl 6) - honeyReach, y + honeyDepth, z - honeyReach, (word shl 6) + 63 + honeyReach, y + HONEY_TOP, z + honeyReach)) {
			for (index in honeyed) addEdges(builder, index, 0L, 0L, 0L, stances)
		}
		return builder.build(moves.groups)
	}

	private fun clearActive(stamp: LongArray) {
		for (position in 0 until activeCount) {
			val program = programs[active[position]]!!
			stamp.fill(0L, program.slot, program.slot + program.slotsUsed)
		}
	}

	private fun stampedEdges(builder: RowEdges.Builder, index: Int, program: StampLayout.Program, stamp: LongArray, stances: Long) {
		val exact = stamp[program.slot] and stances
		val candidates = program.candidates
		if (candidates == null) {
			val edges = stamp[program.slot + 1] and stances
			return addEdges(builder, index, edges, edges, 0L, exact)
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
		addEdges(builder, index, edges, standing, irregular, exact)
	}

	private fun addEdges(builder: RowEdges.Builder, index: Int, edges: Long, standing: Long, irregular: Long, exact: Long) {
		if (edges != 0L || exact != 0L) builder.add(index, edges, standing, irregular, exact)
	}

	private fun standingMask(candidate: StampProgram.Candidate, behind: Long): Long =
		(if (candidate.standingStart) behind.inv() else 0L) or (if (candidate.standingStartBehind) behind else 0L)

	private fun chosen(program: StampLayout.Program, stamp: LongArray, bit: Long): StampProgram.Candidate? {
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
		val stamp = compute(word, y, z, LongArray(layout.slotCount))
		stamps.putAndMoveToLast(key, stamp)
		if (stamps.size > MAX_STAMPS) stamps.removeFirst()
		return stamp
	}

	fun candidates(word: Int, y: Int, z: Int): Long {
		specials = false
		climbing = false
		wet = false
		hazardous = false
		fetch(word, y, z, 0, stanceRows)
		return candidates()
	}

	private fun candidates(): Long {
		val (below2, below, body, head) = stanceCells
		for (cell in stanceCells) load(cell)
		return (solid(below) and (blocking(body) or blocking(head)).inv()) or climb(body) or water(body) or
				special(below2) or special(below) or special(body) or special(head)
	}

	private fun gated(word: Int, y: Int, z: Int): Long {
		val gated = candidates()
		var stances = 0L
		var remaining = gated
		while (remaining != 0L) {
			val bit = java.lang.Long.numberOfTrailingZeros(remaining)
			remaining = remaining and (remaining - 1)
			if (standable.isStance(Stance((word shl 6) + bit, y, z))) stances = stances or (1L shl bit)
		}
		return stances
	}

	private fun compute(word: Int, y: Int, z: Int, stamp: LongArray): LongArray {
		specials = false
		climbing = false
		wet = false
		hazardous = false
		fetch(word, y, z, 0, stanceRows)
		val stances = gated(word, y, z)
		gate = stances
		activeCount = 0
		if (stances == 0L) return stamp
		fetch(word, y, z, stanceRows, rowY.size)
		var irregular = 0L
		for (cell in cellRow.indices) {
			load(cell)
			irregular = irregular or irregulars[cell]
		}
		irregularLanes = irregular
		generation++
		if (specials) markSpecialClauses()
		val anySpecial = specials || climbing
		val absent = if (specials) 0 else (if (climbing) 0 else StampLayout.NEEDS_CLIMB) or (if (wet) 0 else StampLayout.NEEDS_WATER)
		for (index in stamped) {
			if (programNeeds[index] and absent != 0) continue
			var passing = stances
			var undecided = 0L
			for (position in clauseStart[index] until clauseStart[index + 1]) {
				val id = clauseIds[position]
				if (clauseGenerations[id] != generation) {
					clauseMasks[id] = mask(id)
					clauseGenerations[id] = generation
				}
				val special = if (specials) clauseSpecials[id] else 0L
				undecided = undecided or special
				passing = passing and (clauseMasks[id] or special)
				if (passing == 0L) break
			}
			if (passing == 0L) continue
			val program = programs[index]!!
			active[activeCount++] = index
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
			stamp[program.slot + 1] = memoizedMask(program.behind!!)
			var open = conditions
			for (index in candidates.indices) {
				if (open == 0L) break
				val bonks = program.bonks[index]
				var bonked = if (bonks.isEmpty()) -1L else 0L
				for (cell in bonks) bonked = bonked or solid(cell)
				if (!anySpecial || irregularLanes and open == 0L) {
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
					val special = irregulars[cell] and scanning
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
			stamp[program.slot] = if (open != 0L) fallback or bend(program, open, stamp, anySpecial) else fallback
		}
		return stamp
	}

	private fun markSpecialClauses() {
		clauseSpecials.fill(0L)
		for (cell in cellRow.indices) {
			val special = specialCells[cell]
			if (special == 0L) continue
			for (id in clausesByCell[cell]) clauseSpecials[id] = clauseSpecials[id] or special
		}
	}

	private fun bend(program: StampLayout.Program, needing: Long, stamp: LongArray, anySpecial: Boolean): Long {
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
				if (through != 0L && anySpecial && irregularLanes and through != 0L) {
					var special = 0L
					for (cell in cells[index]) special = special or irregulars[cell]
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

	private fun memoizedMask(clause: StampLayout.Clause): Long {
		val id = clause.id
		if (clauseGenerations[id] == generation) return clauseMasks[id]
		clauseGenerations[id] = generation
		return mask(id).also { clauseMasks[id] = it }
	}

	private fun mask(id: Int): Long {
		val start = clauseCellStart[id]
		val end = clauseCellStart[id + 1]
		if (start == end) {
			val clause = clauses[id]
			if (clause.classSets != null) return -1L
			val diagram = clause.diagram ?: return 0L
			return evaluate(diagram, diagram.root, clause.cells)
		}
		var mask = -1L
		for (position in start until end) {
			val set = clauseSets[position]
			if (set == ALL_CLASSES) continue
			mask = mask and among(set, clauseCells[position])
			if (mask == 0L) break
		}
		return mask
	}

	private fun evaluate(diagram: ClauseDiagram, node: Int, cells: IntArray): Long {
		if (node == ClauseDiagram.TRUE) return -1L
		if (node == ClauseDiagram.FALSE) return 0L
		val cell = cells[diagram.level(node)]
		var mask = 0L
		for (edge in diagram.firstEdge(node) until diagram.endEdge(node)) {
			val below = evaluate(diagram, diagram.target(edge), cells)
			if (below != 0L) mask = mask or (among(diagram.classes(edge), cell) and below)
		}
		return mask
	}

	private fun among(classes: Int, cell: Int): Long {
		var mask = 0L
		if (classes and EMPTY_CLASS != 0) mask = mask or empties[cell]
		if (classes and SOLID_CLASS != 0) mask = mask or solid(cell)
		if (classes and HAZARD_CLASS != 0) mask = mask or hazard(cell)
		if (classes and CLIMB_CLASS != 0) mask = mask or climb(cell)
		if (classes and WATER_CLASS != 0) mask = mask or water(cell)
		return mask
	}

	private fun load(cell: Int) {
		val solid = shifted(words[CellClass.SOLID.plane], cell)
		val hazard = if (hazardous) shifted(words[CellClass.HAZARD.plane], cell) else 0L
		val climb = if (climbing) shifted(words[CellClass.CLIMB.plane], cell) else 0L
		val water = if (wet) shifted(words[CellClass.WATER.plane], cell) else 0L
		val special = if (specials) shifted(words[CellClass.SPECIAL.plane], cell) else 0L
		val blocking = solid or hazard or water
		solids[cell] = solid
		hazards[cell] = hazard
		climbs[cell] = climb
		waters[cell] = water
		specialCells[cell] = special
		blockings[cell] = blocking
		empties[cell] = (blocking or climb).inv()
		irregulars[cell] = special or climb
	}

	private fun fetch(word: Int, y: Int, z: Int, from: Int, until: Int) {
		for (row in from until until) bits.fetch(word, y + rowY[row], z + rowZ[row], words, row * 3)
		val special = words[CellClass.SPECIAL.plane]
		val climb = words[CellClass.CLIMB.plane]
		val water = words[CellClass.WATER.plane]
		val hazard = words[CellClass.HAZARD.plane]
		for (index in from * 3 until until * 3) {
			if (special[index] != 0L) specials = true
			if (climb[index] != 0L) climbing = true
			if (water[index] != 0L) wet = true
			if (hazard[index] != 0L) hazardous = true
		}
	}

	private fun solid(cell: Int): Long = solids[cell]

	private fun hazard(cell: Int): Long = hazards[cell]

	private fun climb(cell: Int): Long = climbs[cell]

	private fun water(cell: Int): Long = waters[cell]

	private fun special(cell: Int): Long = specialCells[cell]

	private fun blocking(cell: Int): Long = blockings[cell]

	private fun shifted(words: LongArray, cell: Int): Long {
		val base = cellRow[cell] * 3 + 1
		val dx = cellDx[cell]
		return when {
			dx == 0 -> words[base]
			dx > 0 -> (words[base] ushr dx) or (words[base + 1] shl (64 - dx))
			else -> (words[base] shl -dx) or (words[base - 1] ushr (64 + dx))
		}
	}

	private companion object {
		const val MAX_STAMPS = 1_024

		const val STANCE_REACH = 2

		const val HONEY_TOP = 2

		const val EMPTY_CLASS = 1
		const val SOLID_CLASS = 2
		const val HAZARD_CLASS = 4
		const val CLIMB_CLASS = 8
		const val WATER_CLASS = 16
		const val ALL_CLASSES = 31
	}
}
