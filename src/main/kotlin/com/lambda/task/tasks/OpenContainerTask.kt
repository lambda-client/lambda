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

package com.lambda.task.tasks

import baritone.api.pathing.goals.GoalNear
import com.lambda.context.Automated
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.interaction.manager.managers.rotating.RotationRequestBuilder.Companion.rotationRequest
import com.lambda.task.Task
import com.lambda.threading.runSafeAutomated
import com.lambda.util.TickTimer
import com.lambda.util.player.RotationUtils.lookAtBlock
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.screen.ScreenHandler
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class OpenContainerTask @Ta5kBuilder constructor(
    private val blockPos: BlockPos,
    private val waitForSlotLoad: Boolean = true,
    private val sides: Set<Direction> = Direction.entries.toSet(),
    private val automated: Automated
) : Task<ScreenHandler>(), Automated by automated {
    override val name get() = "${containerState.description()} at ${blockPos.toShortString()}"

    private var screenHandler: ScreenHandler? = null
    private var containerState = State.Scoping

    private val retryTimer = TickTimer()

    enum class State {
        Pathing, Scoping, Opening, SlotLoading;

        fun description() = when (this) {
            Pathing -> "Pathing closer"
            Scoping -> "Waiting for scope"
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
            if (containerState == State.Opening) {
                retryTimer.tick()
                if (retryTimer.hasSurpassed(10)) {
                    retryTimer.reset()
                    containerState = State.Scoping
                }
                return@listen
            }

            if (containerState != State.Scoping && containerState != State.Pathing) return@listen

            val checkedHit = runSafeAutomated { lookAtBlock(blockPos, sides) }
                ?: run {
                    containerState = State.Pathing
                    if (!BaritoneHandler.isActive) BaritoneHandler.setGoalAndPath(GoalNear(blockPos, 3))
                    return@listen
                }
            if (interactConfig.rotate && !rotationRequest { rotation(checkedHit.rotation) }.submit().done) return@listen

            interaction.interactBlock(player, Hand.MAIN_HAND, checkedHit.hit.blockResult ?: return@listen)
            player.swingHand(Hand.MAIN_HAND)

            containerState = State.Opening
        }
    }

    companion object {
        @Ta5kBuilder
        context(automated: Automated)
        fun openContainer(
            blockPos: BlockPos,
            waitForSlotLoad: Boolean = true,
            sides: Set<Direction> = Direction.entries.toSet()
        ) = OpenContainerTask(blockPos, waitForSlotLoad, sides, automated)
    }
}
