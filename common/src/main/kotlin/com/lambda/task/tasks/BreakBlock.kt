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

import baritone.api.pathing.goals.GoalBlock
import com.lambda.config.groups.IRotationConfig
import com.lambda.config.groups.InteractionConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.visibilty.VisibilityChecker.lookAtBlock
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.BaritoneUtils
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.inventorySlots
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils.clickSlot
import com.lambda.util.player.SlotUtils.hotbarAndStorage
import net.minecraft.block.BlockState
import net.minecraft.entity.ItemEntity
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class BreakBlock @Ta5kBuilder constructor(
    private val ctx: BreakContext,
    private val rotation: IRotationConfig,
    private val interact: InteractionConfig,
    private val sides: Set<Direction>,
    private val collectDrop: Boolean,
    private val rotate: Boolean,
    private val swingHand: Boolean,
) : Task<ItemEntity?>() {
    val blockPos: BlockPos get() = ctx.result.blockPos

    private var beginState: BlockState? = null
    val SafeContext.blockState: BlockState
        get() = blockPos.blockState(world)

    private var drop: ItemEntity? = null
    private var state = State.BREAKING
    private var isValid = false

    enum class State {
        BREAKING, COLLECTING
    }

    override fun SafeContext.onStart() {
        if (done()) {
            success(null)
            return
        }
        beginState = blockState

        if (!rotate || ctx.instantBreak) {
            breakBlock(ctx.result.side)
        }
    }

    init {
        listener<RotationEvent.Update> { event ->
            if (state != State.BREAKING) return@listener
            if (!rotate || ctx.instantBreak) return@listener
            event.context = lookAtBlock(blockPos, rotation, interact, sides)
        }

        listener<RotationEvent.Post> {
            if (state != State.BREAKING) return@listener
            if (!rotate || ctx.instantBreak) return@listener

            isValid = it.context.isValid
        }

        listener<TickEvent.Pre> {
            drop?.let { itemDrop ->
                if (!world.entities.contains(itemDrop)) {
                    BaritoneUtils.cancel()
                    success(itemDrop)
                    return@listener
                }

                if (player.hotbarAndStorage.none { it.isEmpty }) {
                    player.currentScreenHandler.inventorySlots.firstOrNull {
                        it.stack.item.block in TaskFlow.disposables
                    }?.let {
                        clickSlot(it.index, 1, SlotActionType.THROW)
                    }
                    return@listener
                }

                BaritoneUtils.setGoalAndPath(GoalBlock(itemDrop.blockPos))
                return@listener
            } ?: BaritoneUtils.cancel()

            if (isValid || !rotate || ctx.instantBreak) {
                breakBlock(ctx.result.side)
            }

            if (done()) {
                if (!collectDrop) {
                    BaritoneUtils.cancel()
                    success(null)
                }
            }
        }

        // ToDo: Find out when the stack entity is filled with the item
        listener<WorldEvent.EntityUpdate> {
            if (collectDrop
                && it.entity is ItemEntity
                && it.entity.pos.isInRange(blockPos.toCenterPos(), 0.5)
            ) {
                drop = it.entity
                state = State.COLLECTING
            }
        }
    }

    private fun SafeContext.done() = blockState.isAir && !collectDrop

    private fun SafeContext.breakBlock(side: Direction) {
        if (interaction.updateBlockBreakingProgress(blockPos, side)) {
            if (player.isCreative) interaction.blockBreakingCooldown = 0
            if (swingHand) player.swingHand(ctx.hand)
        }
    }

    companion object {
        @Ta5kBuilder
        fun breakBlock(
            ctx: BreakContext,
            rotationConfig: IRotationConfig = TaskFlow.rotation,
            interactionConfig: InteractionConfig = TaskFlow.interact,
            sides: Set<Direction> = emptySet(),
            collectDrop: Boolean = TaskFlow.build.collectDrops,
            rotate: Boolean = TaskFlow.build.rotateForBreak,
            swingHand: Boolean = TaskFlow.interact.swingHand,
        ) = BreakBlock(
            ctx,
            rotationConfig,
            interactionConfig,
            sides,
            collectDrop,
            rotate,
            swingHand
        )
    }
}
