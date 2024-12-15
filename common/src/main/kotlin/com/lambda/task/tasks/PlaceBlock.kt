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
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.rotation
import com.lambda.interaction.rotation.RotationContext
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.BlockUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.info
import com.lambda.util.Communication.warn
import com.lambda.util.extension.partialTicks
import net.minecraft.block.BlockState
import net.minecraft.client.gui.screen.ingame.SignEditScreen
import net.minecraft.text.Text

class PlaceBlock @Ta5kBuilder constructor(
    private val ctx: PlaceContext,
    private val rotate: Boolean,
    private val interact: InteractionConfig = TaskFlow.interact,
    private val waitForConfirmation: Boolean,
) : Task<Unit>() {
    private var beginState: BlockState? = null
    private var state = State.PRIME_ROTATION
    private var findOutIfNeeded = false
    private var primeContext: RotationContext? = null
    private var waited = 0

    private val SafeContext.resultingState: BlockState
        get() = ctx.resultingPos.blockState(world)

    private val SafeContext.matches
        get() = ctx.targetState.matches(ctx.resultingPos.blockState(world), ctx.resultingPos, world)

    enum class State {
        PRIME_ROTATION, ROTATING, PLACING, CONFIRMING
    }

    override fun SafeContext.onStart() {
        if (ctx.primeDirection == null) {
            state = State.ROTATING
        }

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
        listen<RotationEvent.Update> { event ->
            if (!rotate) return@listen
            when (state) {
                State.PRIME_ROTATION -> {
                    ctx.primeDirection?.let { direction ->
                        primeContext = RotationContext(
                            direction.rotation,
                            ctx.rotation.config,
                        )
                        event.context = primeContext
                    }
                }
                else -> event.context = ctx.rotation
            }
        }

        listen<RotationEvent.Post> { event ->
            if (!rotate) return@listen
            if (!event.context.isValid) return@listen
            Text.of("Rotation: ${event.context.rotation}")
            when (state) {
                State.PRIME_ROTATION -> {
                    if (event.context != primeContext) return@listen
                    state = State.ROTATING
                }
                State.ROTATING -> {
                    if (event.context != ctx.rotation) return@listen
                    if (!event.context.isValid) return@listen

                    state = State.PLACING
                }
                else -> return@listen
            }
        }

        listen<TickEvent.Pre> {
            if (state != State.PLACING) return@listen

            /*if (findOutIfNeeded) placeBlock()
            findOutIfNeeded = true*/
            placeBlock()
        }

        listen<MovementEvent.InputUpdate> {
            if (state != State.PLACING) return@listen
            val hitBlock = ctx.result.blockPos.blockState(world).block
            if (hitBlock in BlockUtils.interactionBlacklist) {
                it.input.sneaking = true
            }
        }

        listen<WorldEvent.BlockUpdate> {
            if (it.pos != ctx.resultingPos) return@listen

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
