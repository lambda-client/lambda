/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.debug

import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.world.blockSearch
import net.minecraft.block.Blocks
import net.minecraft.util.math.Vec3i
import java.awt.Color

@Suppress("unused")
object BlockTest : Module(
    name = "BlockTest",
    description = "BlockTest",
    tag = ModuleTag.Debug,
) {
    private val rangeX by setting("Range X", 5, 1..7, 1, "Range X")
    private val rangeY by setting("Range Y", 5, 1..7, 1, "Range Y")
    private val rangeZ by setting("Range Z", 5, 1..7, 1, "Range Z")
    private val stepX by setting("Step X", 1, 1..7, 1, "Step X")
    private val stepY by setting("Step Y", 1, 1..7, 1, "Step Y")
    private val stepZ by setting("Step Z", 1, 1..7, 1, "Step Z")

    private val range: Vec3i
        get() = Vec3i(rangeX, rangeY, rangeZ)

    private val step: Vec3i
        get() = Vec3i(stepX, stepY, stepZ)

    private val filledColor = Color(100, 150, 255, 128)
    private val outlineColor = Color(100, 150, 255, 51)

    init {
        tickedRenderer("BlockTest Ticked Renderer") {
            runSafe {
                blockSearch(range, step = step) { _, state ->
                    state.isOf(Blocks.DIAMOND_BLOCK)
                }.forEach { (pos, state) ->
                    state.getOutlineShape(world, pos).boundingBoxes.forEach { box ->
                        box(box.offset(pos)) {
                            colors(filledColor, outlineColor)
                        }
                    }
                }
            }
        }
    }
}
