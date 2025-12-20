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

package com.lambda.interaction.construction.simulation.context

import com.lambda.context.Automated
import com.lambda.graphics.esp.ShapeScope
import com.lambda.graphics.mc.TransientRegionESP
import com.lambda.interaction.managers.LogContext
import com.lambda.interaction.managers.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.managers.LogContext.Companion.getLogContextBuilder
import com.lambda.interaction.managers.Request.Companion.submit
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.interacting.InteractRequest
import com.lambda.interaction.managers.rotating.RotationRequest
import net.minecraft.block.BlockState
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.awt.Color

data class InteractContext(
    override val hitResult: BlockHitResult,
    override val rotationRequest: RotationRequest,
    override var hotbarIndex: Int,
    override val blockPos: BlockPos,
    override var cachedState: BlockState,
    override val expectedState: BlockState,
    val placing: Boolean,
    val sneak: Boolean,
    val currentDirIsValid: Boolean = false,
    private val automated: Automated
) : BuildContext(), LogContext, Automated by automated {
    private val baseColor = Color(35, 188, 254, 50)
    private val sideColor = Color(35, 188, 254, 100)

    override val sorter get() = interactConfig.sorter

    override fun render(esp: TransientRegionESP) {
        esp.shapes(hitResult.pos.x, hitResult.pos.y, hitResult.pos.z) {
            val box = with(hitResult.pos) {
                Box(
                    x - 0.05, y - 0.05, z - 0.05,
                    x + 0.05, y + 0.05, z + 0.05,
                ).offset(hitResult.side.doubleVector.multiply(0.05))
            }
            box(box, baseColor, sideColor)
        }
    }

    fun requestDependencies(request: InteractRequest): Boolean {
        val hotbarRequest = submit(HotbarRequest(hotbarIndex, this), false)
        val validRotation = if (request.interactConfig.rotate) {
            submit(rotationRequest, false).done && currentDirIsValid
        } else true
        return hotbarRequest.done && validRotation
    }

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Place Context") {
            text(blockPos.getLogContextBuilder())
            text(hitResult.getLogContextBuilder())
            text(rotationRequest.getLogContextBuilder())
            value("Hotbar Index", hotbarIndex)
            value("Cached State", cachedState)
            value("Expected State", expectedState)
            value("Sneak", sneak)
            value("Current Dir Is Valid", currentDirIsValid)
        }
    }
}
