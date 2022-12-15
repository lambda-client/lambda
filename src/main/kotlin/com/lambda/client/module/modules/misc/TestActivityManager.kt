package com.lambda.client.module.modules.misc

import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.activity.activities.example.SayAnnoyinglyActivity
import com.lambda.client.manager.managers.activity.activities.inventory.DumpInventoryActivity
import com.lambda.client.manager.managers.activity.activities.inventory.SwapOrMoveToAnyBlockActivity
import com.lambda.client.module.Category
import com.lambda.client.module.Module

object TestActivityManager : Module(
    name = "TestActivityManager",
    description = "",
    category = Category.MISC
) {
    private val getBlock by setting("Get Block", false, consumer = { _, _->
        ActivityManager.addActivity(SwapOrMoveToAnyBlockActivity())
        false
    })

    private val dumpInventoryActivity by setting("Dump Inventory", false, consumer = { _, _->
        ActivityManager.addActivity(DumpInventoryActivity())
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
