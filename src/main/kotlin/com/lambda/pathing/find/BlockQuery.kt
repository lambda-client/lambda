package com.lambda.pathing.find

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.WorldView
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

class BlockQuery(val description: String, world: WorldView?, private val predicate: Predicate<CachedBlockPosition>) {
	private val probes = ThreadLocal.withInitial { Probe(world) }
	private val states = ConcurrentHashMap<BlockState, Boolean>()
	private val blocks = ConcurrentHashMap<Block, Boolean>()
	private val names = ConcurrentHashMap<String, Boolean>()

	fun matches(state: BlockState): Boolean = states.computeIfAbsent(state) { probes.get().test(it) }

	fun mayMatch(block: Block): Boolean = blocks.computeIfAbsent(block) { it.stateManager.states.any(::matches) }

	fun mayMatchName(name: String): Boolean = names.computeIfAbsent(name) { key ->
		Identifier.tryParse(key)?.let { Registries.BLOCK.getOptionalValue(it).orElse(null) }?.let(::mayMatch) ?: false
	}

	private inner class Probe(world: WorldView?) : CachedBlockPosition(world, BlockPos.ORIGIN, false) {
		private var probed: BlockState = Blocks.AIR.defaultState

		override fun getBlockState(): BlockState = probed

		fun test(state: BlockState): Boolean {
			probed = state
			return predicate.test(this)
		}
	}
}
