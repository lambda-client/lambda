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

package com.lambda.task.tasks

import com.lambda.context.Automated
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.RotationRequest
import com.lambda.interaction.managers.rotating.visibilty.lookAtBlock
import com.lambda.task.Task
import com.lambda.threading.runSafeAutomated
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.screen.ScreenHandler
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class OpenContainer @Ta5kBuilder constructor(
    private val blockPos: BlockPos,
    private val automated: Automated,
    private val waitForSlotLoad: Boolean = true,
    private val sides: Set<Direction> = Direction.entries.toSet()
) : Task<ScreenHandler>(), Automated by automated {
    override val name get() = "${containerState.description(inScope)} at ${blockPos.toShortString()}"

    private var screenHandler: ScreenHandler? = null
    private var containerState = State.Scoping
    private var inScope = 0

    enum class State {
        Scoping, Opening, SlotLoading;

        fun description(inScope: Int) = when (this) {
            Scoping -> "Waiting for scope ($inScope)"
            Opening -> "Opening container"
            SlotLoading -> "Waiting for slots to load"
        }
    }

    init {
        listen<InventoryEvent.Open> {
            if (containerState != State.Opening) return@listen

            screenHandler = it.screenHandler
            containerState = State.SlotLoading

            if (!waitForSlotLoad) success(it.screenHandler)
        }

        listen<InventoryEvent.Close> {
            if (screenHandler != it.screenHandler) return@listen

            containerState = State.Scoping
            screenHandler = null
        }

        listen<InventoryEvent.FullUpdate> {
            if (containerState != State.SlotLoading) return@listen

            screenHandler?.let {
                success(it)
            }
        }

        listen<TickEvent.Pre> {
            if (containerState != State.Scoping) return@listen

            val checkedHit = runSafeAutomated { lookAtBlock(blockPos, sides) } ?: return@listen
            if (interactConfig.rotate && !RotationRequest(checkedHit.rotation, this@OpenContainer).submit().done) return@listen

            interaction.interactBlock(player, Hand.MAIN_HAND, checkedHit.hit.blockResult ?: return@listen)

            containerState = State.Opening
        }
    }
}
