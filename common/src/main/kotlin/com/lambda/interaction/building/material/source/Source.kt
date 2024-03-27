package com.lambda.interaction.building.material.source

import com.lambda.interaction.building.manager.MaterialSourceManager
import com.lambda.interaction.building.material.SourceSelection
import com.lambda.interaction.building.material.source.sources.*
import com.lambda.util.ItemUtils
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.StringIdentifiable

enum class Source : StringIdentifiable {
    CREATIVE {
        override fun gather(player: ClientPlayerEntity) = setOf(CreativeSource)
    },
    MAIN_HAND {
        override fun gather(player: ClientPlayerEntity) =
            setOf(MainHandSource(player.mainHandStack)) + listOf(player.mainHandStack).doShulkerCheck(MAIN_HAND)
    },
    OFF_HAND {
        override fun gather(player: ClientPlayerEntity) =
            setOf(OffHandSource(player.offHandStack)) + listOf(player.offHandStack).doShulkerCheck(OFF_HAND)
    },
    HANDS {
        override fun gather(player: ClientPlayerEntity) =
            MAIN_HAND.gather(player) + OFF_HAND.gather(player)
    },
    HOTBAR {
        override fun gather(player: ClientPlayerEntity) =
            setOf(HotbarSource(player.hotbar)) + player.hotbar.doShulkerCheck(HOTBAR)
    },
    INVENTORY {
        override fun gather(player: ClientPlayerEntity) =
            setOf(InventorySource(player.combined)) + player.combined.doShulkerCheck(INVENTORY)
    },
    SHULKER_BOX {
        override fun gather(player: ClientPlayerEntity) = emptySet<MaterialSource>()
    },
    ENDER_CHEST {
        override fun gather(player: ClientPlayerEntity) = emptySet<MaterialSource>()
    },
    CHEST {
        override fun gather(player: ClientPlayerEntity) = emptySet<MaterialSource>()
    },
    STASH {
        override fun gather(player: ClientPlayerEntity) = emptySet<MaterialSource>()
    }, ;

    // ToDo: This is an experiment for commands
    override fun asString() = name.lowercase()

    abstract fun gather(player: ClientPlayerEntity): Set<MaterialSource>

    fun select() = SourceSelection().apply {
        selection = { it.source == this@Source }
    }

    fun List<ItemStack>.doShulkerCheck(source: Source) =
        filter {
            it.item in ItemUtils.shulkerBoxes
        }.map { stack ->
            ShulkerBoxSource(
                MaterialSourceManager.getShulkerBoxContents(stack),
                containedIn = source,
                shulkerStack = stack,
            )
        }.toSet()
}