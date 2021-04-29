package com.lambda.client.event.events

import com.lambda.client.event.Cancellable
import com.lambda.client.event.Event
import net.minecraft.entity.Entity

class PlayerAttackEvent(val entity: Entity) : Event, Cancellable()