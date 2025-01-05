/*
 * Copyright 2024 Lambda
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

package com.lambda.interaction.construction.blueprint

import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
import com.lambda.util.extension.Structure
import net.minecraft.util.math.Vec3i

data class DynamicBlueprint(
    val init: SafeContext.(Structure) -> Structure = { emptyMap() },
    val update: SafeContext.(Structure) -> Structure = { it },
) : Blueprint() {
    fun update() {
        runSafe {
            structure = update(structure)
        }
    }

    fun create() {
        runSafe {
            structure = init(structure)
        }
    }

    override var structure: Structure = emptyMap()
        private set(value) {
            field = value
            bounds.reset()
        }

    override fun toString() = "Dynamic Blueprint at ${center?.toShortString()}"

    companion object {
        fun offset(offset: Vec3i): SafeContext.(Structure) -> Structure = {
            it.map { (pos, state) ->
                pos.add(offset) to state
            }.toMap()
        }

        fun Structure.toBlueprint(
            init: SafeContext.(Structure) -> Structure = { this@toBlueprint },
            onTick: SafeContext.(Structure) -> Structure,
        ) = DynamicBlueprint(init = init, update = onTick)
    }
}
