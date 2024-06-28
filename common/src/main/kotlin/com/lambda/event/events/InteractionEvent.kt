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

    sealed class BlockAttack : InteractionEvent() {
        class Pre(
            val pos: BlockPos,
            val side: Direction
        ) : BlockAttack(), ICancellable by Cancellable()

        class Post(
            val pos: BlockPos,
            val side: Direction
        ) : BlockAttack()
    }

    sealed class BreakingProgress : InteractionEvent() {
        class Pre(
            val pos: BlockPos,
            val side: Direction,
            var progress: Float,
        ) : BreakingProgress(), ICancellable by Cancellable()

        class Post(
            val pos: BlockPos,
            val side: Direction,
            val progress: Float,
        ) : BreakingProgress()
    }
}
