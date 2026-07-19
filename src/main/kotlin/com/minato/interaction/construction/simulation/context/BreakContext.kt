
package com.minato.interaction.construction.simulation.context

import com.minato.context.Automated
import com.minato.graphics.mc.RenderBuilder
import com.minato.interaction.managers.rotating.RotationRequest
import com.minato.interaction.material.StackSelection
import com.minato.threading.runSafe
import com.minato.util.BlockUtils.emptyState
import net.minecraft.block.BlockState
import net.minecraft.block.FallingBlock
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import java.awt.Color
import kotlin.math.sqrt

data class BreakContext(
    override val hitResult: BlockHitResult,
    override val rotationRequest: RotationRequest,
    override val hotbarIndex: Int,
    val itemSelection: StackSelection,
    val instantBreak: Boolean,
    val insideBlock: Boolean,
    override var cachedState: BlockState,
    private val automated: Automated
) : BuildContext(), Automated by automated {
    private val baseColor = Color(222, 0, 0, 25)
    private val sideColor = Color(222, 0, 0, 100)

    override val blockPos: BlockPos = hitResult.blockPos
    override val expectedState = cachedState.emptyState
    override val sortDistance = runSafe {
        val pov = player.eyePos
        val vec = hitResult.pos
        val d = vec.x - pov.x
        val e = (vec.y - pov.y).let {
            if (cachedState.block is FallingBlock) it - (buildConfig.entityReach / 2)
            else it
        }
        val f = vec.z - pov.z
        sqrt(d * d + e * e + f * f)
    } ?: Double.MAX_VALUE

    override val sorter get() = breakConfig.sorter

    override fun RenderBuilder.render() {
        box(blockPos) {
            colors(baseColor, sideColor)
        }
    }

    override fun canUse() = buildConfig.breakBlocks
}
