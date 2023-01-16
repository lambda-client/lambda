package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock

object Prevent : Module(
    name = "Prevent",
    description = "Prevents contact with certain objects",
    category = Category.MOVEMENT
) {
    val fire by setting("Fire", true, description = "Prevents you from touching fire by making the hitbox solid")
    val cactus by setting("Cactus", true, description = "Prevents you from taking cactus damage by slightly expanding its hitbox")
    val unloaded by setting("Unloaded Chunks", true, description = "Prevents you from entering unloaded chunks")
    val void by setting("Void", true, description = "Prevents you from entering Y levels below zero")
    val dragonEgg by setting("Dragon Egg", true, description = "Prevents you from teleporting dragon eggs")

    init {
        safeListener<PacketEvent.Send> {
            if (dragonEgg) {
                when (it.packet) {
                    is CPacketPlayerTryUseItemOnBlock -> {
                        if (world.getBlockState(it.packet.pos).block == Blocks.DRAGON_EGG) it.cancel()
                    }

                    is CPacketPlayerDigging -> {
                        if (world.getBlockState(it.packet.position).block == Blocks.DRAGON_EGG) it.cancel()
                    }
                }
            }

        }
    }
}