package com.lambda.client.module.modules.misc

import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.activity.activities.example.SayAnnoyinglyActivity
import com.lambda.client.manager.managers.activity.activities.inventory.DumpInventoryActivity
import com.lambda.client.manager.managers.activity.activities.inventory.SwapOrMoveToItemActivity
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.block
import net.minecraft.block.BlockShulkerBox
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Enchantments
import net.minecraft.init.Items

object TestActivityManager : Module(
    name = "TestActivityManager",
    description = "",
    category = Category.MISC
) {
    private val a by setting("Get Dia Pickaxe", false, consumer = { _, _->
        ActivityManager.addActivity(SwapOrMoveToItemActivity(Items.DIAMOND_PICKAXE))
        false
    })

    private val b by setting("Get Dia Pickaxe with silktouch", false, consumer = { _, _->
        ActivityManager.addActivity(
            SwapOrMoveToItemActivity(
                Items.DIAMOND_PICKAXE,
                predicateItem = {
                    EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, it) == 1
                },
                predicateSlot = {
                    val item = it.item
                    item != Items.DIAMOND_PICKAXE && item.block !is BlockShulkerBox
                }
            )
        )
        false
    })

    private val dumpInventoryActivity by setting("Dump Inventory", false, consumer = { _, _->
        ActivityManager.addActivity(DumpInventoryActivity())
        false
    })

    private val onlyOne by setting("Dump only one Inventory", false, consumer = { _, _->
        ActivityManager.addActivity(DumpInventoryActivity(1))
        false
    })

    private val sayHelloWorld by setting("Hello World", false, consumer = { _, _->
        ActivityManager.addActivity(SayAnnoyinglyActivity("Hello World"))
        false
    })

    private val reset by setting("Reset", false, consumer = { _, _->
        ActivityManager.reset()
        false
    })
}
