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

package com.lambda.interaction.construction.simulation.context

import com.lambda.config.groups.ActionConfig
import com.lambda.context.Automated
import com.lambda.interaction.construction.simulation.result.Drawable
import com.lambda.interaction.managers.rotating.RotationRequest
import com.lambda.threading.runSafe
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
}
