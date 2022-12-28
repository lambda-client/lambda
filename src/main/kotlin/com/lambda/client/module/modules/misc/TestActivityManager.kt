package com.lambda.client.module.modules.misc

import com.lambda.client.LambdaMod
import com.lambda.client.activity.activities.example.ProbablyFailing
import com.lambda.client.activity.activities.example.SayAnnoyingly
import com.lambda.client.activity.activities.highlevel.BreakDownEnderChests
import com.lambda.client.activity.activities.highlevel.BuildBlock
import com.lambda.client.activity.activities.highlevel.SurroundWithObsidian
import com.lambda.client.activity.activities.inventory.DumpInventory
import com.lambda.client.activity.activities.inventory.SwapOrMoveToItem
import com.lambda.client.activity.activities.storage.ExtractItemFromShulkerBox
import com.lambda.client.activity.activities.storage.StoreItemToShulkerBox
import com.lambda.client.activity.activities.travel.PickUpDrops
import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.block
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.items.item
import com.lambda.client.util.threads.runSafe
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
    private val a by setting("Get any Dia Pickaxe", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            SwapOrMoveToItem(Items.DIAMOND_PICKAXE)
        )
        false
    })

    private val tie by setting("Store Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            StoreItemToShulkerBox(Blocks.OBSIDIAN.item)
        )
        false
    })

    private val ctiectie by setting("Auto Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            BreakDownEnderChests()
        )
        false
    })

    private val etit by setting("Extract Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            ExtractItemFromShulkerBox(Blocks.OBSIDIAN.item)
        )
        false
    })

    private val b by setting("Get Dia Pickaxe with silktouch", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            SwapOrMoveToItem(
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
        ActivityManager.addSubActivities(DumpInventory())
        false
    })

    private val po by setting("Pickup Obby", false, consumer = { _, _->
        ActivityManager.addSubActivities(PickUpDrops(Blocks.OBSIDIAN.item))
        false
    })

    private val ti by setting("count", false, consumer = { _, _->
        runSafe {
            LambdaMod.LOG.info(player.inventorySlots.countEmpty())
        }

        false
    })

    private val tiectie by setting("Surround me", false, consumer = { _, _->
        ActivityManager.addSubActivities(
            SurroundWithObsidian()
        )
        false
    })

    private val sayHelloWorld by setting("Hello World", false, consumer = { _, _->
        ActivityManager.addSubActivities(SayAnnoyingly("Hello World"))
        false
    })

    private val fail by setting("maybe fail", false, consumer = { _, _->
        ActivityManager.addSubActivities(ProbablyFailing())
        false
    })

    private val reset by setting("Reset", false, consumer = { _, _->
        ActivityManager.reset()
        false
    })
}
