package com.lambda.client.module.modules.misc

import com.lambda.client.manager.managers.ActivityManager
import com.lambda.client.manager.managers.activity.activities.SayAnnoyinglyActivity
import com.lambda.client.module.Category
import com.lambda.client.module.Module

object SayHelloWorld : Module(
    name = "SayHelloWorld",
    description = "",
    category = Category.MISC
) {
    init {
        onEnable {
            ActivityManager.addActivity(SayAnnoyinglyActivity("Hello World!"))
        }
    }
}
