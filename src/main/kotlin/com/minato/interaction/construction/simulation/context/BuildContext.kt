
package com.minato.interaction.construction.simulation.context

import com.minato.config.blocks.ActionConfig
import com.minato.context.Automated
import com.minato.interaction.construction.simulation.result.Drawable
import com.minato.interaction.managers.rotating.RotationRequest
import com.minato.threading.runSafe
import net.minecraft.block.BlockState
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import kotlin.random.Random

/**
 * Holds the necessary information for managers to perform actions.
 */
abstract class BuildContext : Drawable, Automated {
    abstract val hitResult: BlockHitResult
    abstract val rotationRequest: RotationRequest
    abstract val hotbarIndex: Int
    abstract val cachedState: BlockState
    abstract val expectedState: BlockState
    abstract val blockPos: BlockPos
    abstract val sorter: ActionConfig.SortMode
    val random = Random.nextDouble()

    open val sortDistance by lazy {
        runSafe { player.eyePos.distanceTo(hitResult.pos) } ?: Double.MAX_VALUE
    }

    abstract fun canUse(): Boolean
}
