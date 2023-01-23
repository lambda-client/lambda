package com.lambda.client.module.modules.movement

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityBoat
import net.minecraft.entity.passive.*
import net.minecraft.util.EnumHand
import net.minecraftforge.fml.common.gameevent.TickEvent

object AutoRemount : Module(
    name = "AutoRemount",
    description = "Automatically remounts your ridable entity",
    category = Category.MOVEMENT
) {
    private val boat by setting("Boats", true)
    private val horse by setting("Horse", true)
    private val skeletonHorse by setting("Skeleton Horse", true)
    private val donkey by setting("Donkey", true)
    private val mule by setting("Mule", true)
    private val pig by setting("Pig", true)
    private val llama by setting("Llama", true)
    private val range by setting("Range", 2.0f, 1.0f..5.0f, 0.5f)
    private val remountDelay by setting("Remount Delay", 5, 0..10, 1)

    private val remountTimer = TickTimer(TimeUnit.TICKS)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            // we don't need to do anything if we're already riding.
            if (player.isRiding) {
                remountTimer.reset()
                return@safeListener
            }

            if (remountTimer.tick(remountDelay)) {
                world.loadedEntityList.asSequence()
                    .filter(::isValidEntity)
                    .minByOrNull { player.getDistanceSq(it) }
                    ?.let {
                        if (player.getDistance(it) < range) {
                            playerController.interactWithEntity(player, it, EnumHand.MAIN_HAND)
                        }
                    }
            }
        }
    }

    private fun isValidEntity(entity: Entity): Boolean {
        return boat && entity is EntityBoat
            || entity is EntityAnimal && !entity.isChild // FBI moment
            && (horse && entity is EntityHorse
            || skeletonHorse && entity is EntitySkeletonHorse
            || donkey && entity is EntityDonkey
            || mule && entity is EntityMule
            || pig && entity is EntityPig && entity.saddled
            || llama && entity is EntityLlama)
    }
}
