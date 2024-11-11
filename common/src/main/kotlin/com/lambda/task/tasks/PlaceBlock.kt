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

import com.lambda.Lambda.LOG
import com.lambda.config.groups.InteractionConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.warn
import net.minecraft.block.BlockState

class PlaceBlock @Ta5kBuilder constructor(
    private val ctx: PlaceContext,
    private val rotate: Boolean,
    private val interact: InteractionConfig = TaskFlow.interact,
    private val waitForConfirmation: Boolean,
) : Task<Unit>() {
    private var beginState: BlockState? = null
    private var state = State.ROTATING
    private var findOutIfNeeded = false

    private val SafeContext.resultingState: BlockState
        get() = ctx.resultingPos.blockState(world)

    private val SafeContext.matches
        get() = ctx.targetState.matches(ctx.resultingPos.blockState(world), ctx.resultingPos, world)

    enum class State {
        ROTATING, PLACING, CONFIRMING
    }

    override fun SafeContext.onStart() {
        if (matches) {
            finish()
            return
        }
        beginState = resultingState

        if (!rotate) {
            placeBlock()
        }
    }

    init {
        listener<RotationEvent.Update> { event ->
            if (state != State.ROTATING) return@listener
            if (!rotate) return@listener
            event.context = ctx.rotation
        }

        listener<RotationEvent.Post> { event ->
            if (state != State.ROTATING) return@listener
            if (!rotate) return@listener
            if (event.context != ctx.rotation) return@listener
            if (!event.context.isValid) return@listener

            state = State.PLACING
        }

        listener<TickEvent.Pre> {
            if (state != State.PLACING) return@listener

            if (findOutIfNeeded) placeBlock()
            findOutIfNeeded = true
        }

        listener<MovementEvent.InputUpdate> {
            if (state != State.PLACING) return@listener
            val hitBlock = ctx.result.blockPos.blockState(world).block
            if (hitBlock in BlockUtils.interactionBlacklist) {
                it.input.sneaking = true
            }
        }

        listener<WorldEvent.BlockUpdate> {
            if (it.pos != ctx.resultingPos) return@listener

            if (ctx.targetState.matches(it.state, it.pos, world)) {
                finish()
            }
        }
    }

    private fun SafeContext.placeBlock() {
        val actionResult = interaction.interactBlock(
            player,
            ctx.hand,
            ctx.result
        )

        if (actionResult.isAccepted) {
            if (actionResult.shouldSwingHand() && interact.swingHand) {
                player.swingHand(ctx.hand)
            }

            if (!player.getStackInHand(ctx.hand).isEmpty && interaction.hasCreativeInventory()) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(ctx.hand)
            }

            state = State.CONFIRMING

            if (matches) {
                if (!waitForConfirmation) finish()
            }
        } else {
            warn("Internal interaction failed with $actionResult")
        }
    }

    private fun SafeContext.finish() {
        LOG.info(
            "Placed at ${
                ctx.result.blockPos.toShortString()
            } (${ctx.result.side}) with expecting state ${
                ctx.expectedState
            } and expecting position at ${ctx.resultingPos.toShortString()}"
        )
        success(Unit)
    }

    companion object {
        @Ta5kBuilder
        fun placeBlock(
            ctx: PlaceContext,
            rotate: Boolean = TaskFlow.build.rotateForPlace,
            waitForConfirmation: Boolean = TaskFlow.build.placeConfirmation,
            interact: InteractionConfig = TaskFlow.interact,
        ) = PlaceBlock(ctx, rotate, interact, waitForConfirmation)
    }
}
