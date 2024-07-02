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

class OpenContainer<H : ScreenHandler>(
    private val blockPos: BlockPos,
    private val waitForSlotLoad: Boolean = true,
    private val rotationConfig: IRotationConfig = TaskFlow.rotation,
    private val interactionConfig: InteractionConfig = TaskFlow.interact,
    private val sides: Set<Direction> = emptySet(),
) : Task<H>() {
    private var screenHandler: H? = null
    private var slotsLoaded = false

    init {
        listener<ScreenHandlerEvent.Open<H>> {
            screenHandler = it.screenHandler

            if (!waitForSlotLoad || slotsLoaded) success(it.screenHandler)
        }

        listener<ScreenHandlerEvent.Close<H>> {
            screenHandler = null
        }

        listener<ScreenHandlerEvent.Loaded> {
            slotsLoaded = true

            screenHandler?.let {
                success(it)
            }
        }

        listener<RotationEvent.Update> { event ->
            if (screenHandler != null) return@listener
            event.context = lookAtBlock(blockPos, rotationConfig, interactionConfig, sides)
        }

        listener<RotationEvent.Post> {
            if (screenHandler != null) return@listener
            if (!it.context.isValid) return@listener
            val hitResult = it.context.hitResult?.blockResult ?: return@listener
            interaction.interactBlock(player, Hand.MAIN_HAND, hitResult)
        }
    }

    companion object {
        @Ta5kBuilder
        inline fun <reified T : ScreenHandler> openContainer(
            blockPos: BlockPos,
            waitForSlotLoad: Boolean = true
        ) = OpenContainer<T>(blockPos, waitForSlotLoad)
    }
}