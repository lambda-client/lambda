package com.lambda.client.module.modules.player

import com.lambda.client.module.Category
import com.lambda.client.module.Module

internal object TpsSync : Module(
    name = "TpsSync",
    description = "Synchronizes block states with the server TPS",
    category = Category.PLAYER
)
