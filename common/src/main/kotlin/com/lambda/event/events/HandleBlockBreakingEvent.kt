package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable

/**
 * Called when the player attacks a block.
 * @author Doogie13
 * @since 05/06/2024
 */
abstract class HandleBlockBreakingEvent : Event {

    class Pre : HandleBlockBreakingEvent(),
        ICancellable by Cancellable()

    class Post : HandleBlockBreakingEvent()

}