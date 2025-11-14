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

package com.lambda.interaction.construction.context

import com.lambda.context.Automated
import com.lambda.graphics.renderer.esp.DirectionMask.mask
import com.lambda.graphics.renderer.esp.ShapeBuilder
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.LogContext.Companion.getLogContextBuilder
import com.lambda.interaction.request.Request.Companion.submit
import com.lambda.interaction.request.hotbar.HotbarRequest
import com.lambda.interaction.request.placing.PlaceRequest
import com.lambda.interaction.request.rotating.RotationRequest
import net.minecraft.block.BlockState
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import java.awt.Color

data class PlaceContext(
    override val hitResult: BlockHitResult,
    override val rotationRequest: RotationRequest,
    override var hotbarIndex: Int,
    override val blockPos: BlockPos,
    override var cachedState: BlockState,
    override val expectedState: BlockState,
    val sneak: Boolean,
    val currentDirIsValid: Boolean = false,
    private val automated: Automated
) : BuildContext(), LogContext, Automated by automated {
    private val baseColor = Color(35, 188, 254, 25)
    private val sideColor = Color(35, 188, 254, 100)

    override val sorter get() = placeConfig.sorter

    override fun ShapeBuilder.buildRenderer() {
        box(blockPos, expectedState, baseColor, sideColor, hitResult.side.mask)
    }

    fun requestDependencies(request: PlaceRequest): Boolean {
        val hotbarRequest = submit(HotbarRequest(hotbarIndex, this), false)
        val validRotation = if (request.placeConfig.rotateForPlace) {
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
