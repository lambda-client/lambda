package com.lambda.client.event.events

import com.lambda.client.event.Cancellable
import com.lambda.client.event.Event
import com.lambda.client.event.ICancellable
import net.minecraft.util.math.AxisAlignedBB

class CollisionEvent : Event {
    class AddCollision(val collisionBoxList : MutableList<AxisAlignedBB>)
    class PushOut : ICancellable by Cancellable()
}