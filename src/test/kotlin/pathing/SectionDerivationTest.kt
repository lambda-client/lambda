/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import com.lambda.pathing.world.snapshot.BlockPhysicsTable
import com.lambda.pathing.world.snapshot.HazardRules
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SectionDerivation
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import minecraft.MinecraftBootstrap
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.block.SlabBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.enums.SlabType
import net.minecraft.fluid.FluidState
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import net.minecraft.world.chunk.PaletteProvider
import net.minecraft.world.chunk.PalettedContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Sections derived from a live palette carry exactly the physics the per-cell capture computes. */
class SectionDerivationTest {
	private val base = BlockPos(16, 64, 32)
	private val hazards = HazardRules.AVOID_ALL

	private class StatesView(private val states: Map<BlockPos, BlockState>) : BlockView {
		override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
		override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
		override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
		override fun getHeight(): Int = 384
		override fun getBottomY(): Int = -64
	}

	private fun assertDerivedLikeCapture(states: Map<BlockPos, BlockState>): ImmutableSnapshotSection {
		val container = PalettedContainer(Blocks.AIR.defaultState, PaletteProvider.forBlockStates(Block.STATE_IDS))
		for ((pos, state) in states) {
			if (pos.x - base.x in 0..15 && pos.y - base.y in 0..15 && pos.z - base.z in 0..15) container.set(pos.x and 15, pos.y and 15, pos.z and 15, state)
		}
		val view = StatesView(states)
		val section = SectionDerivation.derive(container, BlockPhysicsTable.of(hazards), base.x, base.y, base.z, view)
		val pos = BlockPos.Mutable()
		for (y in 0..15) for (z in 0..15) for (x in 0..15) {
			pos.set(base.x + x, base.y + y, base.z + z)
			val expected = BlockPhysicsCapture.capture(view.getBlockState(pos), view, pos, ShapeContext.absent(), hazards)
			assertEquals(expected, section[pos.x, pos.y, pos.z], "cell $pos (${view.getBlockState(pos)})")
		}
		return section
	}

	@Test
	fun `a mixed section matches the per-cell capture, water currents and offset bamboo included`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -1..16) for (z in -1..16) states[base.add(x, 0, z)] = Blocks.STONE.defaultState
		for (x in 2..6) for (z in 2..6) states[base.add(x, 1, z)] = Blocks.WATER.defaultState
		states[base.add(7, 1, 4)] = Blocks.WATER.defaultState.with(Properties.LEVEL_15, 2)
		states[base.add(-1, 1, 4)] = Blocks.WATER.defaultState
		for (z in 0..15) states[base.add(10, 1, z)] = Blocks.SLIME_BLOCK.defaultState
		states[base.add(12, 1, 3)] = Blocks.OAK_SLAB.defaultState.with(SlabBlock.TYPE, SlabType.BOTTOM)
		states[base.add(12, 1, 4)] = Blocks.OAK_FENCE.defaultState
		states[base.add(12, 1, 5)] = Blocks.MAGMA_BLOCK.defaultState
		states[base.add(12, 1, 6)] = Blocks.CACTUS.defaultState
		for (z in 8..14) states[base.add(14, 1, z)] = Blocks.BAMBOO.defaultState
		val section = assertDerivedLikeCapture(states)
		assertTrue(section.bouncy && section.watery && section.hot)
	}

	@Test
	fun `a section with more than 256 states takes the global palette and short indices`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		val distinct = Block.STATE_IDS.asSequence().filter { it.fluidState.isEmpty && !it.block.hasDynamicBounds() }.take(400).toList()
		for ((index, state) in distinct.withIndex()) states[base.add(index and 15, index shr 8, (index shr 4) and 15)] = state
		val section = assertDerivedLikeCapture(states)
		assertTrue(section.paletteSize > 1)
	}

	@Test
	fun `a uniform section is one palette entry without indices`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (y in 0..15) for (z in 0..15) for (x in 0..15) states[base.add(x, y, z)] = Blocks.STONE.defaultState
		val section = assertDerivedLikeCapture(states)
		assertEquals(1, section.paletteSize)
		assertEquals(0, section.indexStorageBytes)
	}

	@Test
	fun `patching a cell reuses or appends a palette entry and never touches the source section`() {
		val uniform = ImmutableSnapshotSection.uniform(SnapshotBlockPhysics.AIR)
		val walled = uniform.with(1, 2, 3, SnapshotBlockPhysics.FULL_CUBE)
		assertEquals(2, walled.paletteSize)
		assertEquals(SnapshotBlockPhysics.FULL_CUBE, walled[1, 2, 3])
		assertEquals(SnapshotBlockPhysics.AIR, walled[1, 2, 4])
		assertEquals(SnapshotBlockPhysics.AIR, uniform[1, 2, 3])

		val twice = walled.with(4, 5, 6, SnapshotBlockPhysics.FULL_CUBE)
		assertEquals(2, twice.paletteSize, "an existing entry is reused")
		assertEquals(SnapshotBlockPhysics.FULL_CUBE, twice[4, 5, 6])
		assertEquals(SnapshotBlockPhysics.AIR, walled[4, 5, 6])
		assertSame(twice, twice.with(4, 5, 6, SnapshotBlockPhysics.FULL_CUBE), "an unchanged cell keeps the section")
	}
}
