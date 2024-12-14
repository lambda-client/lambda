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

package com.lambda.task.tasks

import com.lambda.config.groups.IRotationConfig
import com.lambda.config.groups.InteractionConfig
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.visibilty.VisibilityChecker.lookAtBlock
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.screen.ScreenHandler
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class OpenContainer(
    private val blockPos: BlockPos,
    private val waitForSlotLoad: Boolean = true,
    private val rotate: Boolean,
    private val rotation: IRotationConfig = TaskFlow.rotation,
    private val interact: InteractionConfig = TaskFlow.interact,
    private val sides: Set<Direction> = emptySet(),
) : Task<ScreenHandler>() {
    private var screenHandler: ScreenHandler? = null
    private var state = State.SCOPING
    private var inScope = 0

    override var timeout = 50

    enum class State {
        SCOPING, OPENING, SLOT_LOADING
    }

    init {
        listen<ScreenHandlerEvent.Open> {
            if (state != State.OPENING) return@listen

            screenHandler = it.screenHandler
            state = State.SLOT_LOADING

            if (!waitForSlotLoad) success(it.screenHandler)
        }

        listen<ScreenHandlerEvent.Close> {
            if (screenHandler != it.screenHandler) return@listen

            state = State.SCOPING
            screenHandler = null
        }

        listen<ScreenHandlerEvent.Update> {
            if (state != State.SLOT_LOADING) return@listen

            screenHandler?.let {
                success(it)
            }
        }

        listen<RotationEvent.Update> { event ->
            if (!rotate) return@listen
            event.context = lookAtBlock(blockPos, rotation, interact, sides)
        }

        listen<RotationEvent.Post> {
            if (!rotate) return@listen
            if (state != State.SCOPING) return@listen
            if (!it.context.isValid) return@listen

            if (inScope++ >= interact.scopeThreshold) {
                val hitResult = it.context.hitResult?.blockResult ?: return@listen
                interaction.interactBlock(player, Hand.MAIN_HAND, hitResult)

                state = State.OPENING
            }
        }
    }

    companion object {
        @Ta5kBuilder
        fun openContainer(
            blockPos: BlockPos,
            waitForSlotLoad: Boolean = true,
            rotate: Boolean = true,
        ) = OpenContainer(blockPos, waitForSlotLoad, rotate)
    }
}
