package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.client.world.ClientWorld
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.hit.BlockHitResult

sealed class InteractionEvent : Event {
    class Block(
        val world: ClientWorld,
        val blockHitResult: BlockHitResult
    ) : InteractionEvent()

    data class SlotClick(
        val syncId: Int,
        val slot: Int,
        val button: Int,
        val action: SlotActionType,
        val screenHandler: ScreenHandler,
    ) : ScreenHandlerEvent(), ICancellable by Cancellable()
}