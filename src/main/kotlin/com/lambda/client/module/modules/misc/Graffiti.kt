package com.lambda.client.module.modules.misc

import com.lambda.client.activity.activities.construction.Graffiti
import com.lambda.client.manager.managers.ActivityManager.addSubActivities
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe

object Graffiti : Module(
    name = "Graffiti",
    description = "Spams item frames and maps",
    category = Category.MISC
) {
    private val mapID by setting("Map ID", 0, 0..65535, 1)
    private val ownedActivity = Graffiti()

    init {
        onEnable {
            addSubActivities(ownedActivity)
        }

        onDisable {
            runSafe {
                with(ownedActivity) {
                    cancel()
                }
            }
        }
    }
}