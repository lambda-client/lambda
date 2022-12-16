package com.lambda.client.module.modules.misc

import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.activity.Activity
import com.lambda.client.manager.managers.activity.activities.example.SayAnnoyinglyActivity
import com.lambda.client.manager.managers.activity.activities.interaction.PlaceAndBreakBlockActivity
import com.lambda.client.manager.managers.activity.activities.inventory.DumpInventoryActivity
import com.lambda.client.manager.managers.activity.activities.inventory.SwapHotbarSlotsActivity
import com.lambda.client.manager.managers.activity.activities.inventory.SwapOrMoveToItemActivity
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.block
import net.minecraft.block.BlockShulkerBox
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
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

    private val place by setting("PlaceAhed", false, consumer = { _, _->
        ActivityManager.addActivity(PlaceAndBreakBlockActivity(Blocks.SLIME_BLOCK))
        false
    })

    private val moin by setting("Ja Moin", false, consumer = { _, _->
        val activities = mutableListOf<Activity>()

        activities.add(SwapOrMoveToItemActivity(Items.DIAMOND_PICKAXE))
        repeat(9) {
            activities.add(SwapHotbarSlotsActivity(36 + it, it + 1))
        }
        repeat(4) {
            activities.add(PlaceAndBreakBlockActivity(Blocks.SLIME_BLOCK))
        }
        activities.add(DumpInventoryActivity())

        ActivityManager.addAllActivities(activities)
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
