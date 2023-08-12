package com.lambda.client.activity.activities.storage

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.storage.types.ItemInfo
import net.minecraft.item.ItemStack

class StoreItemToEnderChest(
    containerStack: ItemStack,
    private val itemInfo: ItemInfo
) : Activity()