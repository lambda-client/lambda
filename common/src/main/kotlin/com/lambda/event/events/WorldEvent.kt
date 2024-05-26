package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import net.minecraft.entity.Entity

abstract class WorldEvent : Event {
    class EntitySpawn(
        val entity: Entity
    ) : WorldEvent(), ICancellable by Cancellable()
}