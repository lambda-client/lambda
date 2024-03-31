package com.lambda.event.events

import com.lambda.event.Event
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.hit.BlockHitResult

sealed class InteractionEvent : Event {
    class Block(
        val world: ClientWorld,
        val blockHitResult: BlockHitResult
    ) : InteractionEvent()
}