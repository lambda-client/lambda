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

package com.lambda.interaction.request.breaking

import com.lambda.config.groups.BuildConfig
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.Request
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos

data class BreakRequest(
    val contexts: Collection<BreakContext>,
    val build: BuildConfig,
    val rotation: RotationConfig,
    val hotbar: HotbarConfig,
    val pendingInteractions: MutableCollection<BuildContext>,
    val onAccept: ((BlockPos) -> Unit)? = null,
    val onCancel: ((BlockPos) -> Unit)? = null,
    val onBreak: ((BlockPos) -> Unit)? = null,
    val onItemDrop: ((ItemEntity) -> Unit)? = null,
    private val prio: Priority = 0
) : Request(prio, build.breaking) {
    override val done: Boolean
        get() = runSafe { contexts.all { it.targetState.matches(blockState(it.expectedPos), it.expectedPos, world) } } == true
}
