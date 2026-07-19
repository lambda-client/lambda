
package com.minato.interaction.construction.simulation.context

import com.minato.context.Automated
import com.minato.graphics.mc.RenderBuilder
import com.minato.interaction.construction.simulation.processing.PreProcessingInfo
import com.minato.interaction.managers.interacting.InteractRequest
import com.minato.interaction.managers.rotating.RotationRequest
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
    val preProcessingInfo: PreProcessingInfo,
    val sneak: Boolean,
    val currentDirIsValid: Boolean = false,
    private val automated: Automated
) : BuildContext(), Automated by automated {
    private val baseColor = Color(35, 188, 254, 50)
    private val sideColor = Color(35, 188, 254, 100)

    override val sorter get() = interactConfig.sorter

    override fun RenderBuilder.render() {
        val box = with(hitResult.pos) {
            Box(
                x - 0.05, y - 0.05, z - 0.05,
                x + 0.05, y + 0.05, z + 0.05,
            ).offset(hitResult.side.doubleVector.multiply(0.05))
        }
        box(box) {
            colors(baseColor, sideColor)
        }
    }

    fun requestDependencies(request: InteractRequest): Boolean {
        val validRotation = if (request.interactConfig.rotate) {
            (rotationRequest.submit(queueIfMismatchedStage = false).done || interactConfig.airPlace.isEnabled) && currentDirIsValid
        } else true
        return validRotation
    }

    override fun canUse() =
        (buildConfig.interactBlocks && !preProcessingInfo.placing) || (buildConfig.placeBlocks && preProcessingInfo.placing)
}
