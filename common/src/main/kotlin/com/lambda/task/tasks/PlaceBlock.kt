package com.lambda.task.tasks

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.task.Task
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.BlockState

class PlaceBlock @Ta5kBuilder constructor(
    private val ctx: PlaceContext,
    private val swingHand: Boolean = true,
    private val rotate: Boolean = true,
    private val waitForConfirmation: Boolean = false,
) : Task<Unit>() {
    private var beginState: BlockState? = null
    private val SafeContext.resultingState: BlockState get() = ctx.resultingPos.blockState(world)
    private val SafeContext.matches get() = ctx.targetState.matches(ctx.resultingPos.blockState(world), ctx.resultingPos, world)

    override fun SafeContext.onStart() {
        if (matches) {
            success(Unit)
            return
        }
        beginState = resultingState
    }

    init {
        listener<RotationEvent.Pre> { event ->
            if (!rotate) return@listener
            event.context = ctx.rotation
        }

        listener<RotationEvent.Post> {
            if (!rotate) return@listener
            if (!it.context.isValid) return@listener

            placeBlock()
        }

//        listener<WorldEvent.BlockUpdate> {
//            if (matches) success(Unit)
//        }
    }

    private fun SafeContext.placeBlock() {
        val preStack = player.getStackInHand(ctx.hand)

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

            if (!waitForConfirmation && matches) {
                LOG.info("Placed $preStack at ${
                    ctx.result.blockPos.toShortString()
                } (${ctx.result.side}) with expecting state ${
                    ctx.expectedState
                } and expecting position at ${ctx.resultingPos.toShortString()}")
                success(Unit)
            }
        } else {
            failure("Internal interaction failed with $actionResult")
        }
    }

    companion object {
        @Ta5kBuilder
        fun placeBlock(
            ctx: PlaceContext,
            swingHand: Boolean = true,
            rotate: Boolean = true,
            waitForConfirmation: Boolean = false,
        ) = PlaceBlock(ctx, swingHand, rotate, waitForConfirmation)
    }
}