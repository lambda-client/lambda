package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

sealed class InteractionEvent : Event {
    class Block(
        val world: ClientWorld,
        val blockHitResult: BlockHitResult
    ) : InteractionEvent()

    class AttackBlock(
        val pos: BlockPos,
        val side: Direction
    ) : InteractionEvent(), ICancellable by Cancellable()

    abstract class UpdateBlockBreakingProgress : InteractionEvent() {

        class Pre(
            val pos: BlockPos,
            val side: Direction,
        ) : UpdateBlockBreakingProgress()

        class Post(
            val pos: BlockPos,
            val side: Direction,
        ) : UpdateBlockBreakingProgress()
    }

    class GetBlockBreakingProgress(
        var value: Int
    ) : InteractionEvent()
}