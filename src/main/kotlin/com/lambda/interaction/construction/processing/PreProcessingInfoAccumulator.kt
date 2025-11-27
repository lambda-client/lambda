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

package com.lambda.interaction.construction.processing

import com.lambda.interaction.construction.verify.SurfaceScan
import net.minecraft.state.property.Property
import net.minecraft.util.math.Direction

class PreProcessingInfoAccumulator(
    override var surfaceScan: SurfaceScan = SurfaceScan.DEFAULT,
    override val ignore: MutableSet<Property<*>> = ProcessorRegistry.postProcessedProperties.toMutableSet(),
    override val sides: MutableSet<Direction> = Direction.entries.toMutableSet(),
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
    fun complete() = this as PreProcessingInfo

    companion object {
        @DslMarker
        private annotation class InfoAccumulator
    }
}