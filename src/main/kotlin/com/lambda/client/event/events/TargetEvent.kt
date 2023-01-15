package com.lambda.client.event.events

import com.lambda.client.event.Cancellable
import com.lambda.client.event.Event
import com.lambda.client.event.ICancellable
import com.lambda.client.module.modules.combat.KillAura
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase

/**
 * TargetEvent is used for events related to a target doing something
 */
open class TargetEvent : Event {
    /* Before the target is set */
    class Pre(target: EntityLivingBase) : TargetEvent()

    /* After the target is set */
    class Post(target: EntityLivingBase) : TargetEvent()

    /* When the target is changed */
    class Switch(target: EntityLivingBase) : TargetEvent()

    /* When the target is set to null */
    class Reset(target: Entity) : TargetEvent()

    /* When the target is dead */
    class Death(target: Entity) : TargetEvent()

    /* When the target is attacked */
    class HealthUpdate(target: Entity) : TargetEvent()

    /* When the target is attacking */
    class Attack(target: Entity) : TargetEvent()
}