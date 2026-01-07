/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.interaction.construction.simulation.processing

import com.lambda.context.AutomatedSafeContext
import com.lambda.interaction.construction.verify.SurfaceScan
import com.lambda.interaction.construction.verify.TargetState
import net.minecraft.block.BlockState
import net.minecraft.item.Item
import net.minecraft.state.property.Property
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

interface PreProcessingInfo {
	val surfaceScan: SurfaceScan
	val ignore: Set<Property<*>>
	val sides: Set<Direction>
	val item: Item?
	val expectedState: BlockState
	val placing: Boolean
	val sneak: Boolean?
	val noCaching: Boolean
	val omitInteraction: Boolean

	companion object {
		context(_: AutomatedSafeContext)
		fun default(targetState: TargetState, pos: BlockPos) = object : PreProcessingInfo {
			override val surfaceScan = SurfaceScan.DEFAULT
			override val ignore = setOf<Property<*>>()
			override val sides = Direction.entries.toSet()
			override val item = targetState.getStack(pos).item
			override val expectedState = targetState.getState(pos)
			override val placing = true
			override val sneak = null
			override val noCaching = true
			override val omitInteraction = false
		}
	}
}

class PreProcessingInfoAccumulator(
	override var expectedState: BlockState,
	override var item: Item?,
	override var surfaceScan: SurfaceScan = SurfaceScan.DEFAULT,
	override val ignore: MutableSet<Property<*>> = ProcessorRegistry.postProcessedProperties.toMutableSet(),
	override val sides: MutableSet<Direction> = Direction.entries.toMutableSet(),
	override var placing: Boolean = true,
	override var sneak: Boolean? = null,
	override var noCaching: Boolean = false,
	override var omitInteraction: Boolean = false
) : PreProcessingInfo {
	@InfoAccumulator
	fun offerSurfaceScan(scan: SurfaceScan) {
		if (scan.mode.priority > surfaceScan.mode.priority) {
			surfaceScan = scan
		}
	}

	@InfoAccumulator
	fun addIgnores(vararg properties: Property<*>) {
		ignore.addAll(properties)
	}

	@InfoAccumulator
	fun retainSides(predicate: (Direction) -> Boolean) {
		sides.retainAll(predicate)
	}

	@InfoAccumulator
	fun retainSides(vararg sides: Direction) {
		this.sides.retainAll(sides.toSet())
	}

	@InfoAccumulator
	@JvmName("setExpectedState1")
	fun setExpectedState(expectedState: BlockState) {
		this.expectedState = expectedState
	}

	@InfoAccumulator
	@JvmName("setItem1")
	fun setItem(item: Item?) {
		this.item = item
	}

	@InfoAccumulator
	@JvmName("setPlacing1")
	fun setPlacing(placing: Boolean) {
		this.placing = placing
	}

	@InfoAccumulator
	@JvmName("setSneak1")
	fun setSneak(sneak: Boolean) {
		this.sneak = sneak
	}

	@InfoAccumulator
	fun noCaching() {
		noCaching = true
	}

	@InfoAccumulator
	fun omitInteraction() {
		omitInteraction = true
	}

	@InfoAccumulator
	fun complete(): PreProcessingInfo = this

	companion object {
		@DslMarker
		private annotation class InfoAccumulator
	}
}