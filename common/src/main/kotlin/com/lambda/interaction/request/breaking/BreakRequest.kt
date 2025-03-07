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
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.Request
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import net.minecraft.entity.ItemEntity

data class BreakRequest(
    val contexts: List<BreakContext>,
    val buildConfig: BuildConfig,
    val rotationConfig: RotationConfig,
    val hotbarConfig: HotbarConfig,
    val prio: Priority = 0,
    val onBreak: () -> Unit,
    val onItemDrop: (ItemEntity) -> Unit,
) : Request(prio) {
    override val done: Boolean
        get() = runSafe {
            contexts.all {
                ctx -> ctx.targetState.matches(blockState(ctx.expectedPos), ctx.expectedPos, world)
            }
        } == true
}