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
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.player.SlotUtils.clickSlot
import com.lambda.util.player.SlotUtils.hotbarAndStorage
import com.lambda.util.primitives.extension.inventorySlots
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.BlockState
import net.minecraft.entity.ItemEntity
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class BreakBlock @Ta5kBuilder constructor(
    private val ctx: BreakContext,
    private val rotationConfig: IRotationConfig,
    private val interactionConfig: InteractionConfig,
    private val sides: Set<Direction>,
    private var collectDrop: Boolean,
    private val rotate: Boolean,
    private val swingHand: Boolean,
) : Task<ItemEntity?>() {
    val blockPos: BlockPos get() = ctx.result.blockPos
    private var beginState: BlockState? = null
    val SafeContext.state: BlockState get() = blockPos.blockState(world)
    override var cooldown = Int.MAX_VALUE
        get() = maxOf(TaskFlow.build.breakCoolDown, TaskFlow.taskCooldown)
    private var drop: ItemEntity? = null

    override fun SafeContext.onStart() {
        parent?.let {
            if (it is BuildStructure) {
                collectDrop = it.collectDrops
            }
        }

        if (state.isAir && !collectDrop) {
            success(null)
            return
        }
        beginState = state
    }

    init {
        listener<RotationEvent.Pre> { event ->
            if (!rotate) return@listener
            event.context = lookAtBlock(blockPos, rotationConfig, interactionConfig, sides)
        }

        listener<RotationEvent.Post> {
            if (!rotate) return@listener
            if (!it.context.isValid) return@listener
            val hitResult = it.context.hitResult?.blockResult ?: return@listener

            breakBlock(hitResult.side)
        }

        listener<TickEvent.Pre> {
            drop?.let { itemDrop ->
                if (!world.entities.contains(itemDrop)) {
                    success(itemDrop)
                    return@listener
                }

                if (player.hotbarAndStorage.none { it.isEmpty }) {
                    player.currentScreenHandler.inventorySlots.firstOrNull {
                        it.stack.item.block in TaskFlow.disposables
                    }?.let {
                        clickSlot(it.index, 1, SlotActionType.THROW)
                    }
                }

                BaritoneUtils.setGoalAndPath(GoalBlock(itemDrop.blockPos))
            }

            if (finish()) {
                success(null)
                return@listener
            }

            if (rotate) return@listener

            breakBlock(ctx.result.side)
            if (finish()) success(null)
        }

        listener<WorldEvent.EntitySpawn> {
            if (collectDrop
                && drop == null
                && it.entity is ItemEntity
                && it.entity.pos.isInRange(blockPos.toCenterPos(), 1.0)
//                && it.entity.stack.item == beginState?.block?.item // ToDo: The item entities are all air??
            ) {
                drop = it.entity
            }
        }
    }

    private fun SafeContext.finish() = state.isAir && !collectDrop

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
