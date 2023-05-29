package com.lambda.client.activity.activities.interaction.crafting

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import net.minecraft.item.Item
import net.minecraft.item.ItemShulkerBox

class CraftSimpleRecipe(val item: Item) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        when(item) {
            is ItemShulkerBox -> {
                addSubActivities(

                )
            }
        }
    }
}