package com.lambda.neoforge

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.VERSION
import net.neoforged.fml.common.Mod

@Mod(Lambda.MOD_ID)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object LambdaNeoForge {
    init {
        Lambda.initialize()
        LOG.info("$MOD_NAME NeoForge $VERSION initialized.")
    }
}
