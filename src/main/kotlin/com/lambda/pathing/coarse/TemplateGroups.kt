package com.lambda.pathing.coarse

import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.core.MovementId

internal class TemplateGroups(templates: List<MotionTemplate>) {
	private data class Key(val dy: Int, val dz: Int)

	private val keys = templates.map { if (it.movement == MovementId.BOUNCE) null else Key(it.dy, it.dz) }
	private val order = keys.filterNotNull().distinct().sortedWith(compareBy(Key::dy, Key::dz))

	val of: IntArray = order.withIndex().associate { it.value to it.index }.let { index -> IntArray(templates.size) { keys[it]?.let(index::getValue) ?: -1 } }
	val dy = IntArray(order.size) { order[it].dy }
	val dz = IntArray(order.size) { order[it].dz }
	val minDx = IntArray(order.size) { group -> templates.indices.filter { of[it] == group }.minOf { templates[it].dx } }
	val maxDx = IntArray(order.size) { group -> templates.indices.filter { of[it] == group }.maxOf { templates[it].dx } }
	val size: Int get() = order.size

	val rank: IntArray = templates.indices.sortedWith(compareBy({ if (of[it] < 0) Int.MAX_VALUE else of[it] }, { it })).let { sorted ->
		IntArray(templates.size).also { rank -> sorted.forEachIndexed { position, template -> rank[template] = position } }
	}
}
