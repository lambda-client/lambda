package com.lambda.task.tasks

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.info
import net.minecraft.block.BlockState

class PlaceBlock @Ta5kBuilder constructor(
    private val ctx: PlaceContext,
    private val swingHand: Boolean,
    private val rotate: Boolean,
    private val waitForConfirmation: Boolean,
) : Task<Unit>() {
    private var beginState: BlockState? = null
    override var cooldown = Int.MAX_VALUE
        get() = maxOf(TaskFlow.build.placeCooldown, TaskFlow.taskCooldown)
    override var timeout = 20
    private var placed = false

    private val SafeContext.resultingState: BlockState get() =
        ctx.resultingPos.blockState(world)
    private val SafeContext.matches get() =
        ctx.targetState.matches(ctx.resultingPos.blockState(world), ctx.resultingPos, world)

    override fun SafeContext.onStart() {
        if (matches) {
            finish()
            return
        }
        beginState = resultingState

        if (!rotate) placeBlock()
    }

    init {
        listener<RotationEvent.Pre> { event ->
            if (placed) return@listener
            if (!rotate) return@listener
            event.context = ctx.rotation
        }

        listener<RotationEvent.Post> {
            if (placed) return@listener
            if (!rotate) return@listener
            if (!it.context.isValid) return@listener

            placeBlock()
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
            if (actionResult.shouldSwingHand() && swingHand) {
                player.swingHand(ctx.hand)
            }

            if (!player.getStackInHand(ctx.hand).isEmpty && interaction.hasCreativeInventory()) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(ctx.hand)
            }

            if (matches) {
                placed = true
                if (!waitForConfirmation) finish()
            }
        } else {
            info("Internal interaction failed with $actionResult")
        }
    }

    private fun SafeContext.finish() {
        LOG.info("Placed at ${
            ctx.result.blockPos.toShortString()
        } (${ctx.result.side}) with expecting state ${
            ctx.expectedState
        } and expecting position at ${ctx.resultingPos.toShortString()}")
        success(Unit)
    }

    companion object {
        @Ta5kBuilder
        fun placeBlock(
            ctx: PlaceContext,
            swingHand: Boolean = TaskFlow.interact.swingHand,
            rotate: Boolean = TaskFlow.build.rotateForPlace,
            waitForConfirmation: Boolean = TaskFlow.build.placeConfirmation,
        ) = PlaceBlock(ctx, swingHand, rotate, waitForConfirmation)
    }
}