package com.lambda.task.tasks

import com.lambda.event.events.RotationEvent
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.config.groups.InteractionConfig
import com.lambda.config.groups.IRotationConfig
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
        listener<ScreenHandlerEvent.Open> {
            if (state != State.OPENING) return@listener

            screenHandler = it.screenHandler
            state = State.SLOT_LOADING

            if (!waitForSlotLoad) success(it.screenHandler)
        }

        listener<ScreenHandlerEvent.Close> {
            if (screenHandler != it.screenHandler) return@listener

            state = State.SCOPING
            screenHandler = null
        }

        listener<ScreenHandlerEvent.Update> {
            if (state != State.SLOT_LOADING) return@listener

            screenHandler?.let {
                success(it)
            }
        }

        listener<RotationEvent.Update> { event ->
            if (!rotate) return@listener
            event.context = lookAtBlock(blockPos, rotation, interact, sides)
        }

        listener<RotationEvent.Post> {
            if (!rotate) return@listener
            if (state != State.SCOPING) return@listener
            if (!it.context.isValid) return@listener

            if (inScope++ >= interact.scopeThreshold) {
                val hitResult = it.context.hitResult?.blockResult ?: return@listener
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