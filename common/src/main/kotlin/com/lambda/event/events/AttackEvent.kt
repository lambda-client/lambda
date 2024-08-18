package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.entity.Entity

abstract class AttackEvent(val entity: Entity) : Event {
    class Pre(entity: Entity) : AttackEvent(entity), ICancellable by Cancellable()
    class Post(entity: Entity) : AttackEvent(entity)
}