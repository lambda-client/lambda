package com.lambda.task.tasks

import com.lambda.event.EventFlow.awaitEvent
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.task.TaskChainBuilder
import com.lambda.task.buildChain
import com.lambda.task.tasks.LookAtBlock.Companion.lookAtBlock
import com.lambda.threading.runGameBlocking
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.screen.ScreenHandler
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

class OpenContainer<H : ScreenHandler>(
    private val blockPos: BlockPos,
    private val waitForSlotLoad: Boolean = true,
) : Task<H>() {
    private var screenHandler: H? = null
    private var slotsLoaded = false

    init {
        listener<ScreenHandlerEvent.Open<H>> {
            screenHandler = it.screenHandler
        }

        listener<ScreenHandlerEvent.Loaded> {
            slotsLoaded = true
        }
    }

    override suspend fun onAction(): H {
        buildChain {
            lookAtBlock(blockPos)
                .withTimeout(2000L)
                .onSuccess { request ->
                    runGameBlocking {
                        val cast = request.rotation.rayCast(5.0)?.blockResult ?: throw IllegalStateException("Failed to raycast block")
                        interaction.interactBlock(player, Hand.MAIN_HAND, cast)
                    }

                    awaitEvent<TickEvent.Post> {
                        screenHandler != null && (!waitForSlotLoad || slotsLoaded)
                    }
                }
        }.run()

        return screenHandler ?: throw IllegalStateException("Failed to open container")
    }

    companion object {
        @TaskCha1nBuilder
        inline fun <reified T : ScreenHandler> TaskChainBuilder.openContainer(
            blockPos: BlockPos,
            waitForSlotLoad: Boolean = true
        ) = OpenContainer<T>(blockPos, waitForSlotLoad).apply {
                required(this)
            }
    }
}